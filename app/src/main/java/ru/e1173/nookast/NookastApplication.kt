package ru.e1173.nookast

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.e1173.nookast.app.AppGraph
import ru.e1173.nookast.core.sync.SyncRepositories
import ru.e1173.nookast.sync.SyncWorkScheduler
import ru.e1173.nookast.sync.ReadCacheWorkScheduler

class NookastApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        SyncRepositories.install(this)
        if (BuildConfig.DEBUG) enableStrictModeLogging()
        CrashReportStore(this).install()
        // Decrypt the session once, off the main thread, before any screen or service needs it.
        startupScope.launch(Dispatchers.IO) {
            graph.session.warmUp()
            // Read their flags from disk; the first key event must not do that on Main.
            graph.controlJournal
            graph.legacyMetadataBroadcast
            graph.syncScheduler.start()
        }
        // Periodic work is durable but is not needed before the first useful frame.
        startupScope.launch {
            SyncWorkScheduler.ensurePeriodic(this@NookastApplication)
            ReadCacheWorkScheduler.ensurePeriodic(this@NookastApplication)
            ru.e1173.nookast.update.Updater.load(this@NookastApplication)
            if (ru.e1173.nookast.update.Updater.state.value.mode != ru.e1173.nookast.update.UpdateMode.OFF) {
                ru.e1173.nookast.update.UpdateScheduler.ensure(this@NookastApplication)
                ru.e1173.nookast.update.UpdateScheduler.checkIfStale(this@NookastApplication)
            }
        }
    }
}

/**
 * Debug builds log (never crash on) main-thread disk/network access and leaked closables, so
 * regressions of the local-first startup path (PERF-01) show up in logcat during development.
 */
private fun enableStrictModeLogging() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build(),
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .penaltyLog()
            .build(),
    )
}

class CrashReportStore(context: Context) {
    private val reportFile = File(context.filesDir, REPORT_FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is RecordingExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            RecordingExceptionHandler(reportFile, previous),
        )
    }

    fun read(): String? = runCatching {
        reportFile.takeIf(File::isFile)?.readText(Charsets.UTF_8)?.takeIf(String::isNotBlank)
    }.getOrNull()

    fun clear() {
        if (reportFile.isFile) reportFile.delete()
    }

    private class RecordingExceptionHandler(
        private val reportFile: File,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                val header = buildString {
                    appendLine("Nookast ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Time: ${Instant.now()}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                }
                val report = sanitizeCrashReport(header + error.stackTraceToString())
                reportFile.parentFile?.mkdirs()
                reportFile.writeText(report, Charsets.UTF_8)
            }
            previous?.uncaughtException(thread, error)
                ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private companion object {
        const val REPORT_FILE_NAME = "last-crash.txt"
    }
}

internal const val MAX_CRASH_REPORT_CHARS = 64 * 1024
private val API_KEY_PATTERN = Regex("(?i)(api[_-]?key[=: ]+)[^&\\s]+")
private val AUTHORIZATION_PATTERN = Regex("(?i)(authorization[=: ]+)[^\\r\\n]+")

// JSON values under any key that names a secret: "api_key", "rss_key", "token", "password"...
private val JSON_SECRET_PATTERN =
    Regex("(?i)(\"[a-z0-9_]*(?:key|token|secret|auth|password)[a-z0-9_]*\"\\s*:\\s*)\"[^\"]*\"")

// Query and form parameters: token=..., password=..., rss_key=... (api_key is handled above).
private val PARAM_SECRET_PATTERN = Regex("(?i)\\b((?:[a-z_]*token|secret|password|rss[_-]?key)=)[^&\\s\"]+")
private val URL_HOST_PATTERN = Regex("(?i)\\b(https?://)([^/\\s:\"'?#]+)")

/**
 * Crash reports leave the device by the user's copy (X-06): secrets are removed and every URL host
 * is replaced by a short hash, so a report names neither the server nor the feeds.
 */
internal fun sanitizeCrashReport(report: String): String = report
    .replace(API_KEY_PATTERN, "\$1<redacted>")
    .replace(AUTHORIZATION_PATTERN, "\$1<redacted>")
    .replace(JSON_SECRET_PATTERN, "\$1\"<redacted>\"")
    .replace(PARAM_SECRET_PATTERN, "\$1<redacted>")
    .replace(URL_HOST_PATTERN) { match -> match.groupValues[1] + "host-" + hostHash(match.groupValues[2]) }
    .take(MAX_CRASH_REPORT_CHARS)

private fun hostHash(host: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(host.lowercase().toByteArray())
    .take(4)
    .joinToString("") { "%02x".format(it) }
