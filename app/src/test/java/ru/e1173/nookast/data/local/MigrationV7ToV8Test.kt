package ru.e1173.nookast.data.local

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Room 7 → 8 with a pending row of every v7 kind (docs/SYNC.md §12, scenario S25). Fixtures
 * include the glued v7 rows the data-loss review found: a PERIODIC tick after UNCOMPLETE or after
 * a backward SEEK overwrote the cause in v7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class MigrationV7ToV8Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SyncDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val path: String = RuntimeEnvironment.getApplication().getDatabasePath(DB).absolutePath

    @Test
    fun everyPendingV7IntentSurvivesWithTheRightKind() {
        seedV7()

        migrate(preferencesLastSequence = 9_000).use { db ->
            fun kind(episode: Int) = db.text("SELECT intent_kind FROM playback_progress WHERE episode_id = $episode")

            assertEquals(SyncV8.IntentKind.COMPLETE, kind(1))
            assertEquals("UNCOMPLETE then PERIODIC behind a completed server", SyncV8.IntentKind.REWIND, kind(2))
            assertEquals("backward SEEK then PERIODIC behind the podcast cache", SyncV8.IntentKind.REWIND, kind(3))
            assertEquals(SyncV8.IntentKind.PASSIVE, kind(4))
            assertEquals("SEEK without any observation", SyncV8.IntentKind.REWIND, kind(5))
            assertEquals(SyncV8.IntentKind.PASSIVE, kind(6))

            assertEquals(6L, db.long("SELECT COUNT(*) FROM playback_progress WHERE dirty = 1 AND migrated = 1"))
            assertEquals(120_000L, db.long("SELECT position_ms FROM playback_progress WHERE episode_id = 2"))
            assertEquals(0L, db.long("SELECT completed FROM playback_progress WHERE episode_id = 2"))
            assertEquals(1_002L, db.long("SELECT rewind_base_counter FROM playback_progress WHERE episode_id = 2"))
            assertEquals(1_500L, db.long("SELECT base_server_position_s FROM playback_progress WHERE episode_id = 3"))
            // The progress row for episode 6 existed only in the outbox; it is created.
            assertEquals(6L, db.long("SELECT COUNT(*) FROM playback_progress"))
        }
    }

    @Test
    fun queueOutboxBecomesOrderedIntentsWithAnchorsAndCompletionDependency() {
        seedV7()

        migrate(preferencesLastSequence = null).use { db ->
            val rows = db.query(
                "SELECT kind, episode_id, anchor_kind, anchor_index, depends_on_counter, operation_id " +
                    "FROM queue_intents ORDER BY counter, operation_id, id",
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            listOf(
                                c.getString(0),
                                if (c.isNull(1)) null else c.getInt(1),
                                c.getString(2),
                                if (c.isNull(3)) null else c.getInt(3),
                                if (c.isNull(4)) null else c.getLong(4),
                                c.getString(5),
                            ),
                        )
                    }
                }
            }
            assertEquals(
                listOf(
                    listOf("ADD", 7, "LAST", null, null, "op-a"),
                    listOf("ADD", 8, "INDEX", 0, null, "op-b"),
                    listOf("MOVE", 9, "INDEX", 2, null, "op-c1"),
                    listOf("MOVE", 9, "INDEX", 1, null, "op-c2"),
                    listOf("REMOVE", 1, null, null, 1_001L, "op-d"),
                    listOf("CLEAR", null, null, null, null, "op-e"),
                ),
                rows,
            )
            assertEquals(0L, db.long("SELECT server_known FROM queue_state"))
            assertEquals("", db.text("SELECT server_fingerprint FROM queue_state"))
            // queue_items of v7 stay as they are until the first observation.
            assertEquals(3L, db.long("SELECT COUNT(*) FROM queue_items"))
        }
    }

    @Test
    fun counterExceedsEveryV7SequenceAndThePreferencesCounter() {
        seedV7()

        migrate(preferencesLastSequence = 9_000).use { db ->
            assertEquals(9_001L, db.long("SELECT value FROM sync_counter"))
        }
    }

    @Test
    fun counterIgnoresASmallerPreferencesCounterAndCountsDeferredIntents() {
        seedV7()

        migrate(preferencesLastSequence = 5).use { db ->
            // deferred_episode_intents holds the largest v7 sequence (2_000).
            assertEquals(2_001L, db.long("SELECT value FROM sync_counter"))
        }
    }

    @Test
    fun rssRowsGetStableNegativeIdsAndOtherOutboxesAreKept() {
        seedV7()

        migrate(preferencesLastSequence = null).use { db ->
            assertEquals(-1L, db.long("SELECT rss_playback_id FROM podcast_episode_cache WHERE cache_key = 'rss:a'"))
            assertEquals(-2L, db.long("SELECT rss_playback_id FROM podcast_episode_cache WHERE cache_key = 'rss:b'"))
            assertNull(db.textOrNull("SELECT rss_playback_id FROM podcast_episode_cache WHERE cache_key = 'pinepods:3'"))
            assertEquals(-3L, db.long("SELECT next_rss_playback_id FROM sync_counter"))

            assertEquals(1L, db.long("SELECT COUNT(*) FROM saved_episode_outbox WHERE account_fp IS NULL"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM podcast_outbox WHERE account_fp IS NULL"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM deferred_episode_intents"))
            // v7 outboxes are converted, not consumed.
            assertEquals(6L, db.long("SELECT COUNT(*) FROM progress_outbox"))
            assertEquals(6L, db.long("SELECT COUNT(*) FROM queue_outbox"))
        }
    }

    @Test
    fun emptyV7DatabaseMigrates() {
        helper.createDatabase(path, 7).close()

        migrate(preferencesLastSequence = null).use { db ->
            assertEquals(1L, db.long("SELECT value FROM sync_counter"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM queue_intents"))
        }
    }

    @Test
    fun migratedKindRules() {
        val ahead = MigrationObservation(positionSeconds = 1_500, completed = false)
        assertEquals(SyncV8.IntentKind.PASSIVE, migratedProgressKind(false, "PERIODIC", 2_000_000, ahead))
        assertEquals(SyncV8.IntentKind.REWIND, migratedProgressKind(false, "PAUSE", 1_000_000, ahead))
        assertEquals(SyncV8.IntentKind.COMPLETE, migratedProgressKind(true, "PERIODIC", 0, ahead))
        assertEquals(SyncV8.IntentKind.UNCOMPLETE, migratedProgressKind(false, "UNCOMPLETE", 0, null))
    }

    private fun migrate(preferencesLastSequence: Long?): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(path, 8, true, MigrationV7ToV8(preferencesLastSequence))

    private fun seedV7() = helper.createDatabase(path, 7).apply {
        // Progress outbox: one row per case.
        progress(1, 3_600_000, completed = true, cause = "COMPLETE", seq = 1_001)
        progress(2, 120_000, completed = false, cause = "PERIODIC", seq = 1_002) // after UNCOMPLETE
        progress(3, 100_000, completed = false, cause = "PERIODIC", seq = 1_003) // after SEEK back
        progress(4, 2_000_000, completed = false, cause = "PERIODIC", seq = 1_004)
        progress(5, 50_000, completed = false, cause = "SEEK", seq = 1_005)
        progress(6, 70_000, completed = false, cause = "PERIODIC", seq = 1_006, withProgressRow = false)
        // Observations: episode 2 completed in the queue cache, 3 and 4 at 1500 s in the podcast cache.
        execSQL(
            "INSERT INTO queue_episode_cache VALUES (2, 'E2', 'P', '', '', 3600, 3600.0, 1, '', '', 0, 10)",
        )
        podcastCache("pinepods:3", pinePodsId = 3, listened = 1_500.0)
        podcastCache("pinepods:4", pinePodsId = 4, listened = 1_500.0)
        podcastCache("rss:b", pinePodsId = null, listened = 0.0)
        podcastCache("rss:a", pinePodsId = null, listened = 0.0)

        // Queue: v7 effective queue and outbox (equal sequences on op-c1/op-c2).
        listOf(7, 8, 9).forEachIndexed { index, id ->
            execSQL("INSERT INTO queue_items VALUES ($id, $index, 10)")
        }
        execSQL("INSERT INTO queue_state VALUES (1, 'abc', 3, 10)")
        queue("op-a", "ADD", 7, null, 1_100)
        queue("op-b", "ADD", 8, 0, 1_101)
        queue("op-c2", "MOVE", 9, 1, 1_102)
        queue("op-c1", "MOVE", 9, 2, 1_102)
        queue("op-d", "REMOVE", 1, null, 1_103)
        queue("op-e", "CLEAR", null, null, 1_104)

        execSQL("INSERT INTO saved_episode_outbox VALUES (5, 1, 1200, 10, 2, 99, 'NETWORK')")
        execSQL("INSERT INTO podcast_outbox VALUES ('pod-1', 'REMOVE', 12, 1300, 10, 0, 0, NULL)")
        execSQL(
            "INSERT INTO deferred_episode_intents VALUES ('rss:a', 'SAVED', 1, NULL, NULL, NULL, NULL, 2000, 10)",
        )
        close()
    }

    private fun SupportSQLiteDatabase.progress(
        episode: Int,
        positionMs: Long,
        completed: Boolean,
        cause: String,
        seq: Long,
        withProgressRow: Boolean = true,
    ) {
        val flag = if (completed) 1 else 0
        if (withProgressRow) {
            execSQL("INSERT INTO playback_progress VALUES ($episode, $positionMs, 3600000, $flag, '$cause', $seq, 10, NULL, NULL)")
        }
        execSQL("INSERT INTO progress_outbox VALUES ($episode, $positionMs, 3600000, $flag, '$cause', $seq, 10, 1, 0, NULL)")
    }

    private fun SupportSQLiteDatabase.podcastCache(key: String, pinePodsId: Int?, listened: Double) {
        execSQL(
            "INSERT INTO podcast_episode_cache (cache_key, podcast_id, pinepods_episode_id, guid, source, title, " +
                "description, published_at, duration_seconds, listened_seconds, completed, podcast_name, " +
                "artwork_url, media_url, downloaded, refreshed_at_epoch_ms) VALUES " +
                "('$key', 12, ${pinePodsId ?: "NULL"}, NULL, 'X', 'T', '', '', 3600, $listened, 0, 'P', '', '$key', 0, 10)",
        )
    }

    private fun SupportSQLiteDatabase.queue(op: String, kind: String, episode: Int?, index: Int?, seq: Long) {
        execSQL(
            "INSERT INTO queue_outbox VALUES ('$op', '$kind', ${episode ?: "NULL"}, ${index ?: "NULL"}, $seq, 10, 0, 0, NULL)",
        )
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    private fun SupportSQLiteDatabase.textOrNull(sql: String): String? =
        query(sql).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private companion object {
        const val DB = "migration-v8-test"
    }
}
