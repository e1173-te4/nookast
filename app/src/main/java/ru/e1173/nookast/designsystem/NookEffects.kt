package ru.e1173.nookast.designsystem

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** A blurred rounded bar (glow of the timeline fill). */
internal fun DrawScope.drawGlowBar(color: Color, topLeft: Offset, barSize: Size, blur: Dp) {
    if (color.alpha <= 0.001f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val sigma = blur.toPx() / 2f
    val blurRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        val r = barSize.height / 2f
        canvas.nativeCanvas.drawRoundRect(topLeft.x, topLeft.y, topLeft.x + barSize.width, topLeft.y + barSize.height, r, r, paint)
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
