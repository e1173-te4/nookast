package ru.e1173.nookast.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room v8 tables of the synchronization redesign (docs/SYNC.md §3). Kinds are stored as TEXT
 * constants from [SyncV8] so the migration SQL and the code share one spelling.
 */

/** Monotonic counter (SYNC.md §4): every intent, confirmation and observation ticket takes one. */
@Entity(tableName = "sync_counter")
data class SyncCounterEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    val value: Long,
    /** Next RSS-only playback ID, counting down from −1 (ADR-0012). */
    @ColumnInfo(name = "next_rss_playback_id", defaultValue = "-1")
    val nextRssPlaybackId: Int = -1,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** The account the local data belongs to (I8). The API key is never stored here. */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SyncCounterEntity.SINGLETON_ID,
    val fingerprint: String,
    @ColumnInfo(name = "server_origin")
    val serverOrigin: String,
    @ColumnInfo(name = "user_id")
    val userId: Int,
    val username: String,
)

/** Server settings that change synchronization behaviour (SYNC.md §5.7, §8). */
@Entity(tableName = "server_settings")
data class ServerSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SyncCounterEntity.SINGLETON_ID,
    @ColumnInfo(name = "auto_complete_seconds")
    val autoCompleteSeconds: Int?,
    @ColumnInfo(name = "server_version")
    val serverVersion: String?,
    @ColumnInfo(name = "observed_counter")
    val observedCounter: Long,
)

/** Backoff and pause per aggregate (`progress:<id>`, `queue`, `saved:<id>`, `podcast:<id>`). */
@Entity(tableName = "sync_backoff")
data class SyncBackoffEntity(
    @PrimaryKey
    val aggregate: String,
    val attempts: Int,
    @ColumnInfo(name = "not_before_elapsed_ms")
    val notBeforeElapsedMs: Long,
    @ColumnInfo(name = "boot_count")
    val bootCount: Int,
    @ColumnInfo(name = "last_category")
    val lastCategory: String?,
    @ColumnInfo(name = "paused_reason")
    val pausedReason: String?,
)

/** Intents that cannot be delivered automatically; one row per aggregate key (SYNC.md §8.1). */
@Entity(
    tableName = "dead_letter",
    indices = [Index(value = ["aggregate", "aggregate_key"], unique = true)],
)
data class DeadLetterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val aggregate: String,
    @ColumnInfo(name = "aggregate_key")
    val aggregateKey: String,
    @ColumnInfo(name = "intent_counter")
    val intentCounter: Long,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    val category: String,
    @ColumnInfo(name = "reason_code")
    val reasonCode: String,
    @ColumnInfo(name = "error_excerpt")
    val errorExcerpt: String?,
    val attempts: Int,
    @ColumnInfo(name = "account_fp")
    val accountFingerprint: String?,
)

/** Ring of synchronization events for the status screen and export (I12). */
@Entity(tableName = "sync_log", indices = [Index(value = ["counter"])])
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val counter: Long,
    @ColumnInfo(name = "elapsed_ms")
    val elapsedMs: Long,
    @ColumnInfo(name = "wall_ms")
    val wallMs: Long,
    val event: String,
    val detail: String,
)

/** Last accepted server queue, canonical order (SYNC.md §6.2). */
@Entity(tableName = "server_queue")
data class ServerQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    val position: Int,
    val ticket: Long,
)

/** FIFO of queue intents (SYNC.md §3.3, §6). Order: counter, operation_id, id. */
@Entity(
    tableName = "queue_intents",
    indices = [
        Index(value = ["operation_id"], unique = true),
        Index(value = ["counter"]),
    ],
)
data class QueueIntentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val counter: Long,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    val kind: String,
    @ColumnInfo(name = "episode_id")
    val episodeId: Int?,
    @ColumnInfo(name = "anchor_kind")
    val anchorKind: String?,
    @ColumnInfo(name = "anchor_episode_id")
    val anchorEpisodeId: Int?,
    @ColumnInfo(name = "anchor_index")
    val anchorIndex: Int?,
    @ColumnInfo(name = "clear_snapshot_json")
    val clearSnapshotJson: String?,
    @ColumnInfo(name = "depends_on_episode_id")
    val dependsOnEpisodeId: Int?,
    @ColumnInfo(name = "depends_on_counter")
    val dependsOnCounter: Long?,
    @ColumnInfo(name = "blocked_reason")
    val blockedReason: String?,
    @ColumnInfo(name = "account_fp")
    val accountFingerprint: String?,
)

/** TEXT constants shared by entities, the migration and the engine. */
object SyncV8 {
    object IntentKind {
        const val NONE = "NONE"
        const val PASSIVE = "PASSIVE"
        const val REWIND = "REWIND"
        const val COMPLETE = "COMPLETE"
        const val UNCOMPLETE = "UNCOMPLETE"
    }

    object QueueKind {
        const val ADD = "ADD"
        const val REMOVE = "REMOVE"
        const val MOVE = "MOVE"
        const val CLEAR = "CLEAR"
    }

    object Anchor {
        const val FIRST = "FIRST"
        const val LAST = "LAST"
        const val AFTER = "AFTER"
        const val INDEX = "INDEX"
    }
}
