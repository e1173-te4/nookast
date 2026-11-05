package ru.e1173.nookast.update

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.e1173.nookast.BuildConfig
import ru.e1173.nookast.offline.EpisodeDownloadWorker
import ru.e1173.nookast.player.PlaybackActivity

/** What Settings → About shows about updates. */
data class UpdateState(
    val mode: UpdateMode = UpdateMode.AUTOMATIC,
    val lastCheckMs: Long = 0L,
    val checking: Boolean = false,
    /** The newer release, when there is one. */
    val available: String? = null,
    /** The newer release's whole text; [ReleaseRules.notesOf] gives the reader's language. */
    val availableBody: String = "",
    /** The newer release is downloaded and verified, ready to install. */
    val downloaded: Boolean = false,
    /** Android would ask to confirm this install, so it waits for the user (OTA review, 3.11.2026). */
    val needsConfirmation: Boolean = false,
    /** Download progress in percent while the APK is coming, else null. */
    val progress: Int? = null,
    /** A manual check on a metered network found an APK of this many bytes and asks before downloading. */
    val meteredSize: Long? = null,
    /** «Later» was chosen in the prompt during this run of the app. */
    val promptPostponed: Boolean = false,
    /** The user opened the app from the «ready» notification: the prompt shows even while something plays. */
    val promptRequested: Boolean = false,
    val error: UpdateError? = null,
)

enum class UpdateError { NETWORK, NO_RELEASE, CHECKSUM, FOREIGN_SIGNATURE, NOT_NEWER, INSTALL, PERMISSION }

/**
 * The built-in updater (user, 2.11.2026). It asks the public repository's latest release once a day
 * (and on «Check now»), downloads the APK to the app's cache, verifies it — the checksum for an intact
 * file, then the package, a higher version code and the installed app's own certificate — and
 * installs it through a package installer session. Release builds only.
 */
object Updater {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val lock = Mutex()

    @Volatile private var loaded = false

    /**
     * GitHub only: none of PinePods' interceptors, keys or private certificate authority, only the
     * system's trust. No token: the anonymous limit of 60 requests an hour is plenty for one a day.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val enabled: Boolean get() = BuildConfig.UPDATER_ENABLED

    /** Reads the stored state; it touches the disk, so callers run it off the main thread. */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val store = store(context)
        _state.value = UpdateState(
            mode = UpdateMode.fromStoredValue(store.getString(KEY_MODE, null)),
            lastCheckMs = store.getLong(KEY_LAST_CHECK, 0L),
            // An offer the running version already reached is no offer (it was just installed).
            available = store.getString(KEY_AVAILABLE, null)?.takeIf(::isNewerThanThis),
            availableBody = store.getString(KEY_AVAILABLE_NOTES, "").orEmpty(),
            downloaded = downloadedFile(context) != null,
            needsConfirmation = store.getString(KEY_NEEDS_CONFIRMATION, null) != null && downloadedFile(context) != null,
        )
        loaded = true
    }

    suspend fun loadAsync(context: Context): Unit = withContext(Dispatchers.IO) { load(context) }

    suspend fun setMode(context: Context, mode: UpdateMode): Unit = withContext(Dispatchers.IO) {
        load(context)
        store(context).edit { putString(KEY_MODE, mode.name) }
        _state.value = _state.value.copy(mode = mode)
        // Off asks nothing of the network; leaving it brings the daily check back.
        if (mode == UpdateMode.OFF) UpdateScheduler.cancel(context) else UpdateScheduler.ensure(context)
    }

    /**
     * Asks for the latest release and fetches it when newer. [manual]: any network will do; the
     * automatic check downloads only on an unmetered network, and a manual one on a metered network
     * asks first ([UpdateState.meteredSize]) unless [allowMetered].
     */
    suspend fun check(context: Context, manual: Boolean, allowMetered: Boolean = false): UpdateState = lock.withLock {
        val app = context.applicationContext
        withContext(Dispatchers.IO) { load(app) }
        _state.value = _state.value.copy(checking = true, error = null, meteredSize = null)
        val outcome = withContext(Dispatchers.IO) { runCatching { checkLocked(app, manual, allowMetered) } }
        _state.value = _state.value.copy(
            checking = false,
            progress = null,
            error = outcome.exceptionOrNull()?.let { (it as? UpdateFailure)?.error ?: UpdateError.NETWORK },
        )
        _state.value
    }

    private fun checkLocked(context: Context, manual: Boolean, allowMetered: Boolean) {
        val store = store(context)
        val json = fetchLatestJson(store)
        store.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        _state.value = _state.value.copy(lastCheckMs = System.currentTimeMillis())
        val current = ReleaseRules.versionOfName(BuildConfig.VERSION_NAME) ?: return
        val release = json?.let {
            ReleaseRules.parseLatest(it, allowHttp = BuildConfig.UPDATE_LATEST_URL.startsWith("http://"))
                ?: throw UpdateFailure(UpdateError.NO_RELEASE)
        }
        if (release == null || !ReleaseRules.isNewer(release.version, current)) {
            clearDownloads(context)
            store.edit {
                remove(KEY_AVAILABLE)
                remove(KEY_AVAILABLE_NOTES)
                remove(KEY_NEEDS_CONFIRMATION)
            }
            _state.value = _state.value.copy(available = null, availableBody = "", downloaded = false, needsConfirmation = false)
            return
        }
        store.edit {
            putString(KEY_AVAILABLE, release.tag)
            putString(KEY_AVAILABLE_NOTES, release.body)
            putString(KEY_NOTES_PREFIX + release.tag, release.body)
            // Only the offered release's notes and this version's own («Updated to X») are kept.
            val keep = setOf(KEY_NOTES_PREFIX + release.tag, KEY_NOTES_PREFIX + "v" + BuildConfig.VERSION_NAME.substringBefore('-'))
            store.all.keys.filter { it.startsWith(KEY_NOTES_PREFIX) && it !in keep }.forEach(::remove)
        }
        _state.value = _state.value.copy(available = release.tag, availableBody = release.body)
        if (downloadedFile(context)?.name == release.apkName) {
            _state.value = _state.value.copy(downloaded = true)
            return
        }
        _state.value = _state.value.copy(downloaded = false)
        if (!manual && (_state.value.mode == UpdateMode.OFF || isMetered(context))) return
        if (manual && !allowMetered && isMetered(context)) {
            // A manual check on mobile data says the size and asks first (OTA review).
            _state.value = _state.value.copy(meteredSize = release.apkSize)
            return
        }
        val expected = fetchText(release.shaUrl).let { ReleaseRules.parseSha256(it, release.apkName) }
            ?: throw UpdateFailure(UpdateError.CHECKSUM)
        if (RejectedReleases.isRejected(store.getString(KEY_REJECTED, null), release.tag, expected)) {
            throw UpdateFailure(UpdateError.FOREIGN_SIGNATURE)
        }
        download(context, release, expected)
        _state.value = _state.value.copy(downloaded = true)
    }

    /** The user declined the download on mobile data. */
    fun dismissMetered() {
        _state.value = _state.value.copy(meteredSize = null)
    }

    /** The release JSON, revalidated with its ETag so an unchanged answer costs no download. */
    private fun fetchLatestJson(store: android.content.SharedPreferences): String? {
        val cached = store.getString(KEY_JSON, null)
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Nookast/${BuildConfig.VERSION_NAME}")
            .apply { if (cached != null) store.getString(KEY_ETAG, null)?.let { header("If-None-Match", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 304 && cached != null) return cached
            // No release published yet: nothing to offer, not a failure (OTA review: a neutral status).
            if (response.code == 404) return null
            if (!response.isSuccessful) throw UpdateFailure(UpdateError.NETWORK)
            val body = response.body.string()
            store.edit {
                putString(KEY_JSON, body)
                response.header("ETag")?.let { putString(KEY_ETAG, it) } ?: remove(KEY_ETAG)
            }
            return body
        }
    }

    private fun download(context: Context, release: ReleaseInfo, expected: String) {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val partial = File(directory, "${release.apkName}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(get(release.apkUrl)).execute().use { response ->
            if (!response.isSuccessful) throw UpdateFailure(UpdateError.NETWORK)
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
            var received = 0L
            _state.value = _state.value.copy(progress = 0)
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        received += read
                        if (total > 0) {
                            val percent = (received * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != _state.value.progress) _state.value = _state.value.copy(progress = percent)
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            partial.delete()
            throw UpdateFailure(UpdateError.CHECKSUM)
        }
        // The checksum proves the file is whole; the certificate proves who made it.
        val rejection = ApkRules.check(archiveFacts(context, partial), installedFacts(context))
        if (rejection != null) {
            partial.delete()
            // The same file is not fetched again; a re-published release has another checksum.
            store(context).edit { putString(KEY_REJECTED, RejectedReleases.key(release.tag, expected)) }
            throw UpdateFailure(
                when (rejection) {
                    ApkRejection.NOT_NEWER -> UpdateError.NOT_NEWER
                    ApkRejection.CHECKSUM -> UpdateError.CHECKSUM
                    else -> UpdateError.FOREIGN_SIGNATURE
                },
            )
        }
        val apk = File(directory, release.apkName)
        if (!partial.renameTo(apk)) throw UpdateFailure(UpdateError.NETWORK)
    }

    private fun get(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", "Nookast/${BuildConfig.VERSION_NAME}")
        .build()

    private fun fetchText(url: String): String = client.newCall(get(url)).execute().use { response ->
        if (!response.isSuccessful) throw UpdateFailure(UpdateError.NETWORK)
        response.body.string()
    }

    /** The verified APK waiting to be installed, if any. */
    fun downloadedFile(context: Context): File? =
        File(context.cacheDir, DIRECTORY).listFiles()?.firstOrNull { it.name.endsWith(".apk") }

    private fun clearDownloads(context: Context) {
        File(context.cacheDir, DIRECTORY).listFiles()?.forEach(File::delete)
    }

    /** Whether the app may install packages; Android asks the user once in its settings. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Hands the verified APK to the system, off the main thread. From Android 12 the app's own update
     * needs no confirmation; when Android asks anyway, [UpdateInstallReceiver] opens the confirmation
     * on screen or leaves it for the user in the app and a notification.
     */
    suspend fun install(context: Context): Boolean = withContext(Dispatchers.IO) { installBlocking(context.applicationContext) }

    private fun installBlocking(context: Context): Boolean {
        load(context)
        val apk = downloadedFile(context)
        if (apk == null) {
            // The cache was cleared under it: forget it and look again on the next check.
            _state.value = _state.value.copy(downloaded = false, needsConfirmation = false)
            store(context).edit { remove(KEY_NEEDS_CONFIRMATION) }
            UpdateScheduler.checkSoon(context)
            return false
        }
        if (!canInstall(context)) {
            _state.value = _state.value.copy(error = UpdateError.PERMISSION)
            return false
        }
        // Checked again right before the install: the file may have sat in the cache for a while.
        if (ApkRules.check(archiveFacts(context, apk), installedFacts(context)) != null) {
            apk.delete()
            _state.value = _state.value.copy(downloaded = false, error = UpdateError.FOREIGN_SIGNATURE)
            return false
        }
        val installer = context.packageManager.packageInstaller
        // A session left by an earlier attempt (killed process, failed commit) would hold its copy of the APK.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        var sessionId = -1
        return runCatching {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION_RESULT),
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
                )
                session.commit(callback.intentSender)
            }
            true
        }.getOrElse {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            _state.value = _state.value.copy(error = UpdateError.INSTALL)
            false
        }
    }

    internal fun reportInstallFailure() {
        _state.value = _state.value.copy(error = UpdateError.INSTALL)
    }

    /**
     * The update waits for the user: Android would ask to confirm it, or the app may not install
     * packages yet. Remembered, so the app offers it when opened (OTA review, 3.11.2026).
     */
    fun markNeedsConfirmation(context: Context) {
        load(context)
        store(context).edit { putString(KEY_NEEDS_CONFIRMATION, _state.value.available ?: "") }
        _state.value = _state.value.copy(needsConfirmation = true)
    }

    /** The «ready» notification was tapped. */
    fun requestPrompt() {
        _state.value = _state.value.copy(promptRequested = true, promptPostponed = false)
    }

    /** «Later» in the prompt: it comes back with the next release or the next start of the app. */
    fun postponePrompt() {
        _state.value = _state.value.copy(promptPostponed = true, promptRequested = false)
    }

    /** Whether Android installs this app's own update without asking (see [InstallPolicy.silentInstallPossible]). */
    fun silentInstallPossible(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }.getOrNull() ?: return false
        val owner = if (Build.VERSION.SDK_INT >= 34) source.updateOwnerPackageName else null
        return InstallPolicy.silentInstallPossible(Build.VERSION.SDK_INT, context.packageName, source.installingPackageName, owner)
    }

    /** What stands in the way of installing by itself right now. */
    fun conditions(context: Context): InstallConditions {
        val foreground = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
            .importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val carMode = context.getSystemService(android.app.UiModeManager::class.java)?.currentModeType ==
            android.content.res.Configuration.UI_MODE_TYPE_CAR
        // A running download would restart from the start after the update; queued ones go on.
        val downloading = runCatching {
            WorkManager.getInstance(context).getWorkInfosByTag(EpisodeDownloadWorker.TAG).get()
                .any { it.state == WorkInfo.State.RUNNING }
        }.getOrDefault(false)
        val pausedAt = PlaybackActivity.pausedAtElapsedMs
        return InstallConditions(
            playing = PlaybackActivity.playing,
            appOnScreen = foreground,
            carConnected = carMode || PlaybackActivity.carConnected,
            downloadsRunning = downloading,
            screenOn = context.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false,
            recentlyPaused = pausedAt > 0 && android.os.SystemClock.elapsedRealtime() - pausedAt < RECENT_PAUSE_MS,
            silentInstall = silentInstallPossible(context) && canInstall(context),
        )
    }

    private fun isMetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun installedFacts(context: Context): ApkFacts {
        val info = context.packageManager.getPackageInfo(context.packageName, signingFlags())
        return ApkFacts(info.packageName, PackageInfoCompat.getLongVersionCode(info), signers(info))
    }

    private fun archiveFacts(context: Context, apk: File): ApkFacts? {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, signingFlags()) ?: return null
        return ApkFacts(info.packageName, PackageInfoCompat.getLongVersionCode(info), signers(info))
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /** The changes of [tag] as its release said, for «Updated to X». */
    fun notesFor(context: Context, tag: String): String = store(context).getString(KEY_NOTES_PREFIX + tag, "").orEmpty()

    /**
     * The version to greet once after an update, or null. The first run of the app only remembers
     * its version: nothing was updated.
     */
    fun takeUpdatedTo(context: Context): String? {
        // Loaded first, so a concurrent first load cannot bring back what is cleared here.
        load(context)
        val store = store(context)
        val seen = store.getLong(KEY_SEEN_CODE, -1L)
        val current = BuildConfig.VERSION_CODE.toLong()
        if (seen == current) return null
        store.edit {
            putLong(KEY_SEEN_CODE, current)
            remove(KEY_NEEDS_CONFIRMATION)
            remove(KEY_AVAILABLE)
            remove(KEY_AVAILABLE_NOTES)
        }
        clearDownloads(context)
        _state.value = _state.value.copy(available = null, availableBody = "", downloaded = false, needsConfirmation = false)
        return if (seen in 0 until current) "v${BuildConfig.VERSION_NAME}" else null
    }

    private fun isNewerThanThis(tag: String): Boolean {
        val offered = ReleaseRules.versionOfTag(tag) ?: return false
        val current = ReleaseRules.versionOfName(BuildConfig.VERSION_NAME) ?: return false
        return ReleaseRules.isNewer(offered, current)
    }

    private fun store(context: Context) = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private class UpdateFailure(val error: UpdateError) : Exception(error.name)

    private const val STORE = "nookast-updates"
    private const val DIRECTORY = "updates"
    private const val KEY_MODE = "mode"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_AVAILABLE = "available"
    private const val KEY_AVAILABLE_NOTES = "available_notes"
    private const val KEY_NOTES_PREFIX = "notes_"
    private const val KEY_JSON = "latest_json"
    private const val KEY_ETAG = "latest_etag"
    private const val KEY_SEEN_CODE = "seen_version_code"
    private const val KEY_NEEDS_CONFIRMATION = "needs_confirmation"
    private const val KEY_REJECTED = "rejected_release"
    private const val RECENT_PAUSE_MS = 30 * 60 * 1000L
}
