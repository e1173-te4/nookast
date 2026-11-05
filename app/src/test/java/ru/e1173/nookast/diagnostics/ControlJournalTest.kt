package ru.e1173.nookast.diagnostics

import android.app.Application
import android.content.Context
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ControlJournalTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = object : ControlJournal.Clock {
        var elapsed = 4_512_031L
        override fun elapsedRealtimeMs(): Long = elapsed
        override fun wallTimeMs(): Long = 1_793_000_000_000L
    }

    private fun journal(maxBytes: Long = ControlJournal.MAX_BYTES): ControlJournal {
        val context = RuntimeEnvironment.getApplication()
        return ControlJournal(
            directory = folder.root,
            preferences = context.getSharedPreferences("control_journal_test", Context.MODE_PRIVATE)
                .also { it.edit().clear().commit() },
            clock = clock,
            maxBytes = maxBytes,
            writer = { it.run() },
        )
    }

    @Test
    fun recordsNothingUntilRecordingIsEnabled() {
        val journal = journal()

        journal.record(ControlJournal.Category.MEDIA_KEY, "ignored")
        journal.mark("ignored")

        assertFalse(journal.isRecording)
        assertEquals("", journal.readAll())
    }

    @Test
    fun startWritesHeaderAndSnapshotThenRecordsInOrder() {
        val journal = journal()

        journal.setRecording(true) { "build model=X\n\ndisplay id=0" }
        journal.record(ControlJournal.Category.MEDIA_KEY, "action=DOWN key=MEDIA_NEXT(87)")
        journal.mark("  Wheel: next\nsecond line ")
        journal.setRecording(false)
        journal.record(ControlJournal.Category.MEDIA_KEY, "after stop")

        val lines = journal.readAll().lines().filter { it.isNotEmpty() }
        assertEquals(6, lines.size)
        assertTrue(lines[0].endsWith("SESSION recording started"))
        assertTrue(lines[1].endsWith("SNAP build model=X"))
        assertTrue(lines[2].endsWith("SNAP display id=0"))
        assertTrue(lines[3].endsWith("KEY-SESSION action=DOWN key=MEDIA_NEXT(87)"))
        assertTrue(lines[4].endsWith("MARK Wheel: next second line"))
        assertTrue(lines[5].endsWith("SESSION recording stopped"))
    }

    @Test
    fun recordingStateSurvivesRecreation() {
        journal().setRecording(true)

        val context = RuntimeEnvironment.getApplication()
        val reopened = ControlJournal(
            directory = folder.root,
            preferences = context.getSharedPreferences("control_journal_test", Context.MODE_PRIVATE),
            clock = clock,
            writer = { it.run() },
        )

        assertTrue(reopened.isRecording)
    }

    @Test
    fun ringKeepsTotalSizeBoundedAndNewestRecords() {
        val journal = journal(maxBytes = 4_000)
        journal.setRecording(true)

        repeat(200) { index ->
            journal.record(ControlJournal.Category.PLAYER, "record-$index")
        }

        assertTrue(journal.sizeBytes() <= 4_000)
        val text = journal.readAll()
        assertTrue(text.contains("record-199"))
        assertFalse(text.contains("record-0\n"))
        assertTrue(File(folder.root, ControlJournal.PREVIOUS_FILE).isFile)
    }

    @Test
    fun clearDeletesRecordsAndExportWritesOneFile() {
        val journal = journal()
        journal.setRecording(true)
        journal.record(ControlJournal.Category.AUDIO, "device added")

        val exportDirectory = folder.newFolder("export")
        val first = journal.exportTo(exportDirectory)
        val second = journal.exportTo(exportDirectory)
        assertEquals(1, exportDirectory.listFiles()!!.size)
        assertEquals(second, exportDirectory.listFiles()!!.single())
        assertTrue(first.name.startsWith("nookast-control-journal-"))
        assertTrue(second.readText().contains("device added"))

        journal.clear()
        assertEquals("", journal.readAll())
        assertTrue(journal.isRecording)
    }

    @Test
    fun lineCarriesMonotonicAndWallTime() {
        val line = ControlJournal.formatLine(
            elapsedRealtimeMs = 4_512_031L,
            wall = Instant.ofEpochMilli(0),
            category = ControlJournal.Category.CONTROLLER,
            message = "connect a\nb",
        )

        assertTrue(line.startsWith("+   4512.031 "))
        assertTrue(line.endsWith(" CONTROLLER connect a | b\n"))
    }
}
