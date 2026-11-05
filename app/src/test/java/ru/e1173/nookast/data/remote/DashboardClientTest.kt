package ru.e1173.nookast.data.remote

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DashboardClientTest {
    private val client = DashboardClient()

    @Test
    fun parsesExtendedAnalyticsAndWeeklyOverview() {
        val extended = client.parseExtendedStats(
            """
            {
              "current_streak": 5,
              "completion_rate": 74.4,
              "listening_badge": "Morning listener",
              "total_downloaded_bytes": 1887436800,
              "top_podcasts": [
                {"podcast_id": 7, "podcast_name": "Test show", "artwork_url": "https://img", "total_listen_time": 7200}
              ],
              "favorite_categories": [
                {"category": "Technology", "total_listen_time": 3600}
              ],
              "listening_by_dow": [
                {"day_of_week": "Monday", "listen_time": 1200}
              ],
              "longest_episode": {
                "episode_id": 99, "episode_title": "Long one", "podcast_name": "Test show", "duration": 8280
              }
            }
            """.trimIndent(),
        )
        val overview = client.parseHomeOverview(
            """
            {
              "saved_count": 12,
              "downloaded_count": 4,
              "queue_count": 6,
              "weekly_stats": {"seconds_listened": 24120, "episodes_completed": 8}
            }
            """.trimIndent(),
            extended,
        )

        assertEquals(5, overview.analytics.currentStreakDays)
        assertEquals(74.4, overview.analytics.completionRatePercent, 0.01)
        assertEquals(24120, overview.analytics.weeklyListenSeconds)
        assertEquals(8, overview.analytics.weeklyCompletedEpisodes)
        assertEquals("Test show", overview.analytics.topPodcasts.single().name)
        assertEquals("Technology", overview.analytics.favoriteCategories.single().name)
        assertEquals(99, overview.analytics.longestEpisode?.episodeId)
        assertEquals(12, overview.savedCount)
        assertEquals(4, overview.downloadedCount)
        assertEquals(6, overview.queueCount)
    }

    @Test
    fun parsesListeningDaysReturnedAsObjectMap() {
        val analytics = client.parseExtendedStats(
            """{"listening_by_dow":{"Sunday":300,"Tuesday":{"listen_time":600},"Monday":1200}}}""",
        )

        assertEquals(
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
            analytics.listeningByDay.map { it.label },
        )
        assertEquals(
            listOf(1200L, 600L, 0L, 0L, 0L, 0L, 300L),
            analytics.listeningByDay.map { it.listenSeconds },
        )
    }

    @Test
    fun normalizesLocalizedAndNumericWeekdays() {
        val normalized = normalizeListeningDays(
            listOf(
                DashboardDay("вс", 70),
                DashboardDay("3", 30),
                DashboardDay("Пн.", 10),
            ),
        )

        assertEquals(listOf(10L, 0L, 30L, 0L, 0L, 0L, 70L), normalized.map { it.listenSeconds })
    }

    @Test
    fun cachedSparseWeekdaysAreNormalizedForOfflineDashboard() {
        val cached = DashboardSnapshot(
            analytics = DashboardAnalytics(
                listeningByDay = listOf(
                    DashboardDay("Friday", 500),
                    DashboardDay("Monday", 100),
                ),
            ),
        )

        val restored = dashboardSnapshotFromCacheJson(cached.toCacheJson())

        assertNotNull(restored)
        assertEquals(
            listOf(100L, 0L, 0L, 0L, 500L, 0L, 0L),
            restored?.analytics?.listeningByDay?.map { it.listenSeconds },
        )
    }

    @Test
    fun parsesSmartPlaylistsAndSavedEpisodes() {
        val playlists = client.parsePlaylists(
            """
            {"playlists":[{
              "playlist_id": 3,
              "name": "Commute",
              "description": "Short episodes",
              "icon_name": "car",
              "episode_count": 14,
              "include_unplayed": true,
              "include_partially_played": true,
              "include_played": false,
              "max_duration": 2100
            }]}
            """.trimIndent(),
        )
        val saved = client.parseSavedEpisodes(
            """
            {"saved_episodes":[{
              "episodeid": 42,
              "podcastid": 9,
              "episodetitle": "Saved episode",
              "podcastname": "Test show",
              "episodeartwork": "https://image",
              "episodeurl": "https://audio",
              "episodeduration": 1800,
              "listenduration": 600,
              "completed": false,
              "downloaded": true
            }],"total":1}
            """.trimIndent(),
        )

        assertEquals("Commute", playlists.single().name)
        assertEquals(14, playlists.single().episodeCount)
        assertEquals(2100, playlists.single().maxDurationSeconds)
        assertEquals(42, saved.episodes.single().episodeId)
        assertEquals(600, saved.episodes.single().listenedSeconds)
        assertTrue(saved.episodes.single().downloadedOnServer)
        assertEquals(1, saved.total)
    }

    @Test
    fun dashboardCacheRoundTripPreservesOfflineSnapshot() {
        val snapshot = DashboardSnapshot(
            analytics = DashboardAnalytics(
                weeklyListenSeconds = 1200,
                currentStreakDays = 3,
                topPodcasts = listOf(DashboardTopPodcast(1, "One", "art", 900)),
            ),
            playlists = listOf(
                DashboardPlaylist(2, "Morning", "", "sun", 5, true, false, false, 24, null, 1800),
            ),
            savedEpisodes = listOf(
                DashboardEpisode(3, 1, "Episode", "One", "art", "audio", 100, 20, false, false),
            ),
            savedEpisodeCount = 1,
            serverDownloadedCount = 2,
            queueCount = 4,
            refreshedAtEpochMs = 12345,
        )

        val restored = dashboardSnapshotFromCacheJson(snapshot.toCacheJson())

        assertNotNull(restored)
        assertEquals(snapshot, restored)
    }

    @Test
    fun listeningByDayAsPlainSecondsStartsOnSunday() {
        // PinePods 0.9: "listening_by_dow" is seconds by PostgreSQL day of week, 0 = Sunday.
        val analytics = client.parseExtendedStats("""{"listening_by_dow":[3428,1844,0,0,0,0,0]}""")
        assertEquals(1844L, analytics.listeningByDay[0].listenSeconds) // Monday
        assertEquals(3428L, analytics.listeningByDay[6].listenSeconds) // Sunday
    }

    @Test
    fun recentHistorySkipsImportBurstsAndBucketsLocalDays() {
        val burst = (1..6).joinToString(",") { """{"listendate":"2026-10-24T09:39:17","listenduration":4000,"podcastname":"Old"}""" }
        val body = """{"data":[
            {"listendate":"2026-10-29T05:00:00","listenduration":1800,"podcastname":"A"},
            {"listendate":"2026-10-28T22:30:00","listenduration":600,"podcastname":"B"},
            {"listendate":"2026-10-27T05:10:00","listenduration":1200,"podcastname":"A"},
            $burst
        ],"total":9}"""
        val zone = java.time.ZoneId.of("Europe/Moscow")
        val result = parseRecentHistory(body, DashboardAnalytics(), java.time.LocalDate.of(2026, 10, 29), zone)
        assertEquals(7, result.recentDays.size)
        assertEquals("2026-10-29", result.recentDays[6].label)
        // 22:30 UTC on the 28th is 01:30 on the 29th in Moscow.
        assertEquals(2400L, result.recentDays[6].listenSeconds)
        assertEquals(1200L, result.recentDays[4].listenSeconds)
        assertEquals(0L, result.recentDays[1].listenSeconds) // the import burst on the 24th
        assertEquals("A", result.weekPodcastName)
        assertEquals(3000L, result.weekPodcastSeconds)
        // PinePods listening is everything after the import.
        assertEquals(3600L, result.pinePodsSeconds)
        assertEquals("2026-10-24", result.pinePodsSince)
    }
}
