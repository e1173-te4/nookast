package ru.e1173.nookast.data.local

import ru.e1173.nookast.core.network.optText
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray
import ru.e1173.nookast.sync.ProgressCause

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    val completed: Boolean,
    val cause: ProgressCause,
    @ColumnInfo(name = "local_sequence")
    val localSequence: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "last_confirmed_sequence")
    val lastConfirmedSequence: Long? = null,
    @ColumnInfo(name = "last_confirmed_at_epoch_ms")
    val lastConfirmedAtEpochMs: Long? = null,
    // --- v8 (docs/SYNC.md §3.2). position_ms/duration_ms/completed are the desired state. ---
    @ColumnInfo(defaultValue = "0")
    val dirty: Boolean = false,
    @ColumnInfo(name = "intent_kind", defaultValue = "'NONE'")
    val intentKind: String = SyncV8.IntentKind.NONE,
    @ColumnInfo(name = "intent_counter", defaultValue = "0")
    val intentCounter: Long = 0,
    @ColumnInfo(name = "intent_session")
    val intentSession: String? = null,
    @ColumnInfo(name = "rewind_base_counter")
    val rewindBaseCounter: Long? = null,
    @ColumnInfo(name = "rewind_phase", defaultValue = "0")
    val rewindPhase: Int = 0,
    @ColumnInfo(name = "max_sent_position_ms", defaultValue = "0")
    val maxSentPositionMs: Long = 0,
    @ColumnInfo(name = "base_server_position_s")
    val baseServerPositionSeconds: Long? = null,
    @ColumnInfo(name = "base_ticket")
    val baseTicket: Long? = null,
    @ColumnInfo(name = "confirmed_counter", defaultValue = "0")
    val confirmedCounter: Long = 0,
    @ColumnInfo(name = "server_position_s")
    val serverPositionSeconds: Long? = null,
    @ColumnInfo(name = "server_completed")
    val serverCompleted: Boolean? = null,
    @ColumnInfo(name = "server_ticket", defaultValue = "0")
    val serverTicket: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val migrated: Boolean = false,
    @ColumnInfo(name = "account_fp")
    val accountFingerprint: String? = null,
) {
    init {
        require(episodeId > 0)
        require(positionMs >= 0)
        require(durationMs == null || durationMs >= 0)
        require(localSequence >= 0)
        require(updatedAtEpochMs >= 0)
    }
}

@Entity(tableName = "progress_outbox")
data class ProgressOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    val completed: Boolean,
    val cause: ProgressCause,
    @ColumnInfo(name = "local_sequence")
    val localSequence: Long,
    @ColumnInfo(name = "recorded_at_epoch_ms")
    val recordedAtEpochMs: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at_epoch_ms")
    val nextAttemptAtEpochMs: Long = 0,
    @ColumnInfo(name = "last_error_category")
    val lastErrorCategory: String? = null,
) {
    init {
        require(episodeId > 0)
        require(positionMs >= 0)
        require(durationMs == null || durationMs >= 0)
        require(localSequence >= 0)
        require(recordedAtEpochMs >= 0)
        require(attemptCount >= 0)
        require(nextAttemptAtEpochMs >= 0)
    }
}

@Entity(
    tableName = "queue_items",
    indices = [Index(value = ["position"])],
)
data class QueueItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    val position: Int,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    init {
        require(episodeId > 0)
        require(position >= 0)
        require(updatedAtEpochMs >= 0)
    }
}

@Entity(tableName = "queue_episode_cache")
data class QueueEpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "episode_title")
    val episodeTitle: String,
    @ColumnInfo(name = "podcast_name")
    val podcastName: String,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String,
    @ColumnInfo(name = "media_url")
    val mediaUrl: String,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    @ColumnInfo(name = "listen_duration_seconds")
    val listenDurationSeconds: Double?,
    val completed: Boolean,
    val description: String,
    @ColumnInfo(name = "published_at")
    val publishedAt: String,
    val downloaded: Boolean,
    @ColumnInfo(name = "refreshed_at_epoch_ms")
    val refreshedAtEpochMs: Long,
) {
    init {
        require(episodeId > 0)
        require(durationSeconds >= 0)
        require(listenDurationSeconds == null || listenDurationSeconds >= 0)
        require(refreshedAtEpochMs >= 0)
    }
}

data class QueueListEntry(
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "queue_position")
    val queuePosition: Int,
    @ColumnInfo(name = "episode_title")
    val episodeTitle: String?,
    @ColumnInfo(name = "podcast_name")
    val podcastName: String?,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String?,
    @ColumnInfo(name = "media_url")
    val mediaUrl: String?,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int?,
    @ColumnInfo(name = "listen_duration_seconds")
    val listenDurationSeconds: Double?,
    val completed: Boolean?,
    val description: String?,
    @ColumnInfo(name = "published_at")
    val publishedAt: String?,
    val downloaded: Boolean?,
    @ColumnInfo(name = "refreshed_at_epoch_ms")
    val refreshedAtEpochMs: Long?,
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "server_fingerprint")
    val serverFingerprint: String,
    @ColumnInfo(name = "server_item_count")
    val serverItemCount: Int,
    @ColumnInfo(name = "fetched_at_epoch_ms")
    val fetchedAtEpochMs: Long,
    /** v8: false until the first server observation after the migration (SYNC.md §12). */
    @ColumnInfo(name = "server_known", defaultValue = "1")
    val serverKnown: Boolean = true,
) {
    init {
        require(singletonId == SINGLETON_ID)
        require(serverItemCount >= 0)
        require(fetchedAtEpochMs >= 0)
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

enum class QueueMutationKind {
    ADD,
    REMOVE,
    MOVE,
    CLEAR,
}

enum class PodcastMutationKind {
    REMOVE,
}

@Entity(tableName = "podcast_subscriptions")
data class PodcastSubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "podcast_id")
    val podcastId: Int,
    val name: String,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String,
    val description: String,
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int?,
    @ColumnInfo(name = "episodes_played")
    val episodesPlayed: Int,
    @ColumnInfo(name = "feed_url")
    val feedUrl: String,
    val author: String,
    @ColumnInfo(name = "latest_episode_id")
    val latestEpisodeId: Int?,
    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: String,
    @ColumnInfo(name = "refreshed_at_epoch_ms")
    val refreshedAtEpochMs: Long,
) {
    init {
        require(podcastId > 0)
        require(episodeCount == null || episodeCount >= 0)
        require(episodesPlayed >= 0)
        require(latestEpisodeId == null || latestEpisodeId > 0)
        require(refreshedAtEpochMs >= 0)
    }
}

@Entity(
    tableName = "podcast_outbox",
    indices = [
        Index(value = ["sequence"]),
        Index(value = ["next_attempt_at_epoch_ms"]),
        Index(value = ["podcast_id"]),
    ],
)
data class PodcastOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    val kind: PodcastMutationKind,
    @ColumnInfo(name = "podcast_id")
    val podcastId: Int,
    val sequence: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at_epoch_ms")
    val nextAttemptAtEpochMs: Long = 0,
    @ColumnInfo(name = "last_error_category")
    val lastErrorCategory: String? = null,
    @ColumnInfo(name = "account_fp")
    val accountFingerprint: String? = null,
) {
    init {
        require(operationId.isNotBlank())
        require(podcastId > 0)
        require(sequence >= 0)
        require(createdAtEpochMs >= 0)
        require(attemptCount >= 0)
        require(nextAttemptAtEpochMs >= 0)
    }
}

@Entity(
    tableName = "saved_episode_outbox",
    indices = [
        Index(value = ["sequence"]),
        Index(value = ["next_attempt_at_epoch_ms"]),
    ],
)
data class SavedEpisodeOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "desired_saved")
    val desiredSaved: Boolean,
    val sequence: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at_epoch_ms")
    val nextAttemptAtEpochMs: Long = 0,
    @ColumnInfo(name = "last_error_category")
    val lastErrorCategory: String? = null,
    @ColumnInfo(name = "account_fp")
    val accountFingerprint: String? = null,
) {
    init {
        require(episodeId > 0)
        require(sequence >= 0)
        require(createdAtEpochMs >= 0)
        require(attemptCount >= 0)
        require(nextAttemptAtEpochMs >= 0)
    }
}

enum class DeferredEpisodeIntentKind {
    PROGRESS,
    QUEUE,
    SAVED,
}

@Entity(
    tableName = "deferred_episode_intents",
    primaryKeys = ["episode_cache_key", "kind"],
    indices = [Index(value = ["sequence"])],
)
data class DeferredEpisodeIntentEntity(
    @ColumnInfo(name = "episode_cache_key")
    val episodeCacheKey: String,
    val kind: DeferredEpisodeIntentKind,
    @ColumnInfo(name = "desired_state")
    val desiredState: Boolean? = null,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    @ColumnInfo(name = "progress_cause")
    val progressCause: ProgressCause? = null,
    @ColumnInfo(name = "destination_index")
    val destinationIndex: Int? = null,
    val sequence: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
) {
    init {
        require(episodeCacheKey.startsWith("rss:"))
        require(sequence >= 0)
        require(createdAtEpochMs >= 0)
        require(positionMs == null || positionMs >= 0)
        require(durationMs == null || durationMs >= 0)
        require(destinationIndex == null || destinationIndex >= 0)
        when (kind) {
            DeferredEpisodeIntentKind.PROGRESS -> {
                require(desiredState == null)
                require(positionMs != null && progressCause != null && destinationIndex == null)
            }
            DeferredEpisodeIntentKind.QUEUE -> {
                require(desiredState != null)
                require(positionMs == null && durationMs == null && progressCause == null)
                require(desiredState || destinationIndex == null)
            }
            DeferredEpisodeIntentKind.SAVED -> {
                require(desiredState != null)
                require(positionMs == null && durationMs == null && progressCause == null)
                require(destinationIndex == null)
            }
        }
    }
}

@Entity(tableName = "dashboard_cache")
data class DashboardCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "refreshed_at_epoch_ms")
    val refreshedAtEpochMs: Long,
) {
    init {
        require(singletonId == SINGLETON_ID)
        require(payloadJson.isNotBlank())
        require(refreshedAtEpochMs >= 0)
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "queue_outbox",
    indices = [
        Index(value = ["sequence"]),
        Index(value = ["next_attempt_at_epoch_ms"]),
    ],
)
data class QueueOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    val kind: QueueMutationKind,
    @ColumnInfo(name = "episode_id")
    val episodeId: Int?,
    @ColumnInfo(name = "destination_index")
    val destinationIndex: Int?,
    val sequence: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at_epoch_ms")
    val nextAttemptAtEpochMs: Long = 0,
    @ColumnInfo(name = "last_error_category")
    val lastErrorCategory: String? = null,
) {
    init {
        require(operationId.isNotBlank())
        require(episodeId == null || episodeId > 0)
        require(destinationIndex == null || destinationIndex >= 0)
        require(sequence >= 0)
        require(createdAtEpochMs >= 0)
        require(attemptCount >= 0)
        require(nextAttemptAtEpochMs >= 0)
        when (kind) {
            QueueMutationKind.ADD -> require(episodeId != null)
            QueueMutationKind.REMOVE -> require(episodeId != null && destinationIndex == null)
            QueueMutationKind.MOVE -> require(episodeId != null && destinationIndex != null)
            QueueMutationKind.CLEAR -> require(episodeId == null && destinationIndex == null)
        }
    }
}

class SyncTypeConverters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToStringList(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { index -> array.optText(index) }
    }

    @TypeConverter
    fun progressCauseToString(value: ProgressCause): String = value.name

    @TypeConverter
    fun stringToProgressCause(value: String): ProgressCause = ProgressCause.valueOf(value)

    @TypeConverter
    fun deferredEpisodeIntentKindToString(value: DeferredEpisodeIntentKind): String = value.name

    @TypeConverter
    fun stringToDeferredEpisodeIntentKind(value: String): DeferredEpisodeIntentKind =
        DeferredEpisodeIntentKind.valueOf(value)

    @TypeConverter
    fun queueMutationKindToString(value: QueueMutationKind): String = value.name

    @TypeConverter
    fun stringToQueueMutationKind(value: String): QueueMutationKind =
        QueueMutationKind.valueOf(value)

    @TypeConverter
    fun podcastMutationKindToString(value: PodcastMutationKind): String = value.name

    @TypeConverter
    fun stringToPodcastMutationKind(value: String): PodcastMutationKind =
        PodcastMutationKind.valueOf(value)
}
