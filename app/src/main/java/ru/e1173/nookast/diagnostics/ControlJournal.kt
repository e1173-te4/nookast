package ru.e1173.nookast.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opt-in journal of everything that controls playback from outside the app: media keys, media
 * controllers (head unit shell, Android Auto, Bluetooth), browser calls, audio focus and output
 * devices. The user records a trip, adds markers describing what they pressed and exports the
 * file for analysis (EXECUTION_PLAN 2b.1, DEC-003).
 *
 * The journal never contains the server URL, API keys or episode titles: callers pass only
 * hardware identifiers, package names, command codes and episode IDs.
 *
 * Storage is a two-file ring in the app's private directory, bounded by [maxBytes] in total.
 * Lines are formatted on the calling thread (so the timestamps and order are exact) and appended
 * on a single background thread.
 */
class ControlJournal internal constructor(
    private val directory: File,
    private val preferences: SharedPreferences,
    private val clock: Clock = SystemJournalClock,
    private val maxBytes: Long = MAX_BYTES,
    private val writer: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "control-journal").apply { isDaemon = true }
    },
) {
    constructor(context: Context) : this(
        directory = File(context.applicationContext.filesDir, DIRECTORY),
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    interface Clock {
        fun elapsedRealtimeMs(): Long
        fun wallTimeMs(): Long
    }

    private val current = File(directory, CURRENT_FILE)
    private val previous = File(directory, PREVIOUS_FILE)
    private val lock = Any()

    private val _recording = MutableStateFlow(preferences.getBoolean(KEY_RECORDING, false))

    /** Whether new records are written. Off by default. */
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _revision = MutableStateFlow(0L)

    /** Increments after every write or clear, so a visible journal view knows to re-read. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    val isRecording: Boolean get() = _recording.value

    /**
     * Starts or stops recording. Starting writes a header followed by [snapshot], evaluated on
     * the writer thread because it queries package manager and display services.
     */
    fun setRecording(enabled: Boolean, snapshot: () -> String = { "" }) {
        if (_recording.value == enabled) return
        if (enabled) {
            _recording.value = true
            preferences.edit { putBoolean(KEY_RECORDING, true) }
            val header = format(Category.SESSION, "recording started")
            writer.execute {
                val snapshotLines = runCatching(snapshot).getOrElse { error ->
                    "snapshot failed: ${error.javaClass.simpleName}"
                }
                val body = snapshotLines.lineSequence()
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "") { formatAt(header.first, Category.SNAPSHOT, it) }
                append(header.second + body)
            }
        } else {
            record(Category.SESSION, "recording stopped")
            _recording.value = false
            preferences.edit { putBoolean(KEY_RECORDING, false) }
        }
    }

    /** Appends one record when recording is on. Safe to call from any thread. */
    fun record(category: Category, message: String) {
        if (!_recording.value) return
        val line = format(category, message).second
        writer.execute { append(line) }
    }

    /** Lazily built variant of [record] that skips formatting work while recording is off. */
    inline fun record(category: Category, message: () -> String) {
        if (isRecording) record(category, message())
    }

    /** A user marker describing the physical action they are about to perform. */
    fun mark(text: String) {
        val clean = text.replace('\n', ' ').trim().take(MAX_MARK_LENGTH)
        if (clean.isNotEmpty()) record(Category.MARK, clean)
    }

    /** Whole journal, oldest first. Blocking: call off the main thread. */
    fun readAll(): String = flushAndRead { buildString {
        if (previous.isFile) append(previous.readText(Charsets.UTF_8))
        if (current.isFile) append(current.readText(Charsets.UTF_8))
    } }

    /** Last [lines] lines, for an on-screen preview. Blocking: call off the main thread. */
    fun tail(lines: Int): List<String> = readAll().lineSequence()
        .filter { it.isNotEmpty() }
        .toList()
        .takeLast(lines)

    /** Total stored size in bytes. Blocking: call off the main thread. */
    fun sizeBytes(): Long = synchronized(lock) { current.length() + previous.length() }

    /**
     * Writes the whole journal into [exportDirectory] and returns the file, for sharing through
     * a FileProvider. Blocking: call off the main thread.
     */
    fun exportTo(exportDirectory: File): File {
        val text = readAll()
        exportDirectory.mkdirs()
        exportDirectory.listFiles()?.forEach { it.delete() }
        val stamp = EXPORT_STAMP.format(Instant.ofEpochMilli(clock.wallTimeMs()))
        return File(exportDirectory, "nookast-control-journal-$stamp.txt").apply {
            writeText(text, Charsets.UTF_8)
        }
    }

    /** Deletes all records. Recording state is unchanged. */
    fun clear() {
        writer.execute {
            synchronized(lock) {
                current.delete()
                previous.delete()
            }
            _revision.value += 1
        }
    }

    private fun <T> flushAndRead(read: () -> T): T {
        // Wait for queued appends so the reader sees every record made before this call.
        val barrier = java.util.concurrent.CountDownLatch(1)
        writer.execute { barrier.countDown() }
        barrier.await(FLUSH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        return synchronized(lock) { read() }
    }

    private fun append(text: String) {
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                if (current.length() + text.length > maxBytes / 2) {
                    previous.delete()
                    current.renameTo(previous)
                }
                current.appendText(text, Charsets.UTF_8)
            }
        }
        _revision.value += 1
    }

    private fun format(category: Category, message: String): Pair<Instant, String> {
        val elapsed = clock.elapsedRealtimeMs()
        val wall = Instant.ofEpochMilli(clock.wallTimeMs())
        return wall to formatLine(elapsed, wall, category, message)
    }

    private fun formatAt(wall: Instant, category: Category, message: String): String =
        formatLine(clock.elapsedRealtimeMs(), wall, category, message)

    enum class Category(val tag: String) {
        SESSION("SESSION"),

        /** Power and connectivity of the device: screen, shutdown, network (ignition-off order). */
        DEVICE("DEVICE"),
        SNAPSHOT("SNAP"),

        /** Media key delivered to the media session (steering wheel, headset, head unit). */
        MEDIA_KEY("KEY-SESSION"),

        /** Key delivered to the visible activity. */
        ACTIVITY_KEY("KEY-ACTIVITY"),
        CONTROLLER("CONTROLLER"),
        COMMAND("COMMAND"),
        LIBRARY("LIBRARY"),
        PLAYER("PLAYER"),
        AUDIO("AUDIO"),
        BROADCAST("BROADCAST"),
        MARK("MARK"),
    }

    companion object {
        const val MAX_BYTES: Long = 2L * 1024 * 1024
        const val MAX_MARK_LENGTH = 200
        internal const val DIRECTORY = "diagnostics"
        internal const val CURRENT_FILE = "control-journal.log"
        internal const val PREVIOUS_FILE = "control-journal.1.log"
        private const val PREFERENCES_NAME = "control_journal"
        private const val KEY_RECORDING = "recording"
        private const val FLUSH_TIMEOUT_MS = 5_000L

        private val WALL_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .withZone(ZoneId.systemDefault())
        private val EXPORT_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        /**
         * One record: monotonic time since boot (seconds, ms precision), local wall time,
         * category and message. Example:
         * `+  4512.031 2026-10-24T18:02:11.204+03:00 KEY-SESSION action=DOWN key=MEDIA_NEXT(87)`.
         */
        internal fun formatLine(
            elapsedRealtimeMs: Long,
            wall: Instant,
            category: Category,
            message: String,
        ): String {
            val seconds = elapsedRealtimeMs / 1_000
            val millis = (elapsedRealtimeMs % 1_000).toString().padStart(3, '0')
            val monotonic = "+" + seconds.toString().padStart(7) + "." + millis
            val oneLine = message.replace("\r", "").replace("\n", " | ")
            return "$monotonic ${WALL_FORMAT.format(wall)} ${category.tag} $oneLine\n"
        }
    }
}

private object SystemJournalClock : ControlJournal.Clock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun wallTimeMs(): Long = System.currentTimeMillis()
}
