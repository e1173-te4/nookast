package ru.e1173.nookast.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The update install facts for the diagnostics (user, 6.11.2026): which screens Android has for
 * the permission and the install, whether the app may install, and [UpdateLog]'s record of tries.
 */
object UpdateDiagnostics {
    fun lines(context: Context): List<String> {
        val app = context.applicationContext
        val pm = app.packageManager
        fun handler(intent: Intent): String =
            runCatching { intent.resolveActivity(pm)?.flattenToShortString() }.getOrNull() ?: "none"
        val apkType = "application/vnd.android.package-archive"
        val sample = "content://${app.packageName}.diagnostics/updates/sample.apk".toUri()
        @Suppress("DEPRECATION")
        return buildList {
            add("build sdk=${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL}")
            add("canRequestPackageInstalls=${runCatching { pm.canRequestPackageInstalls() }.getOrDefault(false)}")
            add("multiWindow=${(context as? android.app.Activity)?.isInMultiWindowMode ?: "-"}")
            add("permission screen=" + handler(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri())))
            add("install screen=" + handler(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(sample, apkType)))
            add("view screen=" + handler(Intent(Intent.ACTION_VIEW).setDataAndType(sample, apkType)))
            add("confirm screen=" + handler(Intent("android.content.pm.action.CONFIRM_INSTALL")))
            addAll(UpdateLog.lines(app).takeLast(40))
        }
    }
}
