package ru.e1173.nookast.update

import android.content.Context
import java.io.File

/**
 * A short log of update installs, so a failed install can be understood without adb (user,
 * 6.11.2026). It goes into the diagnostics export and copy with the device data. Never a key,
 * a server address or a file path beyond the app's own.
 */
object UpdateLog {
    private const val FILE = "update-log.txt"
    private const val MAX_LINES = 200

    @Synchronized
    fun record(context: Context, line: String) {
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE)
            val stamp = java.time.LocalDateTime.now().withNano(0).toString()
            val kept = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
            file.writeText((kept + "$stamp $line").joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun lines(context: Context): List<String> =
        runCatching { File(context.applicationContext.filesDir, FILE).takeIf(File::exists)?.readLines() }
            .getOrNull().orEmpty()
}
