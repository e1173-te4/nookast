package ru.e1173.nookast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.e1173.nookast.R
import ru.e1173.nookast.data.remote.FeedPreview
import ru.e1173.nookast.data.remote.FoundPodcast
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookExpandable
import ru.e1173.nookast.designsystem.NookIcons
import java.util.Locale

/** Where a found podcast stands for the card's capsule. */
internal enum class FoundPodcastStatus { NEW, ADDING, ADDED, FAILED, IN_LIBRARY }

/**
 * The card of a podcast found by search (user, 2.11.2026: a tap on a result should open details as
 * a tap on an episode does). It is the episode card's layout: the cover over its light, the caption,
 * the title and author, one capsule (Subscribe, then Open podcast once it is in the library), the
 * description, «Information» and the latest episodes, all read from the podcast's own feed.
 */
@Composable
internal fun FoundPodcastCard(
    podcast: FoundPodcast,
    preview: FeedPreview?,
    previewFailed: Boolean,
    status: FoundPodcastStatus,
    wide: Boolean,
    largeControls: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenPodcast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNookastColors.current
    val scroll = rememberScrollState()
    val scale = if (largeControls) 4f / 3f else 1f
    val artwork = podcast.artworkUrl.ifBlank { preview?.artworkUrl.orEmpty() }
    val author = podcast.author.ifBlank { preview?.author.orEmpty() }
    val description = preview?.description?.takeIf(String::isNotBlank) ?: podcast.description
    val titlePx = with(LocalDensity.current) { 300.dp.toPx() }
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    val showTitle by remember(titlePx) { derivedStateOf { scroll.value > titlePx } }
    GlassHeaderScreen(
        headerHeight = 52.dp,
        scrolled = scrolled,
        backdrop = { ArtworkTintBackdrop(artwork, Modifier.matchParentSize()) },
        modifier = modifier.fillMaxSize(),
        header = {
            EpisodeCardBar(
                title = podcast.title,
                showTitle = showTitle,
                menu = emptyList(),
                onBack = onBack,
            )
        },
    ) { topPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, flingBehavior = deviceFling())
                .padding(top = topPadding, bottom = 28.dp + LocalBottomInset.current)
                .padding(horizontal = if (largeControls) 22.dp else 20.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val coverSize = when {
                largeControls -> 250.dp
                wide -> 240.dp
                else -> 216.dp
            }
            val coverShape = RoundedCornerShape(14.dp * ARTWORK_CORNER_SCALE)
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .shadow(24.dp, coverShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f))
                    .size(coverSize),
            ) {
                Artwork(
                    url = artwork,
                    title = podcast.title,
                    downloaded = false,
                    badgeSize = 0.dp,
                    cornerRadius = 14.dp,
                    decodeSize = coverSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(22.dp))
            preview?.episodeCount?.takeIf { it > 0 }?.let { count ->
                Text(
                    pluralStringResource(R.plurals.podcast_total_episodes, count, count).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp * scale, letterSpacing = 0.06.em),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                podcast.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp * scale, lineHeight = 30.sp * scale, letterSpacing = (-0.01).em),
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (author.isNotBlank()) {
                Text(
                    author,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp * scale),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            SubscribeCapsule(status, scale, onAdd, onOpenPodcast, Modifier.fillMaxWidth().widthIn(max = 420.dp))
            val note = when (status) {
                FoundPodcastStatus.ADDED -> stringResource(R.string.search_added)
                FoundPodcastStatus.FAILED -> stringResource(R.string.search_add_failed)
                else -> null
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp * scale),
                    color = if (status == FoundPodcastStatus.FAILED) MaterialTheme.colorScheme.error else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp * scale, lineHeight = 24.sp * scale)
            if (remember(description) { normalizeDescriptionText(description).any(Char::isLetterOrDigit) }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                    NookExpandable(
                        collapsedHeight = with(LocalDensity.current) { bodyStyle.lineHeight.toDp() * DESCRIPTION_LINES },
                        moreLabel = stringResource(R.string.description_more),
                        lessLabel = stringResource(R.string.description_less),
                    ) {
                        RichDescriptionText(
                            html = description,
                            fallback = stringResource(R.string.description_unavailable),
                            style = bodyStyle,
                            color = colors.textPrimary.copy(alpha = 0.86f),
                        )
                    }
                }
            }
            when {
                preview != null -> {
                    FeedInformation(preview, author, podcast.feedUrl, scale)
                    if (preview.latest.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        LatestEpisodes(preview, podcast, scale)
                    }
                }
                previewFailed -> {
                    // The address is what one needs most when the feed does not load.
                    FeedInformation(null, author, podcast.feedUrl, scale)
                    Text(
                        stringResource(R.string.search_preview_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
                else -> CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp, color = colors.accent)
            }
        }
    }
}

private const val DESCRIPTION_LINES = 6

/** The card's one capsule, as Play is on the episode card: Subscribe, a spinner, then Open podcast. */
@Composable
private fun SubscribeCapsule(
    status: FoundPodcastStatus,
    scale: Float,
    onAdd: () -> Unit,
    onOpenPodcast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNookastColors.current
    val enabled = status == FoundPodcastStatus.NEW || status == FoundPodcastStatus.FAILED || status == FoundPodcastStatus.IN_LIBRARY
    val label = stringResource(
        when (status) {
            FoundPodcastStatus.NEW, FoundPodcastStatus.FAILED -> R.string.search_preview_subscribe
            FoundPodcastStatus.ADDING -> R.string.search_preview_adding
            FoundPodcastStatus.ADDED -> R.string.search_preview_subscribed
            FoundPodcastStatus.IN_LIBRARY -> R.string.search_preview_open
        },
    )
    Row(
        modifier
            .heightIn(min = 50.dp * scale)
            .clip(CircleShape)
            .background(if (enabled) colors.accent else colors.tintContainer)
            .focusRing(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = if (status == FoundPodcastStatus.IN_LIBRARY) onOpenPodcast else onAdd)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) colors.onAccent else colors.accent
        when (status) {
            FoundPodcastStatus.ADDING -> CircularProgressIndicator(Modifier.size(18.dp * scale), strokeWidth = 2.dp, color = tint)
            FoundPodcastStatus.ADDED -> Icon(NookIcons.Check, null, tint = tint, modifier = Modifier.size(18.dp * scale))
            FoundPodcastStatus.IN_LIBRARY -> Icon(NookIcons.ChevronRight, null, tint = tint, modifier = Modifier.size(18.dp * scale))
            else -> Icon(NookIcons.Plus, null, tint = tint, modifier = Modifier.size(18.dp * scale))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp * scale),
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «Information» of the feed as the episode card's inset group: author, episodes, latest, language. */
@Composable
private fun FeedInformation(preview: FeedPreview?, author: String, feedUrl: String, scale: Float) {
    val colors = LocalNookastColors.current
    val locale = LocalConfiguration.current.locales[0]
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember(feedUrl) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2_000)
            copied = false
        }
    }
    val rows = buildList {
        author.takeIf(String::isNotBlank)?.let { add(stringResource(R.string.search_preview_author) to it) }
        if (preview != null && preview.episodeCount > 0) {
            add(stringResource(R.string.search_preview_episodes) to pluralStringResource(R.plurals.podcast_total_episodes, preview.episodeCount, preview.episodeCount))
        }
        preview?.latest?.firstOrNull()?.publishedAt?.let { add(stringResource(R.string.search_preview_latest) to listDate(it, locale)) }
        preview?.let { languageName(it.language, locale) }?.let { add(stringResource(R.string.search_preview_language) to it) }
        preview?.website?.takeIf(String::isNotBlank)?.let { site ->
            add(stringResource(R.string.search_preview_website) to site.substringAfter("://").removePrefix("www.").trimEnd('/'))
        }
        // The feed address, copied on a tap (user, 6.11.2026).
        feedUrl.takeIf(String::isNotBlank)?.let { feed ->
            add(stringResource(R.string.search_preview_feed) to if (copied) stringResource(R.string.feed_url_copied) else feed.substringAfter("://"))
        }
    }
    if (rows.isEmpty()) return
    CardSection(stringResource(R.string.episode_info_title), scale) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = colors.divider)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp * scale)
                    .then(
                        if (index == rows.lastIndex && feedUrl.isNotBlank()) {
                            Modifier.clickable {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    ?.setPrimaryClip(android.content.ClipData.newPlainText("RSS", feedUrl))
                                copied = true
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp * scale), color = colors.textSecondary)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp * scale),
                    color = if (index == rows.lastIndex && feedUrl.isNotBlank()) colors.accent else colors.textPrimary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The newest episodes of the feed, as «More from the podcast» rows; they open nothing until added. */
@Composable
private fun LatestEpisodes(preview: FeedPreview, podcast: FoundPodcast, scale: Float) {
    val colors = LocalNookastColors.current
    val locale = LocalConfiguration.current.locales[0]
    CardSection(stringResource(R.string.search_preview_recent), scale) {
        preview.latest.forEachIndexed { index, episode ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = colors.divider)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                val meta = listOfNotNull(
                    episode.publishedAt?.let { listDate(it, locale) },
                    episode.durationSeconds?.let { minutesLabel((it / 60).coerceAtLeast(1)) },
                ).joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(meta.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp * scale), color = colors.textSecondary, maxLines = 1)
                }
                Text(
                    cleanEpisodeTitle(episode.title.ifBlank { podcast.title }),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp * scale, lineHeight = 20.sp * scale),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** «ru», «en-us» as the language's own name in the app's language, capitalised. */
private fun languageName(code: String, locale: Locale): String? {
    val tag = code.trim().replace('_', '-').takeIf(String::isNotBlank) ?: return null
    val name = Locale.forLanguageTag(tag).getDisplayLanguage(locale).takeIf(String::isNotBlank) ?: return null
    return name.replaceFirstChar { it.titlecase(locale) }
}
