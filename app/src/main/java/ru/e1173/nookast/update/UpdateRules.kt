package ru.e1173.nookast.update

import org.json.JSONObject
import org.json.JSONTokener

/**
 * Over-the-air updates from the public releases (user, 2.11.2026: «yes, a built-in updater»). The
 * rules here have no Android in them: what a release says, whether it is newer, what its checksum
 * file holds, whether a downloaded APK may replace this app, and when to install.
 */

/** The latest public release as GitHub's `releases/latest` gives it. */
data class ReleaseInfo(
    val tag: String,
    val version: List<Int>,
    /** The whole release text; [ReleaseRules.notesOf] picks the part in the reader's language. */
    val body: String,
    val apkName: String,
    val apkUrl: String,
    val shaUrl: String,
    /** The APK's size as GitHub states it, or 0 when unknown. */
    val apkSize: Long = 0L,
)

object ReleaseRules {
    private val TAG = Regex("^v(\\d+(?:\\.\\d+){0,3})$")
    private val LANGUAGE_MARK = Regex("^<!--\\s*lang:([a-z]{2})\\s*-->$")

    /**
     * The release, or null when it is a draft or a pre-release, its tag is not a plain `vX.Y.Z`
     * (a `-rc` is never offered) or it lacks `nookast-<version>.apk` and its `.sha256` over HTTPS
     * ([allowHttp] only for a local test build that points the updater at a plain-HTTP server).
     */
    fun parseLatest(json: String, allowHttp: Boolean = false): ReleaseInfo? {
        val root = runCatching { JSONTokener(json).nextValue() as? JSONObject }.getOrNull() ?: return null
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name")
        val version = versionOfTag(tag) ?: return null
        val name = "nookast-${tag.removePrefix("v")}.apk"
        val assets = root.optJSONArray("assets") ?: return null
        var apk: String? = null
        var sha: String? = null
        var size = 0L
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val url = asset.optString("browser_download_url")
                .takeIf { it.startsWith("https://") || (allowHttp && it.startsWith("http://")) } ?: continue
            when (asset.optString("name")) {
                name -> {
                    apk = url
                    size = asset.optLong("size", 0L)
                }
                "$name.sha256" -> sha = url
            }
        }
        return ReleaseInfo(tag, version, root.optString("body"), name, apk ?: return null, sha ?: return null, size)
    }

    fun versionOfTag(tag: String): List<Int>? =
        TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.split('.')?.map { it.toIntOrNull() ?: return null }

    /** This build's own version: `1.1.5` or `1.1.5-debug`, the suffix ignored. */
    fun versionOfName(versionName: String): List<Int>? =
        versionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }

    /** Component by component; a missing component counts as 0 (`1.2` == `1.2.0`). */
    fun isNewer(candidate: List<Int>, current: List<Int>): Boolean {
        for (index in 0 until maxOf(candidate.size, current.size)) {
            val a = candidate.getOrElse(index) { 0 }
            val b = current.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** The hex digest from `<hex>  nookast-<version>.apk`, when the line names this file. */
    fun parseSha256(text: String, apkName: String): String? = text.lineSequence()
        .map(String::trim)
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            val hex = parts.getOrNull(0)?.lowercase() ?: return@mapNotNull null
            val file = parts.getOrNull(1)?.removePrefix("*")
            hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } && (file == null || file == apkName) }
        }
        .firstOrNull()

    /**
     * What changed, in [language] (user, 3.11.2026: the notes are in English and Russian). A body
     * marked with `<!-- lang:en -->` and `<!-- lang:ru -->` lines gives the section of the reader's
     * language, each running to the next mark or to the first `---` line after it (the checksum tail
     * the release workflow adds), or the other language when that one is missing. An unmarked body
     * gives its text above the first `---` line.
     */
    fun notesOf(body: String, language: String = "en"): String {
        val lines = body.replace("\r\n", "\n").lines()
        val sections = linkedMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in lines) {
            val mark = LANGUAGE_MARK.matchEntire(line.trim())
            when {
                mark != null -> current = StringBuilder().also { sections[mark.groupValues[1]] = it }
                line.trim() == "---" -> if (sections.isEmpty()) break else current = null
                else -> current?.appendLine(line)
            }
        }
        if (sections.isEmpty()) return lines.takeWhile { it.trim() != "---" }.joinToString("\n").trim()
        val wanted = if (language == "ru") "ru" else "en"
        return (sections[wanted] ?: sections.values.first()).toString().trim()
    }
}

/** What an APK says about itself, or what the installed app is. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signerSha256: Set<String>)

enum class ApkRejection { WRONG_PACKAGE, NOT_NEWER, FOREIGN_SIGNATURE, UNREADABLE, CHECKSUM }

object ApkRules {
    /**
     * Whether [downloaded] may replace [installed]: the same package, a higher version code and the
     * very certificate the installed app is signed with. The checksum only proves the file arrived
     * whole; the certificate proves who made it.
     */
    fun check(downloaded: ApkFacts?, installed: ApkFacts): ApkRejection? = when {
        downloaded == null -> ApkRejection.UNREADABLE
        downloaded.packageName != installed.packageName -> ApkRejection.WRONG_PACKAGE
        downloaded.versionCode <= installed.versionCode -> ApkRejection.NOT_NEWER
        downloaded.signerSha256.isEmpty() || downloaded.signerSha256 != installed.signerSha256 -> ApkRejection.FOREIGN_SIGNATURE
        else -> null
    }
}

/** Releases the app refused (another certificate, not newer): the same file is not fetched again. */
object RejectedReleases {
    fun key(tag: String, sha256: String) = "$tag:$sha256"

    fun isRejected(stored: String?, tag: String, sha256: String) = stored == key(tag, sha256)
}

enum class UpdateMode {
    /** Install by itself when nothing is in the way (the default). */
    AUTOMATIC,

    /** Only tell that an update is ready. */
    NOTIFY,
    OFF;

    companion object {
        fun fromStoredValue(value: String?): UpdateMode = entries.firstOrNull { it.name == value } ?: AUTOMATIC
    }
}

/** What stands in the way of installing now. */
data class InstallConditions(
    val playing: Boolean,
    val appOnScreen: Boolean,
    val carConnected: Boolean,
    val downloadsRunning: Boolean,
    /** The screen is on: the phone is in use (user, 3.11.2026). */
    val screenOn: Boolean = false,
    /** Playback paused not long ago and may be resumed any moment from the lock screen or the wheel. */
    val recentlyPaused: Boolean = false,
    /** Android would install without asking; otherwise the update waits for the user in the app. */
    val silentInstall: Boolean = true,
)

enum class InstallDecision {
    INSTALL_NOW,
    LATER,
    NOTIFY,

    /** Android would ask the user: wait for them in the app or a notification, never from the background. */
    NEEDS_CONFIRMATION,
    NOTHING,
}

object InstallPolicy {
    /**
     * The automatic mode installs only when Android will not ask, nothing plays or paused lately,
     * the screen is off, the app is not on screen, no car is connected and no episode is being
     * downloaded; otherwise it tries again later. An install Android would confirm waits for the user.
     */
    fun decide(mode: UpdateMode, conditions: InstallConditions): InstallDecision = when (mode) {
        UpdateMode.OFF -> InstallDecision.NOTHING
        UpdateMode.NOTIFY -> InstallDecision.NOTIFY
        UpdateMode.AUTOMATIC -> when {
            !conditions.silentInstall -> InstallDecision.NEEDS_CONFIRMATION
            conditions.playing || conditions.recentlyPaused || conditions.screenOn || conditions.appOnScreen ||
                conditions.carConnected || conditions.downloadsRunning -> InstallDecision.LATER
            else -> InstallDecision.INSTALL_NOW
        }
    }

    /**
     * Whether Android installs the app's own update without a dialog: from Android 12, when the app
     * itself installed the current version and (Android 14) no other app owns its updates.
     */
    fun silentInstallPossible(sdk: Int, ownPackage: String, installer: String?, updateOwner: String?): Boolean =
        sdk >= 31 && installer == ownPackage && (updateOwner == null || updateOwner == ownPackage)
}

/** What follows a check that found a newer release. */
enum class CheckFollowUp {
    /** The background check of the automatic or notify mode on an unmetered network. */
    DOWNLOAD_QUIETLY,

    /** «Check now»: the user is asked first, nothing is downloaded without consent (user, 5.11.2026). */
    OFFER,
    NOTHING,
}

enum class OfferAction { DOWNLOAD_AND_INSTALL, INSTALL }

/** «Version X is available · size», what the one consent button does, and what to warn about. */
data class UpdateOffer(
    val tag: String,
    val sizeBytes: Long,
    val action: OfferAction,
    /** The download would go over mobile data. */
    val metered: Boolean,
    /** Installing restarts the app and stops what plays; said once, in the offer itself. */
    val stopsPlayback: Boolean,
)

object UpdateFlow {
    fun afterCheck(manual: Boolean, mode: UpdateMode, metered: Boolean): CheckFollowUp = when {
        manual -> CheckFollowUp.OFFER
        mode == UpdateMode.OFF || metered -> CheckFollowUp.NOTHING
        else -> CheckFollowUp.DOWNLOAD_QUIETLY
    }

    /** The offer for [tag], or null when nothing newer is known. A downloaded file is only installed. */
    fun offer(tag: String?, sizeBytes: Long, downloaded: Boolean, metered: Boolean, playing: Boolean): UpdateOffer? =
        tag?.let {
            UpdateOffer(
                tag = it,
                sizeBytes = sizeBytes,
                action = if (downloaded) OfferAction.INSTALL else OfferAction.DOWNLOAD_AND_INSTALL,
                metered = metered && !downloaded,
                stopsPlayback = playing,
            )
        }
}

/** How the verified APK reaches Android's installer. */
enum class InstallPath {
    /** A package installer session: silent self-updates are possible from Android 12 (API 31). */
    SESSION,

    /**
     * The system installer screen with the file, as a file manager opens it (user, 6.11.2026: on an
     * Android 11 device the session's confirmation button did not react, a file manager worked).
     */
    CLASSIC,
}

enum class ClassicOutcome { INSTALLED, CANCELLED, FAILED }

object InstallPaths {
    /** Android 11 and older gain nothing from a session; after a failed session the classic screen is the way. */
    fun choose(sdkInt: Int, sessionFailed: Boolean): InstallPath =
        if (sdkInt <= 30 || sessionFailed) InstallPath.CLASSIC else InstallPath.SESSION

    /**
     * Whether Android's «install unknown apps» screen really opened: the app must have left the
     * foreground within [timeoutMs] of the request (a trimmed system shell may show nothing at all).
     */
    fun permissionScreenOpened(requestedAtMs: Long, pausedAtMs: Long?, timeoutMs: Long = 1_000): Boolean =
        pausedAtMs != null && pausedAtMs >= requestedAtMs && pausedAtMs - requestedAtMs <= timeoutMs

    /**
     * The installer's answer to ACTION_INSTALL_PACKAGE with EXTRA_RETURN_RESULT: OK, CANCELED, or
     * FIRST_USER with an install result code. A successful install usually replaces the app before
     * any answer arrives.
     */
    fun classicOutcome(resultCode: Int): ClassicOutcome = when (resultCode) {
        RESULT_OK -> ClassicOutcome.INSTALLED
        RESULT_CANCELED -> ClassicOutcome.CANCELLED
        else -> ClassicOutcome.FAILED
    }

    private const val RESULT_OK = -1
    private const val RESULT_CANCELED = 0
}

/**
 * The last way (user, 6.11.2026: «if nothing helps, save the apk in Downloads, the user opens it»):
 * the verified update as a file in Downloads, also handy to carry to another device.
 */
object SavedUpdate {
    const val PREFIX = "nookast-"

    /** `nookast-1.2.4.apk` for the release tag `v1.2.4`. */
    fun fileName(tag: String): String = PREFIX + tag.trim().removePrefix("v").removePrefix("V") + ".apk"

    /** Whether a file name is one of this app's saved updates. */
    fun isOurs(name: String): Boolean =
        name.startsWith(PREFIX) && name.endsWith(".apk") && name.length > PREFIX.length + 4

    /** Settings always offer it while a newer version is known; the offer after the other way failed. */
    fun showInSettings(available: String?, downloading: Boolean): Boolean = available != null && !downloading

    fun showInOffer(available: String?, classicFailed: Boolean, downloading: Boolean): Boolean =
        available != null && classicFailed && !downloading

    /** The certificate fingerprint as people compare it: pairs of upper-case hex, separated by colons. */
    fun fingerprint(sha256Hex: String): String = sha256Hex.uppercase().chunked(2).joinToString(":")
}
