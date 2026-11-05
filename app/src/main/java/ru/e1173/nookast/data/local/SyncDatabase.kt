package ru.e1173.nookast.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaybackProgressEntity::class,
        ProgressOutboxEntity::class,
        QueueItemEntity::class,
        QueueEpisodeEntity::class,
        QueueStateEntity::class,
        QueueOutboxEntity::class,
        PodcastSubscriptionEntity::class,
        PodcastOutboxEntity::class,
        SavedEpisodeOutboxEntity::class,
        DeferredEpisodeIntentEntity::class,
        DashboardCacheEntity::class,
        PodcastEpisodeCacheEntity::class,
        PodcastDetailsCacheEntity::class,
        PodcastRefreshStateEntity::class,
        CurrentPlaybackContextEntity::class,
        SyncCounterEntity::class,
        AccountEntity::class,
        ServerSettingsEntity::class,
        SyncBackoffEntity::class,
        DeadLetterEntity::class,
        SyncLogEntity::class,
        ServerQueueEntity::class,
        QueueIntentEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(SyncTypeConverters::class)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    abstract fun queueDao(): QueueDao

    abstract fun podcastDao(): PodcastDao

    abstract fun dashboardDao(): DashboardDao

    abstract fun savedEpisodeDao(): SavedEpisodeDao

    abstract fun deferredEpisodeIntentDao(): DeferredEpisodeIntentDao

    abstract fun offlineCacheDao(): OfflineCacheDao

    abstract fun syncV8Dao(): SyncV8Dao

    companion object {
        const val DATABASE_NAME = "nookast-sync.db"

        @Volatile
        private var instance: SyncDatabase? = null

        /**
         * Room databases are process-scoped resources. Reusing one instance avoids reopening and
         * revalidating the same file from the activity, player service and workers during startup.
         * The application owns this instance for the lifetime of the process; callers must not
         * close it individually.
         */
        fun open(context: Context): SyncDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SyncDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MigrationV7ToV8(legacySequence(context)),
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                )
                .build()
                .also { instance = it }
        }

        /** v7 counter from SharedPreferences; the 7 → 8 migration has no Context of its own. */
        private fun legacySequence(context: Context): Long? = context.applicationContext
            .getSharedPreferences(LEGACY_SEQUENCE_PREFERENCES, Context.MODE_PRIVATE)
            .takeIf { it.contains(LEGACY_SEQUENCE_KEY) }
            ?.getLong(LEGACY_SEQUENCE_KEY, 0L)

        private const val LEGACY_SEQUENCE_PREFERENCES = "sync_sequence"
        private const val LEGACY_SEQUENCE_KEY = "last_sequence"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE queue_episode_cache ADD COLUMN description TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE queue_episode_cache ADD COLUMN published_at TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE queue_episode_cache ADD COLUMN downloaded INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS podcast_subscriptions (
                        podcast_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        artwork_url TEXT NOT NULL,
                        description TEXT NOT NULL,
                        episode_count INTEGER,
                        episodes_played INTEGER NOT NULL,
                        feed_url TEXT NOT NULL,
                        author TEXT NOT NULL,
                        latest_episode_id INTEGER,
                        last_updated_at TEXT NOT NULL,
                        refreshed_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(podcast_id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS podcast_outbox (
                        operation_id TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        podcast_id INTEGER NOT NULL,
                        sequence INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        attempt_count INTEGER NOT NULL,
                        next_attempt_at_epoch_ms INTEGER NOT NULL,
                        last_error_category TEXT,
                        PRIMARY KEY(operation_id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcast_outbox_sequence ON podcast_outbox(sequence)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcast_outbox_next_attempt_at_epoch_ms ON podcast_outbox(next_attempt_at_epoch_ms)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcast_outbox_podcast_id ON podcast_outbox(podcast_id)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dashboard_cache (
                        singleton_id INTEGER NOT NULL,
                        payload_json TEXT NOT NULL,
                        refreshed_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(singleton_id)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_episode_outbox (
                        episode_id INTEGER NOT NULL,
                        desired_saved INTEGER NOT NULL,
                        sequence INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        attempt_count INTEGER NOT NULL,
                        next_attempt_at_epoch_ms INTEGER NOT NULL,
                        last_error_category TEXT,
                        PRIMARY KEY(episode_id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_saved_episode_outbox_sequence ON saved_episode_outbox(sequence)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_saved_episode_outbox_next_attempt_at_epoch_ms ON saved_episode_outbox(next_attempt_at_epoch_ms)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS podcast_episode_cache (
                        cache_key TEXT NOT NULL,
                        podcast_id INTEGER NOT NULL,
                        pinepods_episode_id INTEGER,
                        guid TEXT,
                        source TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        published_at TEXT NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        listened_seconds REAL NOT NULL,
                        completed INTEGER NOT NULL,
                        podcast_name TEXT NOT NULL,
                        artwork_url TEXT NOT NULL,
                        media_url TEXT NOT NULL,
                        downloaded INTEGER NOT NULL,
                        refreshed_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(cache_key)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcast_episode_cache_podcast_id ON podcast_episode_cache(podcast_id)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_podcast_episode_cache_pinepods_episode_id ON podcast_episode_cache(pinepods_episode_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcast_episode_cache_media_url ON podcast_episode_cache(media_url)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS podcast_details_cache (
                        podcast_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        artwork_url TEXT NOT NULL,
                        description TEXT NOT NULL,
                        author TEXT NOT NULL,
                        categories TEXT NOT NULL,
                        episode_count INTEGER,
                        episodes_played INTEGER,
                        refreshed_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(podcast_id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS podcast_refresh_state (
                        source_key TEXT NOT NULL,
                        podcast_id INTEGER,
                        feed_url TEXT NOT NULL,
                        etag TEXT,
                        last_modified TEXT,
                        last_attempt_at_epoch_ms INTEGER NOT NULL,
                        last_success_at_epoch_ms INTEGER,
                        last_error_category TEXT,
                        PRIMARY KEY(source_key)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcast_refresh_state_podcast_id ON podcast_refresh_state(podcast_id)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS current_playback_context (
                        singleton_id INTEGER NOT NULL,
                        episode_cache_key TEXT NOT NULL,
                        pinepods_episode_id INTEGER,
                        podcast_id INTEGER,
                        episode_title TEXT NOT NULL,
                        podcast_name TEXT NOT NULL,
                        artwork_url TEXT NOT NULL,
                        media_url TEXT NOT NULL,
                        description TEXT NOT NULL,
                        published_at TEXT NOT NULL,
                        position_ms INTEGER NOT NULL,
                        duration_ms INTEGER,
                        completed INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(singleton_id)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Season and episode numbers of the feed on cached episodes (user, 29.10.2026). */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_episode_cache ADD COLUMN season_number INTEGER")
                db.execSQL("ALTER TABLE podcast_episode_cache ADD COLUMN episode_number INTEGER")
            }
        }

        /**
         * Feeds cached before v9 answer «not modified» and never hand over their season and episode
         * numbers; forgetting the validators makes the next refresh read each feed once in full.
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE podcast_refresh_state SET etag = NULL, last_modified = NULL, last_success_at_epoch_ms = NULL",
                )
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deferred_episode_intents (
                        episode_cache_key TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        desired_state INTEGER,
                        position_ms INTEGER,
                        duration_ms INTEGER,
                        progress_cause TEXT,
                        destination_index INTEGER,
                        sequence INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        PRIMARY KEY(episode_cache_key, kind)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_deferred_episode_intents_sequence ON deferred_episode_intents(sequence)",
                )
            }
        }
    }
}
