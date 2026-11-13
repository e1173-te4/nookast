package ru.e1173.nookast.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one card surface (audit 3.2): the opaque surface over the background, the
 * [NookastColors.cardOutline] outline, radius l and [NookElevation.Level1]. Selected: the tint
 * container and the accent outline at 60 %. Rows and tiles that lay out their own content use the
 * modifier; plain blocks use [NookCard].
 *
 * [radius] is scaled with the interface by the caller (the car profile).
 */
@Composable
fun Modifier.nookCard(
    selected: Boolean = false,
    radius: Dp = NookShapes.lRadius,
    /** The current episode's Play on this card: it lights the card at night (design audit 4.12). */
    light: NookLightSource? = null,
    lightPlaying: Boolean = false,
): Modifier {
    val colors = LocalNookastColors.current
    val shape = RoundedCornerShape(radius)
    val base = colors.surface.compositeOver(colors.background)
    val fill by animateColorAsState(
        if (selected) colors.tintContainer.compositeOver(base) else base,
        NookMotion.standard(),
        label = "nookCardFill",
    )
    val outline by animateColorAsState(
        if (selected) colors.accent.copy(alpha = 0.6f) else colors.cardOutline,
        NookMotion.standard(),
        label = "nookCardOutline",
    )
    return this
        .nookElevation(NookElevation.Level1, radius)
        .clip(shape)
        .background(fill)
        // Depth at night (design audit 5.1): a neutral top edge, white 6 % fading out by 35 % of the
        // height — not the accent, which belongs to the elements near Play (Н5).
        .then(if (colors.isDark) Modifier.drawWithCache { onDrawBehind { drawRect(NightEdge) } } else Modifier)
        .border(1.dp, outline, shape)
        .then(if (light != null) Modifier.currentEpisodeLight(light, lightPlaying) else Modifier)
}

/** A [nookCard] block with its content in a column. */
@Composable
fun NookCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    radius: Dp = NookShapes.lRadius,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.nookCard(selected, radius).padding(contentPadding), content = content)
}

private val NightEdge = androidx.compose.ui.graphics.Brush.verticalGradient(
    0f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
    0.35f to androidx.compose.ui.graphics.Color.Transparent,
    1f to androidx.compose.ui.graphics.Color.Transparent,
)
