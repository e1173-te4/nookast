package ru.e1173.nookast.designsystem

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave

/**
 * CSS `box-shadow` of the night effects reference (Stitch export, Fresh Mint dark, 26.10.2026)
 * drawn behind a rounded shape of this draw scope's size.
 *
 * A CSS blur B is a Gaussian with sigma B / 2; Android's blur radius r has sigma ≈ 0.57735 r + 0.5.
 * Like CSS, the shadow is cut out under the shape, so a translucent glass surface does not show its
 * own shadow through. Blur masks need hardware support from API 28; earlier devices draw nothing.
 */
internal fun DrawScope.drawCssShadow(
    color: Color,
    blur: Dp,
    cornerRadius: Float,
    dx: Dp = 0.dp,
    dy: Dp = 0.dp,
    spread: Dp = 0.dp,
) {
    if (color.alpha <= 0.001f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val sigma = blur.toPx() / 2f
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
    val s = spread.toPx()
    val left = dx.toPx() - s
    val top = dy.toPx() - s
    val right = size.width + dx.toPx() + s
    val bottom = size.height + dy.toPx() + s
    val shadowRadius = (cornerRadius + s).coerceAtLeast(0f)
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        val own = android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.width, size.height, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        }
        native.withSave {
            clipOutPath(own)
            drawRoundRect(left, top, right, bottom, shadowRadius, shadowRadius, paint)
        }
    }
}

/** A blurred filled circle (glows of dots, thumbs and markers): CSS blur as in [drawCssShadow]. */
internal fun DrawScope.drawGlowCircle(color: Color, center: Offset, radius: Float, blur: Dp) {
    if (color.alpha <= 0.001f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val sigma = blur.toPx() / 2f
    val blurRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
    }
}

/** A blurred rounded bar (glow of the timeline fill); [corner] defaults to a capsule along the width. */
internal fun DrawScope.drawGlowBar(color: Color, topLeft: Offset, barSize: Size, blur: Dp, corner: Float = barSize.height / 2f) {
    if (color.alpha <= 0.001f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val sigma = blur.toPx() / 2f
    val blurRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(topLeft.x, topLeft.y, topLeft.x + barSize.width, topLeft.y + barSize.height, corner, corner, paint)
    }
}

/**
 * A glow made once and drawn every frame (DEC-061 review: no Paint or mask filter per frame): a
 * blur of CSS size [blurPx] (px, as [drawCssShadow] converts it), filled or, with [strokePx] > 0,
 * stroked with round ends. Blur masks need API 28; earlier devices draw nothing. Only its colour is
 * set per draw; animate alpha or a transform, never the blur.
 */
internal class GlowPaint(blurPx: Float, strokePx: Float = 0f) {
    private val paint: android.graphics.Paint? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            val sigma = blurPx / 2f
            maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
            if (strokePx > 0f) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
        }
    } else {
        null
    }

    private fun ready(color: Color): android.graphics.Paint? {
        if (color.alpha <= 0.001f) return null
        return paint?.also { it.color = color.toArgb() }
    }

    fun roundRect(scope: DrawScope, color: Color, topLeft: Offset, size: Size, corner: Float) {
        val p = ready(color) ?: return
        scope.drawIntoCanvas { it.nativeCanvas.drawRoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, corner, corner, p) }
    }

    fun circle(scope: DrawScope, color: Color, center: Offset, radius: Float) {
        val p = ready(color) ?: return
        scope.drawIntoCanvas { it.nativeCanvas.drawCircle(center.x, center.y, radius, p) }
    }

    fun path(scope: DrawScope, color: Color, path: androidx.compose.ui.graphics.Path) {
        val p = ready(color) ?: return
        scope.drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), p) }
    }
}

/**
 * Glass edge of a dark control (CSS `inset 0 1px 1px rgba(255,255,255,0.1)`): a thin light
 * crescent along the inside of the top edge of a circle.
 */
internal fun DrawScope.drawGlassHighlight(alpha: Float = 0.10f) {
    val r = size.minDimension / 2f
    val w = 1.dp.toPx()
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.White.copy(alpha = alpha).toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w * 2f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(w * 0.8f, BlurMaskFilter.Blur.NORMAL)
        }
        native.withClip(android.graphics.Path().apply { addCircle(center.x, center.y, r, android.graphics.Path.Direction.CW) }) {
            // The light comes from above: the ring shifted down by 1 dp leaves a crescent at the top.
            drawCircle(center.x, center.y + w, r, paint)
        }
    }
}

/**
 * The one shadow of floating surfaces: alerts, popovers, menus, banners (user, 10.11.2026: «an
 * evenly flat shadow» under the source popover; Android's elevation shadow drew a grey rim, and a
 * blur inside a popup window was cut at the window's edge). Two soft layers in the theme's ink, as
 * on iOS: a wide one lowered under the card and a close one for its edge. The surface's window
 * needs [FloatingShadowMargin] of room around the card, or the blur is cut again.
 */
@androidx.compose.runtime.Composable
fun Modifier.floatingShadow(cornerRadius: Dp, strength: Float = 1f): Modifier {
    val colors = LocalNookastColors.current
    val ink = if (colors.isDark) Color.Black else colors.textPrimary
    val wide = (if (colors.isDark) 0.42f else 0.12f) * strength
    val close = (if (colors.isDark) 0.30f else 0.06f) * strength
    // Blur masks need API 28; Android 8 gets the platform shadow (independent review of audit 1–2).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return this.shadow(8.dp * strength, RoundedCornerShape(cornerRadius), ambientColor = ink, spotColor = ink)
    }
    return this.drawBehind {
        val r = cornerRadius.toPx()
        drawCssShadow(ink.copy(alpha = wide), 40.dp, r, dy = 14.dp)
        drawCssShadow(ink.copy(alpha = close), 6.dp, r, dy = 1.dp)
    }
}

/** Room a floating surface's own window keeps around the card for [floatingShadow]. */
object FloatingShadowMargin {
    val Side = 24.dp
    val Top = 12.dp
    val Bottom = 40.dp
}

/** Elevation levels (design audit 2.4). */
enum class NookElevation {
    /** A card: light themes only, ink 5 % blur 16 dy 6 and 4 % blur 3 dy 1; none at night. */
    Level1,

    /** Floating: the mini player, header discs, menus, popovers ([floatingShadow]). */
    Level2,

    /** Modal: the alert. */
    Level3,
}

/** The shadow of [level] around a rounded shape of [cornerRadius]. */
@androidx.compose.runtime.Composable
fun Modifier.nookElevation(level: NookElevation, cornerRadius: Dp): Modifier {
    val colors = LocalNookastColors.current
    return when (level) {
        NookElevation.Level1 -> if (colors.isDark) {
            this
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            this.shadow(2.dp, RoundedCornerShape(cornerRadius), ambientColor = colors.textPrimary, spotColor = colors.textPrimary)
        } else {
            val ink = colors.textPrimary
            // Rendered once per size and shared by every tile of that size: a grid of covers drew 24–40
            // fresh blurs a frame while it scrolled (B3, DEC-071).
            this.drawWithCache {
                val r = cornerRadius.toPx()
                val far = ShadowBitmaps.get(this, size, ink, 16.dp, r, dy = 6.dp)
                val near = ShadowBitmaps.get(this, size, ink, 3.dp, r, dy = 1.dp)
                onDrawBehind {
                    far?.draw(this, 0.05f)
                    near?.draw(this, 0.04f)
                }
            }
        }
        NookElevation.Level2 -> floatingShadow(cornerRadius)
        NookElevation.Level3 -> floatingShadow(cornerRadius, strength = 1.25f)
    }
}

/**
 * Shadows drawn once into a bitmap and reused (B3, DEC-071): the same CSS shadow as [drawCssShadow],
 * rendered at full opacity for a size, radius and colour and drawn with the wanted alpha. Every tile
 * of a grid shares one bitmap; nothing is allocated while a list scrolls. Shapes larger than
 * [MAX_PIXELS] are not cached and draw nothing here (the caller's shapes are tiles and rows).
 */
internal object ShadowBitmaps {
    class Shadow(private val bitmap: androidx.compose.ui.graphics.ImageBitmap, private val origin: Offset) {
        private val paint = androidx.compose.ui.graphics.Paint()

        fun draw(scope: DrawScope, alpha: Float) {
            paint.alpha = alpha
            scope.drawIntoCanvas { it.drawImage(bitmap, origin, paint) }
        }
    }

    private data class Key(val w: Int, val h: Int, val color: Int, val blur: Float, val radius: Float, val dx: Float, val dy: Float)

    private const val MAX_PIXELS = 1_500_000
    private val cache = object : android.util.LruCache<Key, Shadow>(12 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: Shadow): Int = (key.w + 1) * (key.h + 1) * 4
    }

    fun get(density: androidx.compose.ui.unit.Density, size: Size, color: Color, blur: Dp, cornerRadius: Float, dx: Dp = 0.dp, dy: Dp = 0.dp): Shadow? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || size.width < 1f || size.height < 1f) return null
        with(density) {
            val sigma = blur.toPx() / 2f
            val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
            val margin = kotlin.math.ceil(sigma * 3f + 2f)
            val ox = dx.toPx()
            val oy = dy.toPx()
            val left = minOf(0f, ox) - margin
            val top = minOf(0f, oy) - margin
            val w = kotlin.math.ceil(maxOf(size.width, size.width + ox) + margin - left).toInt()
            val h = kotlin.math.ceil(maxOf(size.height, size.height + oy) + margin - top).toInt()
            if (w.toLong() * h > MAX_PIXELS) return null
            val key = Key(size.width.toInt(), size.height.toInt(), color.copy(alpha = 1f).toArgb(), radius, cornerRadius, ox, oy)
            cache.get(key)?.let { return it }
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.translate(-left, -top)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.copy(alpha = 1f).toArgb()
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
            val own = android.graphics.Path().apply {
                addRoundRect(0f, 0f, size.width, size.height, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
            }
            canvas.withSave {
                clipOutPath(own)
                drawRoundRect(ox, oy, size.width + ox, size.height + oy, cornerRadius, cornerRadius, paint)
            }
            return Shadow(bitmap.asImageBitmap(), Offset(left, top)).also { cache.put(key, it) }
        }
    }
}
