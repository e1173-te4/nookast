package ru.e1173.nookast.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import ru.e1173.nookast.R
import ru.e1173.nookast.data.local.EpisodeNumberRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A season and episode number; either may be unknown. */
internal data class EpisodeNumber(val season: Int?, val episode: Int?) {
    val isEmpty: Boolean get() = season == null && episode == null
}

/**
 * The numbers the feeds give (`itunes:season`, `itunes:episode`, as Pocket Casts shows them for
 * BeardyCast; user, 29.10.2026), by playback ID: PinePods episode IDs and the negative IDs of
 * RSS-only rows.
 */
internal class EpisodeNumbers(private val byId: Map<Int, EpisodeNumber>) {
    operator fun get(episodeId: Int): EpisodeNumber? = byId[episodeId]

    companion object {
        val Empty = EpisodeNumbers(emptyMap())

        fun from(rows: List<EpisodeNumberRow>): EpisodeNumbers = EpisodeNumbers(
            buildMap {
                rows.forEach { row ->
                    val number = EpisodeNumber(row.seasonNumber, row.episodeNumber)
                    row.pinePodsEpisodeId?.let { put(it, number) }
                    row.rssPlaybackId?.let { put(it, number) }
                }
            },
        )
    }
}

internal val LocalEpisodeNumbers = staticCompositionLocalOf { EpisodeNumbers.Empty }

/** A title with a season-and-episode mark taken out of it, and the numbers the mark held. */
internal data class EpisodeTitleParts(val title: String, val number: EpisodeNumber?)

private val TitleNumberPatterns = listOf(
    // S02E99, S2 E5, S02.E99
    Regex("""(?iu)\bS(\d{1,3})\s*[.\-]?\s*E(\d{1,4})\b"""),
    // Season 2 Episode 5, Season 2, Ep. 5
    Regex("""(?iu)\bSeason\s*(\d{1,3})\s*[,.:\-]?\s*Ep(?:isode|\.)?\s*(\d{1,4})\b"""),
    // Сезон 2, выпуск 5 / Сезон 2 эпизод 5 / Сезон 2 серия 5
    Regex("""(?iu)\bСезон\s*(\d{1,3})\s*[,.:\-]?\s*(?:выпуск|эпизод|серия)\s*(\d{1,4})\b"""),
    // 2 сезон, 5 выпуск
    Regex("""(?iu)\b(\d{1,3})\s*сезон\s*[,.:\-]?\s*(\d{1,4})\s*(?:выпуск|эпизод|серия)\b"""),
)

private const val TITLE_SEPARATORS = " \t-–—|:·•,.;/"

/**
 * Takes a common season-and-episode mark («S02E99», «Season 2 Episode 5», «Сезон 2, выпуск 5»)
 * out of a title, with the separator that tied it to the rest; the numbers go to the meta line.
 */
internal fun episodeTitleParts(title: String): EpisodeTitleParts {
    for (pattern in TitleNumberPatterns) {
        val match = pattern.find(title) ?: continue
        val season = match.groupValues[1].toIntOrNull()
        val episode = match.groupValues[2].toIntOrNull()
        var before = title.substring(0, match.range.first)
        var after = title.substring(match.range.last + 1)
        // Brackets around the mark go with it: «Title (S02E99)».
        if (before.trimEnd().endsWith("(") && after.trimStart().startsWith(")")) {
            before = before.trimEnd().dropLast(1)
            after = after.trimStart().drop(1)
        } else if (before.trimEnd().endsWith("[") && after.trimStart().startsWith("]")) {
            before = before.trimEnd().dropLast(1)
            after = after.trimStart().drop(1)
        }
        val left = before.trimEnd { it in TITLE_SEPARATORS }
        val right = after.trimStart { it in TITLE_SEPARATORS }
        val cleaned = when {
            left.isBlank() -> right
            right.isBlank() -> left
            else -> "$left — $right"
        }.trim()
        if (cleaned.isBlank()) return EpisodeTitleParts(title, EpisodeNumber(season, episode))
        return EpisodeTitleParts(cleaned, EpisodeNumber(season, episode))
    }
    return EpisodeTitleParts(title, null)
}

/** The title as lists and Now Playing show it: without a season-and-episode mark. */
internal fun cleanEpisodeTitle(title: String): String = episodeTitleParts(title).title

/** «S13 E473» (RU «С13 Э473»), «E473» or «S2»; null when nothing is known. */
@Composable
internal fun episodeNumberLabel(number: EpisodeNumber?): String? {
    if (number == null || number.isEmpty) return null
    return when {
        number.season != null && number.episode != null ->
            stringResource(R.string.episode_number_season_episode, number.season, number.episode)
        number.episode != null -> stringResource(R.string.episode_number_episode, number.episode)
        else -> stringResource(R.string.episode_number_season, number.season ?: 0)
    }
}

/**
 * The quiet line under an episode title: podcast name (where the list mixes podcasts), then the
 * season and episode, a bullet of the font, and the date with its year (user, 29.10.2026); today,
 * yesterday and the day before read as words (user, 29.10.2026).
 */
@Composable
internal fun episodeMetaLine(
    episodeId: Int,
    title: String,
    publishedAt: String?,
    podcastName: String? = null,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val number = LocalEpisodeNumbers.current[episodeId]?.takeUnless(EpisodeNumber::isEmpty)
        ?: episodeTitleParts(title).number
    val date = publishedAt?.takeIf(String::isNotBlank)?.let { value ->
        when (episodeDayOffset(value)) {
            0L -> stringResource(R.string.date_today)
            1L -> stringResource(R.string.date_yesterday)
            2L -> stringResource(R.string.date_day_before_yesterday)
            else -> listDate(value, locale)
        }
    }
    val numbered = listOfNotNull(episodeNumberLabel(number), date).joinToString(" • ")
    return listOfNotNull(podcastName?.takeIf(String::isNotBlank), numbered.takeIf(String::isNotBlank))
        .joinToString(" · ")
}

/** Whole days from the publication to today, in the device's zone; null when unreadable. */
internal fun episodeDayOffset(value: String, today: LocalDate = LocalDate.now()): Long? =
    parseEpisodeDate(value)?.let { ChronoUnit.DAYS.between(it, today) }

private fun parseEpisodeDate(value: String): LocalDate? = runCatching {
    LocalDateTime.parse(value.take(19)).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.recoverCatching {
    LocalDate.parse(value.take(10))
}.getOrNull()

/** A list date always with its year and the full month (user, 29.10.2026): «12 октября 2026». */
internal fun listDate(value: String, locale: Locale): String =
    parseEpisodeDate(value)?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        ?: value.substringBefore('T')

/** A length in minutes as lists show it: «45 мин», «1 ч 35 мин» from an hour on (user, 29.10.2026). */
@Composable
internal fun minutesLabel(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.duration_hours, minutes / 60)
    minutes >= 60 -> stringResource(R.string.duration_hours_minutes_words, minutes / 60, minutes % 60)
    else -> stringResource(R.string.duration_minutes, minutes)
}

/** «Осталось 45 мин», «Осталось 1 ч 35 мин». */
@Composable
internal fun minutesLeftLabel(minutes: Int): String = if (minutes >= 60) {
    stringResource(R.string.podcast_hours_minutes_remaining, minutes / 60, minutes % 60)
} else {
    stringResource(R.string.podcast_minutes_remaining, minutes)
}
