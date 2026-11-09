package ru.e1173.nookast.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.e1173.nookast.R
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.data.remote.FoundPodcast
import ru.e1173.nookast.data.remote.SearchSource
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookIcons

/**
 * Search and add podcasts, opened from the Dashboard (user, 1.11.2026), the iOS way: the search
 * field sits in the sticky glass bar and takes the keyboard at once; results are an inset list with
 * a round «+» that turns to a spinner and then a check. A link to a feed can be pasted as well.
 */
@Composable
internal fun PodcastSearchScreen(
    subscriptions: List<PodcastSubscriptionEntity>,
    largeControls: Boolean,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    onOpenPodcast: (Int) -> Unit,
    viewModel: PodcastSearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalNookastColors.current
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val subscribed = remember(subscriptions) { SubscriptionLookup(subscriptions) }
    val scale = if (largeControls) 4f / 3f else 1f
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val openCard: (FoundPodcast) -> Unit = { podcast ->
        focusManager.clearFocus()
        viewModel.open(podcast)
    }
    Box(Modifier.fillMaxSize()) {
    GlassHeaderScreen(
        headerHeight = 60.dp,
        scrolled = scrolled,
        modifier = Modifier.fillMaxSize(),
        header = { SearchBar(state.query, viewModel::setQuery, onBack) },
    ) { topPadding ->
        LazyColumn(
            flingBehavior = deviceFling(),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 8.dp, bottom = LocalBottomInset.current + 16.dp),
        ) {
            val link = PodcastSearchViewModel.feedLink(state.query)
            when {
                link != null -> item(key = "link") {
                    SearchGroup {
                        val podcast = FoundPodcast(title = link.substringAfter("://").substringBefore('/'), author = link, artworkUrl = "", feedUrl = link)
                        FoundRow(podcast, state, subscribed.find(podcast), scale, onAdd = { viewModel.add(podcast, onAdded) }, onOpenPodcast = onOpenPodcast, onOpenCard = openCard, title = stringResource(R.string.search_add_by_link))
                    }
                }
                state.query.trim().length < 2 -> item(key = "hint") { SearchMessage(stringResource(R.string.search_hint)) }
                state.searching && state.results.isEmpty() -> item(key = "searching") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = colors.accent)
                    }
                }
                state.failed -> item(key = "failed") { SearchMessage(stringResource(R.string.search_failed)) }
                state.results.isEmpty() && state.source != null -> item(key = "empty") { SearchMessage(stringResource(R.string.search_nothing)) }
                else -> {
                    item(key = "results") {
                        SearchGroup {
                            state.results.forEachIndexed { index, podcast ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 84.dp), thickness = 0.5.dp, color = colors.divider)
                                FoundRow(podcast, state, subscribed.find(podcast), scale, onAdd = { viewModel.add(podcast, onAdded) }, onOpenPodcast = onOpenPodcast, onOpenCard = openCard, host = subscribed.twinHost(podcast, state.results), elsewhere = subscribed.sameShowElsewhere(podcast))
                            }
                        }
                    }
                    state.source?.let { source ->
                        item(key = "source") {
                            Text(
                                stringResource(if (source == SearchSource.SERVER) R.string.search_source_server else R.string.search_source_apple),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    // A found podcast opens its card over the results, pushed in from the side as on iOS (user, 2.11.2026).
    androidx.activity.compose.BackHandler(enabled = state.opened != null) { viewModel.close() }
    androidx.compose.animation.AnimatedContent(
        targetState = state.opened,
        transitionSpec = {
            (androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn()) togetherWith
                (androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut())
        },
        modifier = Modifier.fillMaxSize(),
        label = "foundCard",
    ) { opened ->
        opened?.let { podcast ->
            val subscription = subscribed.find(podcast)
            val feed = podcast.feedUrl
            FoundPodcastCard(
                podcast = podcast,
                preview = state.preview,
                previewFailed = state.previewFailed,
                status = when {
                    subscription != null -> FoundPodcastStatus.IN_LIBRARY
                    feed in state.adding -> FoundPodcastStatus.ADDING
                    feed in state.added -> FoundPodcastStatus.ADDED
                    feed in state.addFailed -> FoundPodcastStatus.FAILED
                    else -> FoundPodcastStatus.NEW
                },
                wide = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() } >= 700.dp,
                largeControls = largeControls,
                onBack = viewModel::close,
                onAdd = { viewModel.add(podcast, onAdded) },
                onOpenPodcast = { subscription?.let { onOpenPodcast(it.podcastId) } },
                failure = state.addFailed[feed],
            )
        }
    }
    }
}

/** The search field in the glass bar: Back, then a rounded field with a magnifier and a clear button. */
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onBack: () -> Unit) {
    val colors = LocalNookastColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backLabel = stringResource(R.string.back)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(38.dp).floatingDisc().background(colors.tintContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NookIcons.ChevronLeft, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.tintContainer)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(NookIcons.Search, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                val clearLabel = stringResource(R.string.search_clear)
                Box(
                    Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button) { onQuery("") }.semantics { contentDescription = clearLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(18.dp).background(colors.textSecondary.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(NookIcons.Close, null, tint = colors.background, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroup(content: @Composable () -> Unit) {
    val colors = LocalNookastColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.compositeOver(colors.background)),
    ) { content() }
}

/** One found podcast: cover, title and author; the round button adds it or shows it is in the library.
 * A tap opens its card, or its page once it is in the library. */
@Composable
private fun FoundRow(
    podcast: FoundPodcast,
    state: PodcastSearchUiState,
    subscription: PodcastSubscriptionEntity?,
    scale: Float,
    onAdd: () -> Unit,
    onOpenPodcast: (Int) -> Unit,
    onOpenCard: (FoundPodcast) -> Unit,
    title: String? = null,
    host: String? = null,
    elsewhere: PodcastSubscriptionEntity? = null,
) {
    val colors = LocalNookastColors.current
    val feed = podcast.feedUrl
    val inLibrary = subscription != null || feed in state.added
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { if (subscription != null) onOpenPodcast(subscription.podcastId) else onOpenCard(podcast) }
            .heightIn(min = 72.dp * scale)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(
            url = podcast.artworkUrl,
            title = podcast.title,
            downloaded = false,
            badgeSize = 0.dp,
            cornerRadius = 10.dp,
            decodeSize = 56.dp,
            modifier = Modifier.size(56.dp * scale),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title ?: podcast.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp * scale, lineHeight = 21.sp * scale),
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val secondLine = when {
                feed in state.addFailed -> stringResource(addFailedText(state.addFailed[feed]))
                subscription != null -> stringResource(R.string.search_in_library)
                elsewhere != null -> stringResource(
                    R.string.search_followed_other_feed,
                    podcast.feedUrl.substringAfter("://").substringBefore('/').removePrefix("www."),
                )
                feed in state.added -> stringResource(R.string.search_added)
                else -> podcast.author
            }.let { line -> if (host != null && elsewhere == null) listOf(line, host).filter(String::isNotBlank).joinToString(" · ") else line }
            if (secondLine.isNotBlank()) {
                Text(
                    secondLine,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp * scale),
                    color = if (feed in state.addFailed) MaterialTheme.colorScheme.error else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AddButton(adding = feed in state.adding, added = inLibrary, scale = scale, onClick = onAdd)
    }
}

@Composable
private fun AddButton(adding: Boolean, added: Boolean, scale: Float, onClick: () -> Unit) {
    val colors = LocalNookastColors.current
    val fill by animateColorAsState(if (added) colors.accent else colors.tintContainer, label = "addFill")
    val label = stringResource(if (added) R.string.search_in_library else R.string.search_add)
    Box(
        Modifier
            .size(36.dp * scale)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = !adding && !added, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            adding -> CircularProgressIndicator(Modifier.size(18.dp * scale), strokeWidth = 2.dp, color = colors.accent)
            added -> Icon(NookIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(16.dp * scale))
            else -> Icon(NookIcons.Plus, null, tint = colors.accent, modifier = Modifier.size(16.dp * scale))
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalNookastColors.current.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
    )
}

/**
 * Which subscription a found podcast is (user, 2.11.2026: BeardyBuilding was not marked). The feed
 * link first, compared without scheme, trailing slash or case; then the title, as a host often serves
 * one feed under several links (the server keeps the one it was added by, Podcast Index another).
 * A title match with a different author is not the same podcast.
 */
internal class SubscriptionLookup(subscriptions: List<PodcastSubscriptionEntity>) {
    private val byFeed = subscriptions.associateBy { feedKey(it.feedUrl) }
    private val byTitle = subscriptions.groupBy { textKey(it.name) }

    /** The subscription whose feed [podcast] is: only that one is «in your podcasts» (user, 6.11.2026). */
    fun find(podcast: FoundPodcast): PodcastSubscriptionEntity? = byFeed[feedKey(podcast.feedUrl)]

    /**
     * A subscription to the same show under another feed: the same title, and the same author when
     * both name one (user, 6.11.2026: BeardyBuilding is followed via the s3mts link, the search finds
     * cloud.mave.digital). Null when [podcast] is itself the subscribed feed.
     */
    fun sameShowElsewhere(podcast: FoundPodcast): PodcastSubscriptionEntity? {
        if (find(podcast) != null) return null
        val title = textKey(podcast.title).ifEmpty { return null }
        val author = textKey(podcast.author)
        return byTitle[title]?.firstOrNull { author.isEmpty() || textKey(it.author).let { own -> own.isEmpty() || own == author } }
    }

    /** The feed host of [podcast] when another result has the same title, else null. */
    fun twinHost(podcast: FoundPodcast, among: List<FoundPodcast>): String? {
        val title = textKey(podcast.title)
        val twin = among.any { other -> feedKey(other.feedUrl) != feedKey(podcast.feedUrl) && textKey(other.title) == title }
        return if (twin) podcast.feedUrl.substringAfter("://").substringBefore('/').removePrefix("www.") else null
    }

    private companion object {
        fun feedKey(url: String): String = url.trim().lowercase().substringAfter("://").trimEnd('/')
        fun textKey(text: String): String = text.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ")
    }
}

/** What went wrong with adding, in words (user, 9.11.2026: «the server did not accept the feed» for a fetch failure). */
internal fun addFailedText(failure: ru.e1173.nookast.data.remote.AddFailure?): Int = when (failure) {
    ru.e1173.nookast.data.remote.AddFailure.SERVER_FETCH -> R.string.search_add_failed_server_fetch
    ru.e1173.nookast.data.remote.AddFailure.NETWORK -> R.string.search_add_failed_network
    else -> R.string.search_add_failed
}
