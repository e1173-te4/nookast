package ru.e1173.nookast.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/** A screen of the app (plan 5.1): what the back stack holds. */
internal sealed interface Route {
    data object Home : Route
    data class Playlist(val playlistId: Int) : Route
    data object Podcasts : Route
    data class Podcast(val podcastId: Int) : Route
    data object Feed : Route
    data object Queue : Route
    data object Settings : Route

    /** Search and add podcasts (user, 1.11.2026), opened from the Dashboard. */
    data object Search : Route

    /** The episode card (user, 30.10.2026): opened from any list, over the tab it was opened in. */
    data class Episode(val episodeId: Int) : Route
}

internal fun AppDestination.rootRoute(): Route = when (this) {
    AppDestination.HOME -> Route.Home
    AppDestination.PODCASTS -> Route.Podcasts
    AppDestination.FEED -> Route.Feed
    AppDestination.QUEUE -> Route.Queue
    AppDestination.SETTINGS -> Route.Settings
}

/** The destination a route belongs to, for the navigation bar and the screen frame. */
internal fun Route.destination(tab: AppDestination): AppDestination = when (this) {
    Route.Home, is Route.Playlist, Route.Search -> AppDestination.HOME
    Route.Podcasts, is Route.Podcast -> AppDestination.PODCASTS
    Route.Feed -> AppDestination.FEED
    Route.Queue -> AppDestination.QUEUE
    Route.Settings -> AppDestination.SETTINGS
    is Route.Episode -> tab
}

internal fun Route.encode(): String = when (this) {
    Route.Home -> "home"
    is Route.Playlist -> "playlist:$playlistId"
    Route.Podcasts -> "podcasts"
    is Route.Podcast -> "podcast:$podcastId"
    Route.Feed -> "feed"
    Route.Queue -> "queue"
    Route.Settings -> "settings"
    Route.Search -> "search"
    is Route.Episode -> "episode:$episodeId"
}

internal fun decodeRoute(value: String): Route? {
    val id = value.substringAfter(':', "").toIntOrNull()
    return when (value.substringBefore(':')) {
        "home" -> Route.Home
        "playlist" -> id?.let(Route::Playlist)
        "podcasts" -> Route.Podcasts
        "podcast" -> id?.let(Route::Podcast)
        "feed" -> Route.Feed
        "queue" -> Route.Queue
        "settings" -> Route.Settings
        "search" -> Route.Search
        "episode" -> id?.let(Route::Episode)
        else -> null
    }
}

/**
 * The app's back stacks, one per tab as iOS keeps them (plan 5.1, U-11): a tab remembers where it
 * was, tapping the current tab again returns it to its root, Back pops the current tab and from a
 * tab's root goes Home. Saved across configuration changes and process death.
 */
@Stable
internal class AppNavigator(initialTab: AppDestination, initialStacks: Map<AppDestination, List<Route>>) {
    var tab by mutableStateOf(initialTab)
        private set

    /**
     * Grows when the shown tab is tapped again at its root: the screen scrolls to the top, as an
     * iOS tab bar does (user, 30.10.2026: Home on the dashboard).
     */
    var scrollToTop by mutableIntStateOf(0)
        private set

    /** Set by a tab switch so the switch is not animated as a push. */
    var tabSwitch by mutableStateOf(false)
        private set

    private val stacks: Map<AppDestination, SnapshotStateList<Route>> = PrimaryTabs.associateWith { tab ->
        mutableStateListOf<Route>().apply {
            addAll(initialStacks[tab]?.takeIf { it.isNotEmpty() && it.first() == tab.rootRoute() } ?: listOf(tab.rootRoute()))
        }
    }

    fun stack(tab: AppDestination = this.tab): List<Route> = stacks.getValue(tab)

    val top: Route get() = stack().last()

    /** What the screen shows: Settings over a tab is its own destination. */
    val destination: AppDestination get() = top.destination(tab)

    fun push(route: Route) {
        tabSwitch = false
        val current = stacks.getValue(tab)
        if (current.last() != route) current.add(route)
    }

    /** Replaces the screen on top, or pushes when the top is the tab's root. */
    fun replaceTop(route: Route) {
        tabSwitch = false
        val current = stacks.getValue(tab)
        if (current.size > 1) current[current.lastIndex] = route else current.add(route)
    }

    /** Back: pops the current tab, and from the root of another tab goes Home. False at Home's root. */
    fun pop(): Boolean {
        val current = stacks.getValue(tab)
        if (current.size > 1) {
            tabSwitch = false
            current.removeAt(current.lastIndex)
            return true
        }
        if (tab != AppDestination.HOME) {
            selectTab(AppDestination.HOME)
            return true
        }
        return false
    }

    /** Pops while the top matches [predicate]. */
    fun popWhile(predicate: (Route) -> Boolean) {
        val current = stacks.getValue(tab)
        while (current.size > 1 && predicate(current.last())) current.removeAt(current.lastIndex)
    }

    /** A tab tap: switches to it, or returns it to its root when it is already shown. */
    fun selectTab(destination: AppDestination) {
        if (destination !in PrimaryTabs) return
        if (destination == tab) {
            val current = stacks.getValue(tab)
            tabSwitch = false
            if (current.size == 1) scrollToTop++
            while (current.size > 1) current.removeAt(current.lastIndex)
        } else {
            tabSwitch = true
            tab = destination
        }
    }

    /** Switches to [destination] keeping its stack, so a push lands there. */
    fun showTab(destination: AppDestination) {
        if (destination in PrimaryTabs && destination != tab) {
            tabSwitch = true
            tab = destination
        }
    }

    /** Signed out: every tab back to its root and Home shown. */
    fun reset() {
        stacks.forEach { (tab, stack) ->
            stack.clear()
            stack.add(tab.rootRoute())
        }
        tab = AppDestination.HOME
    }

    companion object {
        val PrimaryTabs = listOf(AppDestination.HOME, AppDestination.PODCASTS, AppDestination.FEED, AppDestination.QUEUE)

        val Saver: Saver<AppNavigator, List<String>> = Saver(
            save = { navigator ->
                listOf(navigator.tab.name) + PrimaryTabs.map { tab ->
                    tab.name + "=" + navigator.stack(tab).joinToString("|") { it.encode() }
                }
            },
            restore = { saved ->
                val tab = AppDestination.entries.firstOrNull { it.name == saved.firstOrNull() } ?: AppDestination.HOME
                val stacks = saved.drop(1).associate { line ->
                    val name = line.substringBefore('=')
                    val routes = line.substringAfter('=').split('|').mapNotNull(::decodeRoute)
                    (AppDestination.entries.firstOrNull { it.name == name } ?: AppDestination.HOME) to routes
                }
                AppNavigator(if (tab in PrimaryTabs) tab else AppDestination.HOME, stacks)
            },
        )
    }
}

@Composable
internal fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) {
    AppNavigator(AppDestination.HOME, emptyMap())
}

/** How a route's screen sits in the window: its own scroll or the app's, insets, the chrome's room. */
internal data class RouteFrame(
    /** The screen scrolls itself (lists); otherwise the frame scrolls it. */
    val lazy: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    /** Room the navigation bar and mini player take at the bottom. */
    val bottomInset: Dp,
    /** The screen draws its own glass header up under the status bar. */
    val ownTopBar: Boolean,
)

/**
 * The tab's back stack on screen (plan 5.1): Navigation 3 keeps each entry's saved state, so every
 * screen has its own scroll position, and Back with the predictive gesture pops it. Each tab's
 * entries are kept under the tab's own key, so a tab switch keeps where each tab was.
 */
@Composable
internal fun AppNavHost(
    navigator: AppNavigator,
    hazeState: HazeState,
    frame: (Route) -> RouteFrame,
    /** Routes with the same key are one screen to Navigation: no transition, one saved state. */
    contentKey: (Route) -> String = Route::encode,
    content: @Composable (Route) -> Unit,
) {
    val tabStates = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize()) {
        // The base colour lies under the screens in the same source, so the bars blur it too.
        GlassBase(hazeState)
        Box(Modifier.fillMaxSize().hazeSource(hazeState).glassEdgeFade(top = 0.dp, bottom = GlassEdgeFade)) {
        tabStates.SaveableStateProvider(navigator.tab.name) {
            NavDisplay(
                backStack = navigator.stack(),
                onBack = { navigator.pop() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = { routeTransition(push = true, tabSwitch = navigator.tabSwitch) },
                popTransitionSpec = { routeTransition(push = false, tabSwitch = false) },
                predictivePopTransitionSpec = { routeTransition(push = false, tabSwitch = false) },
                entryProvider = { route ->
                    NavEntry(route, contentKey = contentKey(route)) {
                        // Only the tab's root answers a re-tap with a scroll to the top.
                        val signal = if (route == navigator.tab.rootRoute()) navigator.scrollToTop else 0
                        CompositionLocalProvider(LocalScrollToTop provides signal) {
                            RouteFrameBox(frame(route)) { content(route) }
                        }
                    }
                },
            )
        }
        }
    }
}

private fun routeTransition(push: Boolean, tabSwitch: Boolean): ContentTransform {
    // A tab switch cuts as iOS does; a push slides in from the end and a pop back out.
    if (tabSwitch) return ContentTransform(EnterTransition.None, ExitTransition.None)
    val slide = tween<IntOffset>(ROUTE_ANIMATION_MS)
    val fade = tween<Float>(ROUTE_ANIMATION_MS)
    return if (push) {
        ContentTransform(slideInHorizontally(slide) { it }, slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade))
    } else {
        ContentTransform(slideInHorizontally(slide) { -it / 4 } + fadeIn(fade), slideOutHorizontally(slide) { it })
    }
}

private const val ROUTE_ANIMATION_MS = 280

@Composable
private fun RouteFrameBox(frame: RouteFrame, content: @Composable () -> Unit) {
    val top = LocalTopInset.current
    val scroll = rememberScrollState()
    if (!frame.lazy) ScrollToTopEffect { scroll.animateScrollTo(0) }
    var modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        // Under the status bar glass the content fades into the background (see glassEdgeFade).
        .then(if (frame.ownTopBar) Modifier else Modifier.glassEdgeFade())
        .padding(horizontal = frame.horizontalPadding)
    if (!frame.lazy) {
        // The content scrolls under the status bar, so the top gap is inside the scroll.
        modifier = modifier.verticalScroll(scroll, flingBehavior = deviceFling()).padding(
            top = top + frame.verticalPadding,
            bottom = frame.bottomInset + frame.verticalPadding,
        )
    }
    CompositionLocalProvider(LocalBottomInset provides if (frame.lazy) frame.bottomInset else 0.dp) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** Counts re-taps of the shown tab at its root (see [AppNavigator.scrollToTop]); 0 elsewhere. */
internal val LocalScrollToTop = compositionLocalOf { 0 }

/** Runs [scroll] each time the shown tab is tapped again at its root, not on the first show. */
@Composable
internal fun ScrollToTopEffect(scroll: suspend () -> Unit) {
    val signal = LocalScrollToTop.current
    val first = remember { signal }
    LaunchedEffect(signal) { if (signal != first) scroll() }
}

/** A grid state that scrolls to the top on a re-tap of its tab. */
@Composable
internal fun rememberTopScrollingGrid(): androidx.compose.foundation.lazy.grid.LazyGridState {
    val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ScrollToTopEffect { state.animateScrollToItem(0) }
    return state
}
