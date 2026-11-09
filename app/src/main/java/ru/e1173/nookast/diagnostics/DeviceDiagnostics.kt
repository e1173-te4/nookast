package ru.e1173.nookast.diagnostics

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Window
import java.io.File
import java.util.Locale
import ru.e1173.nookast.BuildConfig
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.data.local.SyncDatabase

/**
 * Anonymous device facts for diagnostics (user, 2.11.2026: "a minimal set that helps understand why
 * a layout broke somewhere or why the feed loads oddly, on other devices"). Nothing names the user:
 * no account, server address, device identifiers, network names, location or file names, only
 * sizes, modes, counts and ages. Shown in Settings → Diagnostics and put at the top of a shared or
 * copied journal.
 */
internal object DeviceDiagnostics {
    data class Section(val title: String, val lines: List<String>)

    /** Reads the database, so it runs off the main thread. */
    fun collect(context: Context, window: Window?, nowMs: Long = System.currentTimeMillis()): List<Section> {
        val app = context.applicationContext
        return listOf(
            Section("app", app(app)),
            Section("screen", screen(context, window)),
            Section("motion", motion(app)),
            Section("network", network(app)),
            Section("power", power(app)),
            Section("memory", memory(app)),
            Section("library", library(app, nowMs)),
            Section("updates", ru.e1173.nookast.update.UpdateDiagnostics.lines(context)),
        )
    }

    fun format(sections: List<Section>): String = buildString {
        sections.forEach { section ->
            appendLine("[${section.title}]")
            section.lines.forEach { appendLine("  $it") }
        }
    }

    private fun app(context: Context) = listOf(
        "nookast ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
        "android ${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}",
        "device ${Build.MANUFACTURER} ${Build.MODEL}",
        "locale ${Locale.getDefault().toLanguageTag()}",
        // Which glass the device gets: blur from Android 12, a tinted veil before.
        "glass ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "blur" else "veil"}",
    )

    private fun screen(context: Context, window: Window?): List<String> {
        val configuration = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val lines = mutableListOf<String>()
        val windowDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window != null) {
            val bounds = window.windowManager.currentWindowMetrics.bounds
            "${(bounds.width() / metrics.density).toInt()}x${(bounds.height() / metrics.density).toInt()}dp"
        } else {
            "${configuration.screenWidthDp}x${configuration.screenHeightDp}dp"
        }
        val widthClass = when {
            configuration.screenWidthDp >= 840 -> "expanded"
            configuration.screenWidthDp >= 600 -> "medium"
            else -> "compact"
        }
        lines += "window $windowDp class=$widthClass sw=${configuration.smallestScreenWidthDp}dp " +
            "orientation=${if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}"
        // The display-size setting shows as a density above or below the device's own.
        val stable = DisplayMetrics.DENSITY_DEVICE_STABLE
        lines += "density ${metrics.densityDpi}dpi (${"%.2f".format(Locale.US, metrics.density)}x) device=${stable}dpi " +
            "displaySize=${"%.2f".format(Locale.US, metrics.densityDpi.toFloat() / stable)}"
        val bold = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) " boldText=${configuration.fontWeightAdjustment > 0}" else ""
        lines += "font scale=${"%.2f".format(Locale.US, configuration.fontScale)}$bold"
        lines += "night ${(configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window != null) {
            val insets = window.windowManager.currentWindowMetrics.windowInsets
            fun dp(px: Int) = (px / metrics.density).toInt()
            val status = insets.getInsets(android.view.WindowInsets.Type.statusBars())
            val navigation = insets.getInsets(android.view.WindowInsets.Type.navigationBars())
            val gestures = insets.getInsets(android.view.WindowInsets.Type.systemGestures())
            val cutout = insets.displayCutout
            lines += "insets status=${dp(status.top)}dp navigation=${dp(navigation.bottom)}dp " +
                "navigationSides=${dp(navigation.left)}/${dp(navigation.right)}dp " +
                "gestureNavigation=${dp(gestures.left) > 0 && dp(navigation.bottom) < 32}"
            lines += "cutout ${if (cutout == null) "none" else "top=${dp(cutout.safeInsetTop)}dp left=${dp(cutout.safeInsetLeft)}dp right=${dp(cutout.safeInsetRight)}dp"}"
        }
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        display?.let { lines += "refresh ${"%.0f".format(Locale.US, it.refreshRate)}Hz displays=${context.getSystemService(android.hardware.display.DisplayManager::class.java)?.displays?.size ?: 1}" }
        return lines
    }

    private fun motion(context: Context): List<String> {
        val resolver = context.contentResolver
        fun scale(name: String) = runCatching { Settings.Global.getFloat(resolver, name) }.getOrDefault(1f)
        return listOf(
            "animator=${scale(Settings.Global.ANIMATOR_DURATION_SCALE)} transition=${scale(Settings.Global.TRANSITION_ANIMATION_SCALE)} " +
                "window=${scale(Settings.Global.WINDOW_ANIMATION_SCALE)}",
        )
    }

    private fun network(context: Context): List<String> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return listOf("unknown")
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            ?: return listOf("none", "dataSaver=${dataSaver(manager)}")
        val transports = listOf(
            NetworkCapabilities.TRANSPORT_WIFI to "wifi",
            NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
            NetworkCapabilities.TRANSPORT_BLUETOOTH to "bluetooth",
            NetworkCapabilities.TRANSPORT_VPN to "vpn",
        ).filter { capabilities.hasTransport(it.first) }.joinToString("+") { it.second }.ifEmpty { "other" }
        return listOf(
            "transport $transports validated=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} " +
                "metered=${!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}",
            "bandwidth down=${capabilities.linkDownstreamBandwidthKbps}kbps up=${capabilities.linkUpstreamBandwidthKbps}kbps",
            "dataSaver=${dataSaver(manager)}",
        )
    }

    private fun dataSaver(manager: ConnectivityManager) = when (manager.restrictBackgroundStatus) {
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "on"
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "on, app allowed"
        else -> "off"
    }

    private fun power(context: Context): List<String> {
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket) {
                UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
                UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
                UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
                UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
                else -> "other"
            }
        } else {
            "n/a"
        }
        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activity?.isBackgroundRestricted else null
        return listOf(
            "batterySaver=${power?.isPowerSaveMode} batteryOptimized=${power?.isIgnoringBatteryOptimizations(context.packageName) == false}",
            "standby=$bucket backgroundRestricted=$restricted",
        )
    }

    private fun memory(context: Context): List<String> {
        val activity = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
        fun gb(bytes: Long) = "%.1f".format(Locale.US, bytes / 1_073_741_824.0)
        val free = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
        val downloads = listOfNotNull(context.getExternalFilesDir(null), File(context.filesDir, "downloads"))
            .sumOf { directorySize(it) }
        return listOf(
            "ram total=${gb(info.totalMem)}GB available=${gb(info.availMem)}GB low=${info.lowMemory} lowRamDevice=${activity?.isLowRamDevice}",
            "heap class=${activity?.memoryClass}MB max=${Runtime.getRuntime().maxMemory() / 1_048_576}MB",
            "storage free=${gb(free)}GB appFiles=${"%.0f".format(Locale.US, downloads / 1_048_576.0)}MB",
        )
    }

    private fun directorySize(directory: File): Long =
        runCatching { directory.walkTopDown().filter(File::isFile).sumOf(File::length) }.getOrDefault(0L)

    /** Counts and ages only: how big the library is, how its feeds refresh and what waits to sync. */
    private fun library(context: Context, nowMs: Long): List<String> {
        val database = runCatching { SyncDatabase.open(context).openHelper.readableDatabase }.getOrNull()
            ?: return listOf("unavailable")
        fun count(sql: String): Long = runCatching {
            database.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        }.getOrDefault(-1L)
        fun age(epochMs: Long): String = if (epochMs <= 0) "never" else "${(nowMs - epochMs) / 60_000}min ago"
        val lines = mutableListOf<String>()
        lines += "podcasts=${count("SELECT COUNT(*) FROM podcast_subscriptions")} " +
            "episodesCached=${count("SELECT COUNT(*) FROM queue_episode_cache")} " +
            "detailsCached=${count("SELECT COUNT(*) FROM podcast_details_cache")}"
        val refreshed = count("SELECT COUNT(*) FROM podcast_refresh_state")
        val failing = count("SELECT COUNT(*) FROM podcast_refresh_state WHERE last_error_category IS NOT NULL")
        val newest = count("SELECT COALESCE(MAX(last_success_at_epoch_ms), 0) FROM podcast_refresh_state")
        val oldest = count("SELECT COALESCE(MIN(last_success_at_epoch_ms), 0) FROM podcast_refresh_state WHERE last_success_at_epoch_ms IS NOT NULL")
        lines += "feeds refreshed=$refreshed failing=$failing newest=${age(newest)} oldest=${age(oldest)}"
        runCatching {
            database.query(
                "SELECT last_error_category, COUNT(*) FROM podcast_refresh_state " +
                    "WHERE last_error_category IS NOT NULL GROUP BY last_error_category",
            ).use { cursor ->
                val errors = buildList { while (cursor.moveToNext()) add("${cursor.getString(0)}=${cursor.getLong(1)}") }
                if (errors.isNotEmpty()) lines += "feed errors ${errors.joinToString(" ")}"
            }
        }
        lines += "pending progress=${count("SELECT COUNT(*) FROM playback_progress WHERE dirty = 1")} " +
            "queue=${count("SELECT COUNT(*) FROM queue_intents")} deadLetters=${count("SELECT COUNT(*) FROM dead_letter")}"
        val status = context.appGraph.syncScheduler.status.value
        lines += "sync last=${age(status.lastSuccessWallMs ?: 0L)} failure=${status.lastFailure ?: "none"} " +
            "running=${status.running} needsSignIn=${status.needsSignIn}"
        return lines
    }
}
