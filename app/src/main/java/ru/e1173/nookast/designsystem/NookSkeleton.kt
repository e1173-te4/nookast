package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A placeholder of loading content (design audit 5.4): the track colour with a pass of the surface
 * at 60 % every 1200 ms. Only for a first load with nothing cached: a refresh over content already
 * shown never puts skeletons back (the quiet-refresh rule of 9.11.2026). Still with «remove
 * animations».
 */
@Composable
fun Modifier.nookSkeleton(shape: androidx.compose.ui.graphics.Shape = NookShapes.s): Modifier {
    val colors = LocalNookastColors.current
    val reduced = rememberReducedMotion()
    // Read while drawing, so the pass does not recompose the placeholder.
    val sweepState = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            -1f,
            2f,
            infiniteRepeatable(tween(SKELETON_PASS_MS, easing = LinearEasing)),
            label = "skeletonSweep",
        )
    }
    val highlight = colors.surface.copy(alpha = SKELETON_HIGHLIGHT)
    return clip(shape)
        .background(colors.track)
        .drawWithCache {
            val band = size.width * SKELETON_BAND
            onDrawBehind {
                val sweep = sweepState?.value ?: return@onDrawBehind
                if (sweep < 0f || sweep > 1.5f) return@onDrawBehind
                val x = sweep * size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, 0f),
                    ),
                )
            }
        }
        .clearAndSetSemantics { }
}

/** Placeholder episode rows: a cover, two lines and a short meta line, on the card. */
@Composable
fun NookSkeletonRows(count: Int = 6, modifier: Modifier = Modifier, artwork: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().nookCard().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (artwork) {
                    Box(Modifier.size(52.dp).nookSkeleton(NookShapes.m))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).nookSkeleton())
                    Box(Modifier.fillMaxWidth(0.9f).height(13.dp).nookSkeleton())
                    Box(Modifier.fillMaxWidth(0.6f).height(13.dp).nookSkeleton())
                }
            }
        }
    }
}

/** Placeholder tiles of a grid: a square cover and a title line. */
@Composable
fun NookSkeletonTile(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).nookSkeleton(NookShapes.l))
        Box(Modifier.fillMaxWidth(0.8f).height(13.dp).nookSkeleton())
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).nookSkeleton())
    }
}

/** A placeholder block of a given height (a week card, a playlist tile). */
@Composable
fun NookSkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).nookSkeleton(NookShapes.l))
}

private const val SKELETON_PASS_MS = 1200
private const val SKELETON_HIGHLIGHT = 0.6f
private const val SKELETON_BAND = 0.35f
