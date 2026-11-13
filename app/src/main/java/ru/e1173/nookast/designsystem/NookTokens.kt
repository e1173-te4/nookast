package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Design tokens of the design audit (11.11.2026, stage 2). Values are changed here only, with the
 * reason in docs/plan/PROGRESS.md, never at the place of use.
 */

/** Corner radii (audit 2.1): 6, 10, 14, 18, 24 and the capsule. The player's geometry (DEC-024) keeps its own constants. */
object NookShapes {
    val xsRadius: Dp = 6.dp
    val sRadius: Dp = 10.dp
    val mRadius: Dp = 14.dp
    val lRadius: Dp = 18.dp
    val xlRadius: Dp = 24.dp

    val xs = RoundedCornerShape(xsRadius)
    val s = RoundedCornerShape(sRadius)
    val m = RoundedCornerShape(mRadius)
    val l = RoundedCornerShape(lRadius)
    val xl = RoundedCornerShape(xlRadius)
    val full = CircleShape

    /** The 15 dp check box: 3 dp keeps it square (6 dp read as a radio button). */
    val checkbox = RoundedCornerShape(3.dp)

    /** A radius scaled with the interface (the car profile), as `RoundedCornerShape(r * scale)` did. */
    fun scaled(radius: Dp, scale: Float) = RoundedCornerShape(radius * scale)
}

/** Durations and springs (audit 2.2). Cycles (snow, garland, the source pulse) keep their own constants. */
object NookMotion {
    const val FAST_MS = 120
    const val STANDARD_MS = 200
    const val EMPHASIZED_MS = 280
    const val SLOW_MS = 600

    fun <T> fast(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(FAST_MS, delayMs, FastOutSlowInEasing)
    fun <T> standard(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(STANDARD_MS, delayMs, FastOutSlowInEasing)
    fun <T> emphasized(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(EMPHASIZED_MS, delayMs, FastOutSlowInEasing)
    fun <T> slow(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(SLOW_MS, delayMs, FastOutSlowInEasing)

    /** Press feedback and spatial moves: 0.82 / 380, the player's own spring (DEC-024). */
    fun <T> press() = spring<T>(dampingRatio = 0.82f, stiffness = 380f)
    fun <T> spatial() = spring<T>(dampingRatio = 0.82f, stiffness = 380f)

    /** Settling back (the player closing): 0.86 / 420. */
    fun <T> settle() = spring<T>(dampingRatio = 0.86f, stiffness = 420f)

    /** Material's own low-bounce spring where a component needs it. */
    fun <T> soft() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * The system's «remove animations» (audit 2.3): new cycles and entrances are off or instant with it.
 * Read once per composition of the caller.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Text roles (audit 2.5): size / weight / line height. The car profile scales a role as a whole,
 * as textScale does. The player's own roles (design/tokens/proposal.md §4) stay where they are.
 */
object NookType {
    private fun role(size: Float, weight: Int, line: Float) = TextStyle(
        fontFamily = Manrope,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        lineHeight = line.sp,
    )

    val display = role(32f, 700, 38f)
    val heroTitle = role(26f, 700, 30f)
    val sectionTitle = role(20f, 700, 25f)
    val barTitle = role(17f, 600, 22f)
    val rowTitle = role(14.5f, 600, 18f)
    val body = role(15f, 500, 22f)
    val bodySmall = role(13f, 500, 18f)
    val meta = role(11.5f, 500, 14f)
    val label = role(13f, 600, 17f)
    val button = role(14f, 600, 18f)
    val buttonLarge = role(16f, 600, 20f)
    val navLabel = role(11f, 600, 13f)

    /** A state shown by weight as well as colour (independent review of audit 3): selected tabs and chips. */
    fun selectedWeight(on: Boolean) = if (on) FontWeight.Bold else FontWeight.Medium

    /** Started time of an episode, the current chapter. */
    fun markedWeight(on: Boolean) = if (on) FontWeight.SemiBold else FontWeight.Normal

    /** The highlighted bar label of the week chart. */
    fun highlightWeight(on: Boolean) = if (on) FontWeight.Bold else FontWeight.Normal

    /** The weight of emphasis inside running text (time links in a description). */
    val emphasis = FontWeight.SemiBold

    /** A role at the interface's text scale (the car profile). */
    fun TextStyle.scaled(scale: Float): TextStyle =
        if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
}
