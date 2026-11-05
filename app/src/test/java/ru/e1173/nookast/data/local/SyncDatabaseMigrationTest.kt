package ru.e1173.nookast.data.local

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class SyncDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SyncDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationSixToSevenPreservesExistingDataAndCreatesDeferredIntentTable() {
        val databasePath = RuntimeEnvironment.getApplication()
            .getDatabasePath(TEST_DATABASE)
            .absolutePath
        helper.createDatabase(databasePath, 6).apply {
            execSQL(
                """
                INSERT INTO podcast_episode_cache (
                    cache_key, podcast_id, pinepods_episode_id, guid, source, title,
                    description, published_at, duration_seconds, listened_seconds,
                    completed, podcast_name, artwork_url, media_url, downloaded,
                    refreshed_at_epoch_ms
                ) VALUES (
                    'pinepods:42', 12, 42, 'guid', 'PINEPODS', 'Episode',
                    'Description', '2026-10-18', 120, 0, 0, 'Show', '', '', 0, 100
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databasePath,
            7,
            true,
            SyncDatabase.MIGRATION_6_7,
        ).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM podcast_episode_cache").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='deferred_episode_intents'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "sync-migration-test"
    }
}
