package ru.e1173.nookast.data.local

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 7 → 8 (docs/SYNC.md §12): adds the tables and columns of the synchronization redesign and
 * converts every pending v7 row, so no intent is lost on upgrade.
 *
 * [preferencesLastSequence] is the v7 SharedPreferences counter (`sync_sequence/last_sequence`),
 * read before the database is built because a migration has no Context.
 *
 * The migration never deletes v7 data. The old outbox tables stay until the engine switch drops
 * them in the same release (they are converted here, not consumed).
 */
internal class MigrationV7ToV8(private val preferencesLastSequence: Long?) : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)

        val counter = seedCounter(db)
        convertProgress(db)
        convertQueue(db)
        db.execSQL("UPDATE queue_state SET server_fingerprint = '', server_known = 0")
        val nextRss = assignRssPlaybackIds(db)
        db.execSQL(
            "INSERT INTO sync_counter (singleton_id, value, next_rss_playback_id) VALUES (1, ?, ?)",
            arrayOf<Any>(counter, nextRss),
        )
    }

    /** max over every v7 sequence column and the preferences counter, plus one. */
    private fun seedCounter(db: SupportSQLiteDatabase): Long {
        val maxima = listOf(
            "SELECT MAX(local_sequence) FROM progress_outbox",
            "SELECT MAX(local_sequence) FROM playback_progress",
            "SELECT MAX(last_confirmed_sequence) FROM playback_progress",
            "SELECT MAX(sequence) FROM queue_outbox",
            "SELECT MAX(sequence) FROM saved_episode_outbox",
            "SELECT MAX(sequence) FROM podcast_outbox",
            "SELECT MAX(sequence) FROM deferred_episode_intents",
        ).map { db.singleLongOrNull(it) ?: 0L }
        return maxOf(maxima.max(), preferencesLastSequence ?: 0L) + 1
    }

    private fun convertProgress(db: SupportSQLiteDatabase) {
        val rows = db.query(
            "SELECT episode_id, position_ms, duration_ms, completed, cause, local_sequence, " +
                "recorded_at_epoch_ms FROM progress_outbox",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        V7ProgressRow(
                            episodeId = cursor.getInt(0),
                            positionMs = cursor.getLong(1),
                            durationMs = cursor.longOrNull(2),
                            completed = cursor.getInt(3) != 0,
                            cause = cursor.getString(4),
                            sequence = cursor.getLong(5),
                            recordedAtEpochMs = cursor.getLong(6),
                        ),
                    )
                }
            }
        }
        rows.forEach { row ->
            val observation = bestObservation(db, row.episodeId)
            val kind = migratedProgressKind(row.completed, row.cause, row.positionMs, observation)
            val rewinding = kind == SyncV8.IntentKind.REWIND || kind == SyncV8.IntentKind.UNCOMPLETE
            db.execSQL(
                "INSERT OR IGNORE INTO playback_progress (episode_id, position_ms, duration_ms, " +
                    "completed, cause, local_sequence, updated_at_epoch_ms) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    row.episodeId,
                    row.positionMs,
                    row.durationMs,
                    if (row.completed) 1 else 0,
                    row.cause,
                    row.sequence,
                    row.recordedAtEpochMs,
                ),
            )
            db.execSQL(
                "UPDATE playback_progress SET position_ms = ?, duration_ms = ?, completed = ?, " +
                    "dirty = 1, intent_kind = ?, intent_counter = ?, rewind_base_counter = ?, " +
                    "rewind_phase = 0, base_server_position_s = ?, migrated = 1 WHERE episode_id = ?",
                arrayOf<Any?>(
                    row.positionMs,
                    row.durationMs,
                    if (row.completed) 1 else 0,
                    kind,
                    row.sequence,
                    if (rewinding) row.sequence else null,
                    observation?.positionSeconds,
                    row.episodeId,
                ),
            )
        }
    }

    /** The best server knowledge v7 has about an episode: maximum of both caches. */
    private fun bestObservation(db: SupportSQLiteDatabase, episodeId: Int): MigrationObservation? {
        val queue = db.query(
            "SELECT listen_duration_seconds, completed FROM queue_episode_cache WHERE episode_id = ?",
            arrayOf<Any>(episodeId),
        ).use { it.observation() }
        val podcast = db.query(
            "SELECT listened_seconds, completed FROM podcast_episode_cache WHERE pinepods_episode_id = ?",
            arrayOf<Any>(episodeId),
        ).use { it.observation() }
        if (queue == null) return podcast
        if (podcast == null) return queue
        return MigrationObservation(
            positionSeconds = maxOf(queue.positionSeconds, podcast.positionSeconds),
            completed = queue.completed || podcast.completed,
        )
    }

    private fun convertQueue(db: SupportSQLiteDatabase) {
        val completions = db.query(
            "SELECT episode_id, local_sequence FROM progress_outbox WHERE completed = 1",
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getInt(0), cursor.getLong(1)) }
        }
        db.query(
            "SELECT operation_id, kind, episode_id, destination_index, sequence FROM queue_outbox " +
                "ORDER BY sequence, operation_id",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val kind = cursor.getString(1)
                val episodeId = cursor.intOrNull(2)
                val index = cursor.intOrNull(3)
                val anchorKind = when (kind) {
                    SyncV8.QueueKind.ADD -> if (index != null) SyncV8.Anchor.INDEX else SyncV8.Anchor.LAST
                    SyncV8.QueueKind.MOVE -> SyncV8.Anchor.INDEX
                    else -> null
                }
                // A v7 CLEAR has no snapshot: it clears the first observed server queue (§12).
                val dependsOn = if (kind == SyncV8.QueueKind.REMOVE) episodeId?.let(completions::get) else null
                db.execSQL(
                    "INSERT INTO queue_intents (counter, operation_id, kind, episode_id, anchor_kind, " +
                        "anchor_episode_id, anchor_index, clear_snapshot_json, depends_on_episode_id, " +
                        "depends_on_counter, blocked_reason, account_fp) " +
                        "VALUES (?, ?, ?, ?, ?, NULL, ?, NULL, ?, ?, NULL, NULL)",
                    arrayOf<Any?>(
                        cursor.getLong(4),
                        cursor.getString(0),
                        kind,
                        episodeId,
                        anchorKind,
                        if (anchorKind == SyncV8.Anchor.INDEX) index else null,
                        if (dependsOn != null) episodeId else null,
                        dependsOn,
                    ),
                )
            }
        }
    }

    /** Stable negative IDs for RSS-only rows, in cache-key order (ADR-0012). Returns the next free. */
    private fun assignRssPlaybackIds(db: SupportSQLiteDatabase): Int {
        val keys = db.query(
            "SELECT cache_key FROM podcast_episode_cache WHERE pinepods_episode_id IS NULL ORDER BY cache_key",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        var next = -1
        keys.forEach { key ->
            db.execSQL(
                "UPDATE podcast_episode_cache SET rss_playback_id = ? WHERE cache_key = ?",
                arrayOf<Any>(next, key),
            )
            next -= 1
        }
        return next
    }

    private data class V7ProgressRow(
        val episodeId: Int,
        val positionMs: Long,
        val durationMs: Long?,
        val completed: Boolean,
        val cause: String,
        val sequence: Long,
        val recordedAtEpochMs: Long,
    )

    companion object {
        /** Exactly the SQL Room generates for v8 (schemas/…/8.json). */
        internal val SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `sync_counter` (`singleton_id` INTEGER NOT NULL, `value` INTEGER NOT NULL, `next_rss_playback_id` INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(`singleton_id`))",
            "CREATE TABLE IF NOT EXISTS `account` (`singleton_id` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL, `server_origin` TEXT NOT NULL, `user_id` INTEGER NOT NULL, `username` TEXT NOT NULL, PRIMARY KEY(`singleton_id`))",
            "CREATE TABLE IF NOT EXISTS `server_settings` (`singleton_id` INTEGER NOT NULL, `auto_complete_seconds` INTEGER, `server_version` TEXT, `observed_counter` INTEGER NOT NULL, PRIMARY KEY(`singleton_id`))",
            "CREATE TABLE IF NOT EXISTS `sync_backoff` (`aggregate` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `not_before_elapsed_ms` INTEGER NOT NULL, `boot_count` INTEGER NOT NULL, `last_category` TEXT, `paused_reason` TEXT, PRIMARY KEY(`aggregate`))",
            "CREATE TABLE IF NOT EXISTS `dead_letter` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `aggregate` TEXT NOT NULL, `aggregate_key` TEXT NOT NULL, `intent_counter` INTEGER NOT NULL, `payload_json` TEXT NOT NULL, `category` TEXT NOT NULL, `reason_code` TEXT NOT NULL, `error_excerpt` TEXT, `attempts` INTEGER NOT NULL, `account_fp` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_dead_letter_aggregate_aggregate_key` ON `dead_letter` (`aggregate`, `aggregate_key`)",
            "CREATE TABLE IF NOT EXISTS `sync_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `counter` INTEGER NOT NULL, `elapsed_ms` INTEGER NOT NULL, `wall_ms` INTEGER NOT NULL, `event` TEXT NOT NULL, `detail` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_sync_log_counter` ON `sync_log` (`counter`)",
            "CREATE TABLE IF NOT EXISTS `server_queue` (`episode_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `ticket` INTEGER NOT NULL, PRIMARY KEY(`episode_id`))",
            "CREATE TABLE IF NOT EXISTS `queue_intents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `counter` INTEGER NOT NULL, `operation_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `episode_id` INTEGER, `anchor_kind` TEXT, `anchor_episode_id` INTEGER, `anchor_index` INTEGER, `clear_snapshot_json` TEXT, `depends_on_episode_id` INTEGER, `depends_on_counter` INTEGER, `blocked_reason` TEXT, `account_fp` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_queue_intents_operation_id` ON `queue_intents` (`operation_id`)",
            "CREATE INDEX IF NOT EXISTS `index_queue_intents_counter` ON `queue_intents` (`counter`)",
            "ALTER TABLE playback_progress ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `intent_kind` TEXT NOT NULL DEFAULT 'NONE'",
            "ALTER TABLE playback_progress ADD COLUMN `intent_counter` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `intent_session` TEXT",
            "ALTER TABLE playback_progress ADD COLUMN `rewind_base_counter` INTEGER",
            "ALTER TABLE playback_progress ADD COLUMN `rewind_phase` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `max_sent_position_ms` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `base_server_position_s` INTEGER",
            "ALTER TABLE playback_progress ADD COLUMN `base_ticket` INTEGER",
            "ALTER TABLE playback_progress ADD COLUMN `confirmed_counter` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `server_position_s` INTEGER",
            "ALTER TABLE playback_progress ADD COLUMN `server_completed` INTEGER",
            "ALTER TABLE playback_progress ADD COLUMN `server_ticket` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `migrated` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN `account_fp` TEXT",
            "ALTER TABLE queue_state ADD COLUMN `server_known` INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE podcast_outbox ADD COLUMN `account_fp` TEXT",
            "ALTER TABLE saved_episode_outbox ADD COLUMN `account_fp` TEXT",
            "ALTER TABLE podcast_episode_cache ADD COLUMN `rss_playback_id` INTEGER",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_podcast_episode_cache_rss_playback_id` ON `podcast_episode_cache` (`rss_playback_id`)",
        )
    }
}

internal data class MigrationObservation(val positionSeconds: Long, val completed: Boolean)

/**
 * Kind of a migrated v7 progress intent (SYNC.md §12). In v7 a PERIODIC tick after a SEEK or
 * UNCOMPLETE overwrote the cause, so a not-completed row that is behind what the server last
 * showed is treated as an explicit rewind, never as a passive no-op.
 */
internal fun migratedProgressKind(
    completed: Boolean,
    cause: String,
    positionMs: Long,
    observation: MigrationObservation?,
): String = when {
    completed -> SyncV8.IntentKind.COMPLETE
    cause == "UNCOMPLETE" -> SyncV8.IntentKind.UNCOMPLETE
    observation != null &&
        (observation.completed || observation.positionSeconds * 1_000 > positionMs) ->
        SyncV8.IntentKind.REWIND
    cause == "SEEK" && observation == null -> SyncV8.IntentKind.REWIND
    else -> SyncV8.IntentKind.PASSIVE
}

private fun SupportSQLiteDatabase.singleLongOrNull(sql: String): Long? =
    query(sql).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

private fun Cursor.observation(): MigrationObservation? {
    if (!moveToFirst()) return null
    val seconds = if (isNull(0)) 0L else getDouble(0).toLong()
    return MigrationObservation(seconds, getInt(1) != 0)
}

private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

private fun Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
