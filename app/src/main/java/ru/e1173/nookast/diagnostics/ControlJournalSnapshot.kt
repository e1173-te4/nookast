package ru.e1173.nookast.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import ru.e1173.nookast.BuildConfig
import ru.e1173.nookast.player.currentMediaDeviceIdentity

/**
 * Device facts written once when journal recording starts: what the head unit reports about
 * itself, which UI and media-button profiles are in effect, and which media apps the shell can
 * see. Runs on the journal writer thread.
 */
internal object ControlJournalSnapshot {

    fun capture(context: Context): String = buildString {
        val app = context.applicationContext
        appendLine("app ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine(
            "build manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL} " +
                "device=${Build.DEVICE} product=${Build.PRODUCT} hardware=${Build.HARDWARE} " +
                "board=${Build.BOARD}",
        )
        appendLine("android ${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} incremental=${Build.VERSION.INCREMENTAL}")
        appendLine("locale ${Locale.getDefault().toLanguageTag()}")

        val configuration = app.resources.configuration
        val metrics = app.resources.displayMetrics
        appendLine(
            "logical ${configuration.screenWidthDp}x${configuration.screenHeightDp}dp " +
                "sw=${configuration.smallestScreenWidthDp}dp density=${metrics.densityDpi}dpi " +
                "(${"%.2f".format(Locale.US, metrics.density)}x) fontScale=" +
                "%.2f".format(Locale.US, configuration.fontScale),
        )
        val displayManager = app.getSystemService(DisplayManager::class.java)
        displayManager?.displays?.forEach { display -> appendLine(describeDisplay(display)) }

        appendLine("head unit=" + (ru.e1173.nookast.player.KnownHeadUnits.detect(currentMediaDeviceIdentity(app))?.mediaProfileId ?: "none"))

        val notificationsEnabled = NotificationManagerCompat.from(app).areNotificationsEnabled()
        val postGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        appendLine("notifications enabled=$notificationsEnabled postPermission=$postGranted")

        val audio = app.getSystemService(AudioManager::class.java)
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.forEach { device ->
            appendLine("audio output ${ControlJournalFormat.audioDevice(device)}")
        }

        MEDIA_SERVICE_ACTIONS.forEach { action ->
            val packages = queryServicePackages(app, action)
            appendLine("services $action count=${packages.size} ${packages.joinToString()}")
        }
    }

    private fun describeDisplay(display: Display): String {
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(real)
        return "display id=${display.displayId} name=\"${display.name}\" " +
            "real=${real.widthPixels}x${real.heightPixels}px dpi=${real.densityDpi} " +
            "xdpi=${"%.1f".format(Locale.US, real.xdpi)} ydpi=${"%.1f".format(Locale.US, real.ydpi)} " +
            "rotation=${display.rotation} refresh=${"%.1f".format(Locale.US, display.refreshRate)}"
    }

    private fun queryServicePackages(context: Context, action: String): List<String> =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(Intent(action), 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())

    /** Must match the `<queries>` element in the manifest, otherwise Android 11+ hides them. */
    private val MEDIA_SERVICE_ACTIONS = listOf(
        "android.media.browse.MediaBrowserService",
        "androidx.media3.session.MediaLibraryService",
        "androidx.media3.session.MediaSessionService",
    )
}
