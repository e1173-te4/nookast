package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pull to refresh (design audit 4.8): the screen's existing refresh, with the app's indicator — a
 * 36 dp disc at level 2 whose accent arc grows with the pull and turns while refreshing; a tick
 * when the pull passes the point where letting go refreshes. [topInset] keeps the disc below the
 * screen's glass bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NookPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    // Only the user's own pull shows the indicator: a background refresh over a shown list stays
    // quiet (user, 9.11.2026). The pull lasts until that refresh has started and ended.
    var userPulled by remember { mutableStateOf(false) }
    val refreshingNow by rememberUpdatedState(refreshing)
    LaunchedEffect(userPulled) {
        if (!userPulled) return@LaunchedEffect
        val started = withTimeoutOrNull(REFRESH_START_TIMEOUT_MS) { snapshotFlow { refreshingNow }.first { it } }
        if (started != null) snapshotFlow { refreshingNow }.first { !it }
        userPulled = false
    }
    val shownRefreshing = userPulled && refreshing
    val haptics = rememberNookHaptics()
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { armed -> if (armed) haptics.tick() }
    }
    val colors = LocalNookastColors.current
    PullToRefreshBox(
        isRefreshing = shownRefreshing,
        onRefresh = {
            userPulled = true
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            val refreshing = shownRefreshing
            val pull = state.distanceFraction
            // The arc turns only while refreshing, so an idle screen runs no animation.
            val spin = if (refreshing) {
                rememberInfiniteTransition(label = "refreshSpin").animateFloat(
                    0f,
                    360f,
                    infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
                    label = "refreshAngle",
                ).value
            } else {
                0f
            }
            if (refreshing || pull > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset)
                        .graphicsLayer {
                            val shown = if (refreshing) 1f else pull.coerceIn(0f, 1f)
                            translationY = INDICATOR_TRAVEL.toPx() * shown
                            alpha = shown
                        }
                        .size(INDICATOR_SIZE)
                        .nookElevation(NookElevation.Level2, INDICATOR_SIZE)
                        .background(colors.surface, CircleShape)
                        .drawBehind {
                            val stroke = ARC_WIDTH.toPx()
                            val inset = ARC_INSET.toPx() + stroke / 2f
                            val sweep = if (refreshing) ARC_REFRESHING else ARC_FULL * pull.coerceIn(0f, 1f)
                            drawArc(
                                colors.accent,
                                startAngle = if (refreshing) spin - 90f else -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        },
                )
            }
        },
        content = content,
    )
}

private val INDICATOR_SIZE = 36.dp
private val INDICATOR_TRAVEL = 56.dp
private val ARC_WIDTH = 3.dp
private val ARC_INSET = 7.dp
private const val ARC_FULL = 330f
private const val ARC_REFRESHING = 270f
private const val SPIN_MS = 900
private const val REFRESH_START_TIMEOUT_MS = 2_000L
