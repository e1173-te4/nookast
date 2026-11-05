package ru.e1173.nookast.data.remote

import ru.e1173.nookast.core.network.optText
import ru.e1173.nookast.core.network.HttpClients
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress

data class DashboardSnapshot(
    val analytics: DashboardAnalytics = DashboardAnalytics(),
    val playlists: List<DashboardPlaylist> = emptyList(),
    val savedEpisodes: List<DashboardEpisode> = emptyList(),
    val savedEpisodeCount: Int = 0,
    val serverDownloadedCount: Int = 0,
    val queueCount: Int = 0,
    val refreshedAtEpochMs: Long = 0,
)

data class DashboardAnalytics(
    val weeklyListenSeconds: Long = 0,
    val weeklyCompletedEpisodes: Int = 0,
    val currentStreakDays: Int = 0,
    val completionRatePercent: Double = 0.0,
    val topPodcasts: List<DashboardTopPodcast> = emptyList(),
    val favoriteCategories: List<DashboardCategory> = emptyList(),
    val listeningByDay: List<DashboardDay> = emptyList(),
    val longestEpisode: DashboardLongestEpisode? = null,
    val listeningBadge: String = "",
    val totalDownloadedBytes: Long = 0,
    val totalDownloadedFormatted: String = "",
    /** The last 7 local days from the server's listening history, oldest first (label = ISO date). */
    val recentDays: List<DashboardDay> = emptyList(),
    val weekPodcastName: String = "",
    val weekPodcastSeconds: Long = 0,
    /** The hour of day this account listens at most often; -1 when the history is too short. */
    val usualListeningHour: Int = -1,
    /**
     * Seconds listened in PinePods itself: the history after the import of older listening (a
     * burst of rows sharing one minute; user, 29.10.2026). -1 when the page read did not reach it.
     */
    val pinePodsSeconds: Long = -1,
    /** The local date PinePods history starts on (the import or the oldest row), ISO; blank if unknown. */
    val pinePodsSince: String = "",
)

data class DashboardTopPodcast(
    val podcastId: Int?,
    val name: String,
    val artworkUrl: String,
    val listenSeconds: Long,
)

data class DashboardCategory(
    val name: String,
    val listenSeconds: Long,
)

data class DashboardDay(
    val label: String,
    val listenSeconds: Long,
)

data class DashboardLongestEpisode(
    val episodeId: Int?,
    val title: String,
    val podcastName: String,
    val durationSeconds: Long,
)

data class DashboardPlaylist(
    val playlistId: Int,
    val name: String,
    val description: String,
    val iconName: String,
    val episodeCount: Int?,
    val includeUnplayed: Boolean,
    val includePartiallyPlayed: Boolean,
    val includePlayed: Boolean,
    val timeFilterHours: Int?,
    val minDurationSeconds: Int?,
    val maxDurationSeconds: Int?,
)

data class DashboardEpisode(
    val episodeId: Int,
    val podcastId: Int?,
    val title: String,
    val podcastName: String,
    val artworkUrl: String,
    val mediaUrl: String,
    val durationSeconds: Int,
    val listenedSeconds: Int,
    val completed: Boolean,
    val downloadedOnServer: Boolean,
    val publishedAt: String = "",
)

class DashboardClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun fetch(
        session: AuthSession,
        cached: DashboardSnapshot?,
        refreshedAtEpochMs: Long,
    ): DashboardSnapshot {
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val failures = mutableListOf<Throwable>()
        var successfulSections = 0

        fun fetchSection(url: HttpUrl): String? = try {
            get(session, url).also { successfulSections++ }
        } catch (error: Exception) {
            failures += error
            null
        }

        val extended = fetchSection(
            endpoint(baseUrl, "/api/data/get_extended_stats", session.userId),
        )
        val overview = fetchSection(
            endpoint(baseUrl, "/api/data/home_overview", session.userId),
        )
        val playlists = fetchSection(
            endpoint(baseUrl, "/api/data/get_playlists", session.userId),
        )
        val saved = fetchSection(
            requireNotNull(baseUrl.resolve("/api/data/saved_episode_list/${session.userId}"))
                .newBuilder()
                .addQueryParameter("limit", SAVED_EPISODE_LIMIT.toString())
                .addQueryParameter("offset", "0")
                .addQueryParameter("sort_by", "savedate")
                .addQueryParameter("sort_order", "desc")
                .build(),
        )

        // Your week reads the account's own history, so every device shows the same days
        // (user, 29.10.2026: no device-only statistics).
        val history = fetchSection(
            requireNotNull(baseUrl.resolve("/api/data/user_history/${session.userId}"))
                .newBuilder()
                .addQueryParameter("limit", HISTORY_LIMIT.toString())
                .addQueryParameter("offset", "0")
                .addQueryParameter("sort_by", "date")
                .addQueryParameter("sort_order", "desc")
                .build(),
        )

        if (successfulSections == 0) {
            throw failures.firstOrNull() ?: QueueLoadException(QueueLoadError.NETWORK)
        }

        var analytics = cached?.analytics ?: DashboardAnalytics()
        extended?.let { analytics = parseExtendedStats(it, analytics) }
        var savedCount = cached?.savedEpisodeCount ?: 0
        var downloadedCount = cached?.serverDownloadedCount ?: 0
        var queueCount = cached?.queueCount ?: 0
        overview?.let {
            val parsed = parseHomeOverview(it, analytics)
            analytics = parsed.analytics
            savedCount = parsed.savedCount
            downloadedCount = parsed.downloadedCount
            queueCount = parsed.queueCount
        }

        history?.let {
            runCatching { parseRecentHistory(it, analytics, java.time.LocalDate.now(), java.time.ZoneId.systemDefault()) }
                .onSuccess { parsed -> analytics = parsed }
        }

        val parsedSaved = saved?.let(::parseSavedEpisodes)
        if (parsedSaved != null) savedCount = parsedSaved.total

        return DashboardSnapshot(
            analytics = analytics,
            playlists = playlists?.let(::parsePlaylists) ?: cached?.playlists.orEmpty(),
            savedEpisodes = parsedSaved?.episodes ?: cached?.savedEpisodes.orEmpty(),
            savedEpisodeCount = savedCount,
            serverDownloadedCount = downloadedCount,
            queueCount = queueCount,
            refreshedAtEpochMs = refreshedAtEpochMs,
        )
    }

    internal fun parseExtendedStats(
        body: String,
        previous: DashboardAnalytics = DashboardAnalytics(),
    ): DashboardAnalytics {
        val root = bodyObject(body)
        return previous.copy(
            currentStreakDays = root.optIntAny(
                "current_streak",
                "current_streak_days",
                fallback = previous.currentStreakDays,
            ).coerceAtLeast(0),
            completionRatePercent = root.optDoubleAny(
                "completion_rate",
                fallback = previous.completionRatePercent,
            ).coerceIn(0.0, 100.0),
            topPodcasts = root.optArray("top_podcasts")
                ?.let(::parseTopPodcasts)
                ?: previous.topPodcasts,
            favoriteCategories = root.optArray("favorite_categories")
                ?.let(::parseCategories)
                ?: previous.favoriteCategories,
            listeningByDay = root.optListeningDays("listening_by_dow", "listening_by_day")
                ?: previous.listeningByDay,
            longestEpisode = root.optObject("longest_episode")
                ?.let(::parseLongestEpisode)
                ?: previous.longestEpisode,
            listeningBadge = root.optStringAny("listening_badge")
                .ifBlank { previous.listeningBadge },
            totalDownloadedBytes = root.optLongAny(
                "total_downloaded_bytes",
                fallback = previous.totalDownloadedBytes,
            ).coerceAtLeast(0),
            totalDownloadedFormatted = root.optStringAny("total_downloaded_formatted")
                .ifBlank { previous.totalDownloadedFormatted },
        )
    }

    internal fun parseHomeOverview(
        body: String,
        previous: DashboardAnalytics = DashboardAnalytics(),
    ): HomeOverviewResult {
        val root = bodyObject(body)
        val weekly = root.optObject("weekly_stats") ?: JSONObject()
        val overviewTop = root.optArray("top_podcasts")?.let(::parseTopPodcasts).orEmpty()
        return HomeOverviewResult(
            analytics = previous.copy(
                weeklyListenSeconds = weekly.optLongAny(
                    "seconds_listened",
                    "listen_time",
                    fallback = previous.weeklyListenSeconds,
                ).coerceAtLeast(0),
                weeklyCompletedEpisodes = weekly.optIntAny(
                    "episodes_completed",
                    "completed_episodes",
                    fallback = previous.weeklyCompletedEpisodes,
                ).coerceAtLeast(0),
                topPodcasts = overviewTop.ifEmpty { previous.topPodcasts },
            ),
            savedCount = root.optIntAny("saved_count", fallback = 0).coerceAtLeast(0),
            downloadedCount = root.optIntAny("downloaded_count", fallback = 0)
                .coerceAtLeast(0),
            queueCount = root.optIntAny("queue_count", fallback = 0).coerceAtLeast(0),
        )
    }

    internal fun parsePlaylists(body: String): List<DashboardPlaylist> {
        val items = bodyArray(body, "playlists", "data")
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optIntAny("playlist_id", "playlistid", "id", fallback = 0)
                if (id <= 0) continue
                add(
                    DashboardPlaylist(
                        playlistId = id,
                        name = item.optStringAny("name", "playlist_name"),
                        description = item.optStringAny("description"),
                        iconName = item.optStringAny("icon_name", "icon"),
                        episodeCount = item.optNullableInt(
                            "episode_count",
                            "episodecount",
                            "count",
                        )?.coerceAtLeast(0),
                        includeUnplayed = item.optBoolean("include_unplayed", true),
                        includePartiallyPlayed = item.optBoolean(
                            "include_partially_played",
                            true,
                        ),
                        includePlayed = item.optBoolean("include_played", false),
                        timeFilterHours = item.optNullableInt("time_filter_hours"),
                        minDurationSeconds = item.optNullableInt("min_duration"),
                        maxDurationSeconds = item.optNullableInt("max_duration"),
                    ),
                )
            }
        }
    }

    internal fun parseSavedEpisodes(body: String): SavedEpisodesResult {
        val root = bodyObject(body, unwrapData = false)
        val items = root.optArray("saved_episodes", "episodes", "data") ?: JSONArray()
        val episodes = buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val episodeId = item.optIntAny("episodeid", "episode_id", fallback = 0)
                if (episodeId <= 0) continue
                add(
                    DashboardEpisode(
                        episodeId = episodeId,
                        podcastId = item.optNullableInt("podcastid", "podcast_id"),
                        title = item.optStringAny("episodetitle", "episode_title", "title"),
                        podcastName = item.optStringAny("podcastname", "podcast_name"),
                        artworkUrl = item.optStringAny(
                            "episodeartwork",
                            "artworkurl",
                            "artwork_url",
                        ),
                        mediaUrl = item.optStringAny("episodeurl", "episode_url", "url"),
                        durationSeconds = item.optIntAny(
                            "episodeduration",
                            "duration",
                            fallback = 0,
                        ).coerceAtLeast(0),
                        listenedSeconds = item.optIntAny(
                            "listenduration",
                            "listen_duration",
                            fallback = 0,
                        ).coerceAtLeast(0),
                        completed = item.optBoolean("completed", false),
                        downloadedOnServer = item.optBoolean("downloaded", false),
                        publishedAt = item.optStringAny("episodepubdate", "episode_pubdate", "pubdate"),
                    ),
                )
            }
        }
        return SavedEpisodesResult(
            episodes = episodes,
            total = root.optIntAny("total", fallback = episodes.size).coerceAtLeast(0),
        )
    }

    private fun get(session: AuthSession, url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", session.apiKey)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            return httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> throw QueueLoadException(QueueLoadError.AUTHENTICATION)
                    in 300..399 -> throw QueueLoadException(QueueLoadError.SERVER)
                }
                if (!response.isSuccessful) throw QueueLoadException(QueueLoadError.SERVER)
                response.body.string()
            }
        } catch (error: QueueLoadException) {
            throw error
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        }
    }

    private fun endpoint(baseUrl: HttpUrl, path: String, userId: Int): HttpUrl =
        requireNotNull(baseUrl.resolve(path))
            .newBuilder()
            .addQueryParameter("user_id", userId.toString())
            .build()

    private fun parseTopPodcasts(items: JSONArray): List<DashboardTopPodcast> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val name = item.optStringAny("podcast_name", "podcastname", "name")
            if (name.isBlank()) continue
            add(
                DashboardTopPodcast(
                    podcastId = item.optNullableInt("podcast_id", "podcastid", "id"),
                    name = name,
                    artworkUrl = item.optStringAny(
                        "artwork_url",
                        "artworkurl",
                        "podcast_artwork",
                    ),
                    listenSeconds = item.optLongAny(
                        "total_listen_time",
                        "listen_time",
                        "seconds_listened",
                        fallback = 0,
                    ).coerceAtLeast(0),
                ),
            )
        }
    }

    private fun parseCategories(items: JSONArray): List<DashboardCategory> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val name = item.optStringAny("category", "name")
            if (name.isBlank()) continue
            add(
                DashboardCategory(
                    name = name,
                    listenSeconds = item.optLongAny(
                        "total_listen_time",
                        "listen_time",
                        "seconds_listened",
                        fallback = 0,
                    ).coerceAtLeast(0),
                ),
            )
        }
    }

    private fun parseDays(items: JSONArray): List<DashboardDay> = normalizeListeningDays(
        buildList {
            for (index in 0 until items.length()) {
                // PinePods 0.9 sends `listening_by_dow` as plain seconds by PostgreSQL day of week
                // (0 = Sunday); reading only objects left every bar empty (user, 27.10.2026).
                val number = items.opt(index) as? Number
                if (number != null) {
                    add(DashboardDay(label = (index % 7).toString(), listenSeconds = number.toLong().coerceAtLeast(0)))
                    continue
                }
                val item = items.optJSONObject(index) ?: continue
                add(
                    DashboardDay(
                        label = item.optStringAny("day_of_week", "day", "label")
                            .ifBlank { (index + 1).toString() },
                        listenSeconds = item.optLongAny(
                            "listen_time",
                            "seconds_listened",
                            "total_listen_time",
                            fallback = 0,
                        ).coerceAtLeast(0),
                    ),
                )
            }
        },
    )

    private fun JSONObject.optListeningDays(vararg names: String): List<DashboardDay>? {
        names.forEach { name ->
            when (val value = opt(name)) {
                is JSONArray -> return parseDays(value)
                is JSONObject -> {
                    return normalizeListeningDays(
                        buildList {
                            value.keys().forEach { label ->
                                val raw = value.opt(label)
                                val seconds = when (raw) {
                                    is Number -> raw.toLong()
                                    is JSONObject -> raw.optLongAny(
                                        "listen_time",
                                        "seconds_listened",
                                        "total_listen_time",
                                        fallback = 0,
                                    )
                                    else -> raw?.toString()?.toLongOrNull() ?: 0
                                }
                                add(DashboardDay(label, seconds.coerceAtLeast(0)))
                            }
                        },
                    )
                }
            }
        }
        return null
    }

    private fun parseLongestEpisode(item: JSONObject): DashboardLongestEpisode? {
        val title = item.optStringAny("episode_title", "episodetitle", "title")
        if (title.isBlank()) return null
        return DashboardLongestEpisode(
            episodeId = item.optNullableInt("episode_id", "episodeid", "id"),
            title = title,
            podcastName = item.optStringAny("podcast_name", "podcastname"),
            durationSeconds = item.optLongAny(
                "duration",
                "episode_duration",
                "episodeduration",
                fallback = 0,
            ).coerceAtLeast(0),
        )
    }

    private companion object {
        const val SAVED_EPISODE_LIMIT = 20
        const val HISTORY_LIMIT = HISTORY_PAGE
    }
}

/**
 * The last week from `user_history` (newest first). PinePods keeps one row per episode with the
 * last listen time and position, so a day's figure is the position reached in episodes last played
 * that day. Rows written in one burst (a history import: 965 episodes at 09:39 on 6.11.2025 made
 * Thursday «1328 h», user 29.10.2026) are not listening and are skipped. Dates are UTC on the server.
 */
internal fun parseRecentHistory(
    body: String,
    previous: DashboardAnalytics,
    today: java.time.LocalDate,
    zone: java.time.ZoneId,
): DashboardAnalytics {
    val root = JSONTokener(body).nextValue()
    val items = when (root) {
        is JSONArray -> root
        is JSONObject -> root.optJSONArray("data") ?: root.optJSONObject("data")?.optJSONArray("episodes")
            ?: root.optJSONArray("episodes") ?: return previous
        else -> return previous
    }
    data class Entry(val raw: String, val at: java.time.LocalDateTime, val seconds: Long, val podcast: String)
    val entries = items.objects().mapNotNull { item ->
        val raw = item.optStringAny("listendate", "listen_date")
        val at = parseServerTime(raw, zone) ?: return@mapNotNull null
        Entry(raw, at, item.optLongAny("listenduration", "listen_duration", fallback = 0).coerceAtLeast(0), item.optStringAny("podcastname", "podcast_name"))
    }
    val bursts = entries.groupingBy { it.raw.take(16) }.eachCount().filterValues { it >= IMPORT_BURST }.keys
    val real = entries.filter { it.raw.take(16) !in bursts }
    val from = today.minusDays(6)
    val week = real.filter { !it.at.toLocalDate().isBefore(from) && !it.at.toLocalDate().isAfter(today) }
    val days = (0..6).map { offset ->
        val date = from.plusDays(offset.toLong())
        DashboardDay(date.toString(), week.filter { it.at.toLocalDate() == date }.sumOf { it.seconds })
    }
    val top = week.filter { it.podcast.isNotBlank() }.groupBy { it.podcast }
        .mapValues { (_, list) -> list.sumOf { it.seconds } }.maxByOrNull { it.value }
    val hours = real.filter { it.at.toLocalDate().isAfter(today.minusDays(90)) }.map { it.at.hour }
    val usual = if (hours.size < 5) -1 else hours.groupingBy { it }.eachCount().maxBy { it.value }.key
    // Everything after the import is PinePods listening; known once the page reaches the import or
    // holds the whole history.
    val reachedStart = bursts.isNotEmpty() || items.length() < HISTORY_PAGE
    val importAt = entries.filter { it.raw.take(16) in bursts }.maxByOrNull { it.at }?.at
    val since = importAt?.toLocalDate() ?: real.minByOrNull { it.at }?.at?.toLocalDate()
    val afterImport = if (importAt == null) real else real.filter { it.at.isAfter(importAt) }
    return previous.copy(
        recentDays = days,
        weekPodcastName = top?.key.orEmpty(),
        weekPodcastSeconds = top?.value ?: 0,
        usualListeningHour = usual,
        pinePodsSeconds = if (reachedStart) afterImport.sumOf { it.seconds } else previous.pinePodsSeconds,
        pinePodsSince = if (reachedStart) since?.toString().orEmpty() else previous.pinePodsSince,
    )
}

private const val IMPORT_BURST = 5
private const val HISTORY_PAGE = 200

private fun parseServerTime(raw: String, zone: java.time.ZoneId): java.time.LocalDateTime? {
    if (raw.isBlank()) return null
    runCatching { return java.time.OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDateTime() }
    return runCatching {
        java.time.LocalDateTime.parse(raw.replace(' ', 'T').take(19))
            .atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime()
    }.getOrNull()
}

data class HomeOverviewResult(
    val analytics: DashboardAnalytics,
    val savedCount: Int,
    val downloadedCount: Int,
    val queueCount: Int,
)

data class SavedEpisodesResult(
    val episodes: List<DashboardEpisode>,
    val total: Int,
)

internal fun DashboardSnapshot.toCacheJson(): String = JSONObject().apply {
    put("refreshed_at", refreshedAtEpochMs)
    put("saved_count", savedEpisodeCount)
    put("downloaded_count", serverDownloadedCount)
    put("queue_count", queueCount)
    put("analytics", analytics.toJson())
    put("playlists", JSONArray().apply { playlists.forEach { put(it.toJson()) } })
    put("saved_episodes", JSONArray().apply { savedEpisodes.forEach { put(it.toJson()) } })
}.toString()

internal fun dashboardSnapshotFromCacheJson(value: String): DashboardSnapshot? = runCatching {
    val root = JSONObject(value)
    DashboardSnapshot(
        analytics = dashboardAnalyticsFromJson(root.optJSONObject("analytics") ?: JSONObject()),
        playlists = root.optJSONArray("playlists").objects().map(::dashboardPlaylistFromJson),
        savedEpisodes = root.optJSONArray("saved_episodes").objects()
            .map(::dashboardEpisodeFromJson),
        savedEpisodeCount = root.optInt("saved_count", 0),
        serverDownloadedCount = root.optInt("downloaded_count", 0),
        queueCount = root.optInt("queue_count", 0),
        refreshedAtEpochMs = root.optLong("refreshed_at", 0),
    )
}.getOrNull()

private fun DashboardAnalytics.toJson(): JSONObject = JSONObject().apply {
    put("weekly_listen_seconds", weeklyListenSeconds)
    put("weekly_completed", weeklyCompletedEpisodes)
    put("streak", currentStreakDays)
    put("completion_rate", completionRatePercent)
    put("badge", listeningBadge)
    put("downloaded_bytes", totalDownloadedBytes)
    put("downloaded_formatted", totalDownloadedFormatted)
    put("top_podcasts", JSONArray().apply { topPodcasts.forEach { put(it.toJson()) } })
    put("categories", JSONArray().apply { favoriteCategories.forEach { put(it.toJson()) } })
    put("days", JSONArray().apply { listeningByDay.forEach { put(it.toJson()) } })
    longestEpisode?.let { put("longest_episode", it.toJson()) }
    put("recent_days", JSONArray().apply { recentDays.forEach { put(it.toJson()) } })
    put("week_podcast", weekPodcastName)
    put("week_podcast_seconds", weekPodcastSeconds)
    put("usual_hour", usualListeningHour)
    put("pinepods_seconds", pinePodsSeconds)
    put("pinepods_since", pinePodsSince)
}

private fun DashboardTopPodcast.toJson(): JSONObject = JSONObject().apply {
    podcastId?.let { put("id", it) }
    put("name", name)
    put("artwork", artworkUrl)
    put("seconds", listenSeconds)
}

private fun DashboardCategory.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("seconds", listenSeconds)

private fun DashboardDay.toJson(): JSONObject = JSONObject()
    .put("label", label)
    .put("seconds", listenSeconds)

private fun DashboardLongestEpisode.toJson(): JSONObject = JSONObject().apply {
    episodeId?.let { put("id", it) }
    put("title", title)
    put("podcast", podcastName)
    put("duration", durationSeconds)
}

private fun DashboardPlaylist.toJson(): JSONObject = JSONObject().apply {
    put("id", playlistId)
    put("name", name)
    put("description", description)
    put("icon", iconName)
    episodeCount?.let { put("count", it) }
    put("unplayed", includeUnplayed)
    put("partial", includePartiallyPlayed)
    put("played", includePlayed)
    timeFilterHours?.let { put("hours", it) }
    minDurationSeconds?.let { put("min_duration", it) }
    maxDurationSeconds?.let { put("max_duration", it) }
}

private fun DashboardEpisode.toJson(): JSONObject = JSONObject().apply {
    put("id", episodeId)
    podcastId?.let { put("podcast_id", it) }
    put("title", title)
    put("podcast", podcastName)
    put("artwork", artworkUrl)
    put("media", mediaUrl)
    put("duration", durationSeconds)
    put("listened", listenedSeconds)
    put("completed", completed)
    put("downloaded", downloadedOnServer)
    put("published", publishedAt)
}

internal fun normalizeListeningDays(days: List<DashboardDay>): List<DashboardDay> {
    if (days.isEmpty()) return emptyList()

    val secondsByDay = LongArray(WEEKDAY_LABELS.size)
    val occupied = BooleanArray(WEEKDAY_LABELS.size)
    val unrecognized = mutableListOf<Long>()
    days.forEach { day ->
        val index = weekdayIndex(day.label)
        if (index == null) {
            unrecognized += day.listenSeconds.coerceAtLeast(0)
        } else {
            secondsByDay[index] += day.listenSeconds.coerceAtLeast(0)
            occupied[index] = true
        }
    }

    var freeIndex = 0
    unrecognized.forEach { seconds ->
        while (freeIndex < occupied.size && occupied[freeIndex]) freeIndex++
        if (freeIndex < occupied.size) {
            secondsByDay[freeIndex] += seconds
            occupied[freeIndex] = true
        }
    }
    return WEEKDAY_LABELS.mapIndexed { index, label ->
        DashboardDay(label = label, listenSeconds = secondsByDay[index])
    }
}

private fun weekdayIndex(rawLabel: String): Int? {
    val label = rawLabel.trim().lowercase()
        .removeSuffix(".")
        .replace('ё', 'е')
    return when (label) {
        "monday", "mon", "понедельник", "пн", "1" -> 0
        "tuesday", "tue", "tues", "вторник", "вт", "2" -> 1
        "wednesday", "wed", "среда", "ср", "3" -> 2
        "thursday", "thu", "thur", "thurs", "четверг", "чт", "4" -> 3
        "friday", "fri", "пятница", "пт", "5" -> 4
        "saturday", "sat", "суббота", "сб", "6" -> 5
        "sunday", "sun", "воскресенье", "вс", "0", "7" -> 6
        else -> null
    }
}

private val WEEKDAY_LABELS = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
)

private fun dashboardAnalyticsFromJson(value: JSONObject) = DashboardAnalytics(
    weeklyListenSeconds = value.optLong("weekly_listen_seconds", 0),
    weeklyCompletedEpisodes = value.optInt("weekly_completed", 0),
    currentStreakDays = value.optInt("streak", 0),
    completionRatePercent = value.optDouble("completion_rate", 0.0),
    topPodcasts = value.optJSONArray("top_podcasts").objects().map { item ->
        DashboardTopPodcast(
            podcastId = item.optNullableInt("id"),
            name = item.optText("name"),
            artworkUrl = item.optText("artwork"),
            listenSeconds = item.optLong("seconds"),
        )
    },
    favoriteCategories = value.optJSONArray("categories").objects().map { item ->
        DashboardCategory(item.optText("name"), item.optLong("seconds"))
    },
    listeningByDay = normalizeListeningDays(
        value.optJSONArray("days").objects().map { item ->
            DashboardDay(item.optText("label"), item.optLong("seconds"))
        },
    ),
    longestEpisode = value.optJSONObject("longest_episode")?.let { item ->
        DashboardLongestEpisode(
            episodeId = item.optNullableInt("id"),
            title = item.optText("title"),
            podcastName = item.optText("podcast"),
            durationSeconds = item.optLong("duration"),
        )
    },
    listeningBadge = value.optText("badge"),
    totalDownloadedBytes = value.optLong("downloaded_bytes", 0),
    totalDownloadedFormatted = value.optText("downloaded_formatted"),
    recentDays = value.optJSONArray("recent_days").objects().map { item ->
        DashboardDay(item.optText("label"), item.optLong("seconds"))
    },
    weekPodcastName = value.optText("week_podcast"),
    weekPodcastSeconds = value.optLong("week_podcast_seconds"),
    usualListeningHour = value.optInt("usual_hour", -1),
    pinePodsSeconds = value.optLong("pinepods_seconds", -1),
    pinePodsSince = value.optText("pinepods_since"),
)

private fun dashboardPlaylistFromJson(value: JSONObject) = DashboardPlaylist(
    playlistId = value.optInt("id"),
    name = value.optText("name"),
    description = value.optText("description"),
    iconName = value.optText("icon"),
    episodeCount = value.optNullableInt("count"),
    includeUnplayed = value.optBoolean("unplayed"),
    includePartiallyPlayed = value.optBoolean("partial"),
    includePlayed = value.optBoolean("played"),
    timeFilterHours = value.optNullableInt("hours"),
    minDurationSeconds = value.optNullableInt("min_duration"),
    maxDurationSeconds = value.optNullableInt("max_duration"),
)

private fun dashboardEpisodeFromJson(value: JSONObject) = DashboardEpisode(
    episodeId = value.optInt("id"),
    podcastId = value.optNullableInt("podcast_id"),
    title = value.optText("title"),
    podcastName = value.optText("podcast"),
    artworkUrl = value.optText("artwork"),
    mediaUrl = value.optText("media"),
    durationSeconds = value.optInt("duration"),
    listenedSeconds = value.optInt("listened"),
    completed = value.optBoolean("completed"),
    downloadedOnServer = value.optBoolean("downloaded"),
    publishedAt = value.optText("published"),
)

private fun bodyObject(body: String, unwrapData: Boolean = true): JSONObject {
    val root = JSONTokener(body).nextValue()
    if (root !is JSONObject) throw QueueLoadException(QueueLoadError.INVALID_RESPONSE)
    if (!unwrapData) return root
    return root.optJSONObject("data") ?: root
}

private fun bodyArray(body: String, vararg keys: String): JSONArray {
    return when (val root = JSONTokener(body).nextValue()) {
        is JSONArray -> root
        is JSONObject -> keys.firstNotNullOfOrNull(root::optJSONArray)
            ?: throw QueueLoadException(QueueLoadError.INVALID_RESPONSE)
        else -> throw QueueLoadException(QueueLoadError.INVALID_RESPONSE)
    }
}

private fun JSONObject.optArray(vararg names: String): JSONArray? =
    names.firstNotNullOfOrNull(::optJSONArray)

private fun JSONObject.optObject(vararg names: String): JSONObject? =
    names.firstNotNullOfOrNull(::optJSONObject)

private fun JSONObject.optStringAny(vararg names: String): String = names.firstNotNullOfOrNull {
    name -> if (has(name) && !isNull(name)) optString(name).takeIf(String::isNotBlank) else null
}.orEmpty()

private fun JSONObject.optIntAny(vararg names: String, fallback: Int): Int =
    names.firstNotNullOfOrNull { name ->
        if (has(name) && !isNull(name)) optInt(name) else null
    } ?: fallback

private fun JSONObject.optLongAny(vararg names: String, fallback: Long): Long =
    names.firstNotNullOfOrNull { name ->
        if (has(name) && !isNull(name)) optLong(name) else null
    } ?: fallback

private fun JSONObject.optDoubleAny(vararg names: String, fallback: Double): Double =
    names.firstNotNullOfOrNull { name ->
        if (has(name) && !isNull(name)) optDouble(name) else null
    } ?: fallback

private fun JSONObject.optNullableInt(vararg names: String): Int? =
    names.firstNotNullOfOrNull { name ->
        if (has(name) && !isNull(name)) optInt(name) else null
    }

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}
