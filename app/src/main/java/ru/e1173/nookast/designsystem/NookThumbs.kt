package ru.e1173.nookast.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Timeline thumbs of the master export (Stitch, 26.10.2026: thumb spec with IMAGE_164, IMAGE_5,
 * IMAGE_6, IMAGE_3), transferred from their SVG 1:1 — no redrawing:
 * - Autumn: the 512 px maple PNG (res/drawable-nodpi/thumb_autumn_maple.png), 36 dp;
 * - Lavender: «Prism Gem», 17 dp, drawn by [drawPrismGem];
 * - Winter: dendritic snowflake, 24 dp ([Snowflake], viewBox 100);
 * - Mint: the leaf of the mockups ([StitchLeafNight], [StitchLeafDay], viewBox 24).
 */
object NookThumbs {
    /** The six-ray snowflake of the export; each ray is the SVG group rotated by 60°. */
    val Snowflake: ImageVector by lazy {
        ImageVector.Builder("snowflake", 24.dp, 24.dp, 100f, 100f).apply {
            val blue = Color(0xFF0284C7)
            val ice = Color(0xFFE0F2FE)
            for (step in 0 until 6) {
                val angle = Math.toRadians(step * 60.0)
                fun p(x: Float, y: Float): String {
                    val rx = x * cos(angle) - y * sin(angle)
                    val ry = x * sin(angle) + y * cos(angle)
                    return "${50 + rx} ${50 + ry}"
                }
                fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float) =
                    path("M ${p(x1, y1)} L ${p(x2, y2)}", stroke = color, width = width)
                line(0f, 0f, 0f, -40f, blue, 4.2f)
                line(0f, -2f, 0f, -38f, ice, 1.8f)
                line(0f, -14f, -12f, -24f, blue, 3.2f)
                line(0f, -14f, 12f, -24f, blue, 3.2f)
                line(0f, -14f, -11f, -23f, ice, 1.4f)
                line(0f, -14f, 11f, -23f, ice, 1.4f)
                line(0f, -26f, -9f, -34f, blue, 2.8f)
                line(0f, -26f, 9f, -34f, blue, 2.8f)
                path("M ${p(0f, -45f)} L ${p(4.5f, -40f)} L ${p(0f, -35f)} L ${p(-4.5f, -40f)} Z", fill = SolidColor(blue))
                path("M ${p(0f, -43f)} L ${p(2.5f, -40f)} L ${p(0f, -37f)} L ${p(-2.5f, -40f)} Z", fill = SolidColor(Color.White))
            }
            path(circle(50f, 50f, 4.2f), fill = SolidColor(blue))
            path(circle(50f, 50f, 2.4f), fill = SolidColor(Color.White))
        }.build()
    }

    /**
     * The leaf of the Stitch mockups (np-mint-dark and np-mint-light markup, viewBox 24), as it is
     * there (user, 31.10.2026: the redrawn leaf with veins was not as good): one solid blade, #34D399
     * at night and #059669 by day.
     */
    val StitchLeafNight: ImageVector by lazy { stitchLeaf(Color(0xFF34D399)) }
    val StitchLeafDay: ImageVector by lazy { stitchLeaf(Color(0xFF059669)) }

    private fun stitchLeaf(color: Color): ImageVector =
        ImageVector.Builder("stitch_leaf", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                "M17 8C8 10 5.9 16.17 3.82 21.34L5.71 22l1-2.3A4.49 4.49 0 0 0 8 20C19 20 22 3 22 3c-1 2-8 2.25-13 3.25S2 11.5 2 13.5s1.75 3.75 1.75 3.75C7 8 17 8 17 8z",
                fill = SolidColor(color),
            )
        }.build()

    private fun ImageVector.Builder.path(data: String, fill: Brush? = null, stroke: Color? = null, width: Float = 0f) {
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = fill,
            stroke = stroke?.let(::SolidColor),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M ${cx - r} $cy A $r $r 0 1 0 ${cx + r} $cy A $r $r 0 1 0 ${cx - r} $cy Z"
}

/**
 * «Prism Gem» of the export (viewBox 48, the 8.5-unit rim is 17 dp, so one unit is one dp),
 * centred at [center]: ambient shadows, sapphire core, caustic arch and glint.
 * [glow] scales the lavender ambient shadow (0.32 light, 0.45 dark; +25 % while scrubbing).
 */
internal fun DrawScope.drawPrismGem(center: Offset, unit: Float, glow: Float, stitchRim: Boolean = false) {
    fun at(x: Float, y: Float) = Offset(center.x + (x - 24f) * unit, center.y + (y - 24f) * unit)
    val r = 8.5f * unit
    // feDropShadow dy 3, σ 3.5, #6954C6 and dy 1, σ 1.5, #181428 .18 (σ in units → CSS blur 2σ).
    drawGlowCircle(Color(0xFF6954C6).copy(alpha = (glow * 0.9f).coerceAtMost(1f)), at(24f, 27f), r, (7f * unit / density).dp)
    drawGlowCircle(Color(0xFF181428).copy(alpha = 0.18f * 0.9f), at(24f, 25f), r, (3f * unit / density).dp)
    if (stitchRim) {
        // The export as it is (temporary «Stitch thumb» option): metallic rim, bezel, smaller core.
        drawCircle(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.95f),
                0.45f to Color(0xFFD8CEF8),
                1f to Color(0xFF553EB9),
                start = at(16f, 16f),
                end = at(32f, 32f),
            ),
            radius = r,
            center = at(24f, 24f),
        )
        drawCircle(Color(0xFF5842B8), radius = 7.5f * unit, center = at(24f, 24f))
    }
    // Without the rim and bezel (user, 29.10.2026: the lavender thumb needs no outline) the core
    // fills the gem's size.
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFA594F9),
            0.40f to Color(0xFF7B61FF),
            0.85f to Color(0xFF6954C6),
            1f to Color(0xFF4D38A2),
            center = at(22f, 21f),
            radius = (if (stitchRim) 9f else 10f) * unit,
        ),
        radius = if (stitchRim) 7f * unit else r,
        center = at(24f, 24f),
    )
    val arch = Path().apply {
        moveTo(at(18.5f, 24f).x, at(18.5f, 24f).y)
        cubicTo(at(18.5f, 20.96f).x, at(18.5f, 20.96f).y, at(20.96f, 18.5f).x, at(20.96f, 18.5f).y, at(24f, 18.5f).x, at(24f, 18.5f).y)
        cubicTo(at(27.04f, 18.5f).x, at(27.04f, 18.5f).y, at(29.5f, 20.96f).x, at(29.5f, 20.96f).y, at(29.5f, 24f).x, at(29.5f, 24f).y)
        cubicTo(at(27.8f, 22.2f).x, at(27.8f, 22.2f).y, at(25.8f, 21.6f).x, at(25.8f, 21.6f).y, at(24f, 21.6f).x, at(24f, 21.6f).y)
        cubicTo(at(22.2f, 21.6f).x, at(22.2f, 21.6f).y, at(20.2f, 22.2f).x, at(20.2f, 22.2f).y, at(18.5f, 24f).x, at(18.5f, 24f).y)
        close()
    }
    drawPath(
        arch,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.9f),
            1f to Color.White.copy(alpha = 0f),
            startY = at(24f, 17.5f).y,
            endY = at(24f, 24f).y,
        ),
    )
    drawCircle(Color.White.copy(alpha = 0.95f), radius = 1.3f * unit, center = at(21.5f, 21f))
}

/**
 * The Signature thumb, from the app icon (user, 29.10.2026): a disc of radius [r] at [center] where
 * a peach wave of hair meets a lavender one along an S-curve, lit from the top left, in a soft
 * peach halo ([glow]); no outline.
 */
internal fun DrawScope.drawSignatureWave(center: Offset, r: Float, glow: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFB497).copy(alpha = glow), Color(0xFFC9B2F0).copy(alpha = glow * 0.4f), Color.Transparent),
            center = center + Offset(0f, r * 0.15f),
            radius = r * 1.9f,
        ),
        radius = r * 1.9f,
        center = center + Offset(0f, r * 0.15f),
    )
    // Turned a quarter: lavender on the left and peach on the right, as the icon's two halves
    // (user, 29.10.2026); the glint stays at the top left.
    rotate(-90f, center) {
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        drawPath(
            disc,
            Brush.linearGradient(
                listOf(Color(0xFFB79CEB), Color(0xFF8A6BD1)),
                start = center + Offset(-r, -r),
                end = center + Offset(r, r),
            ),
        )
        // The peach wave: the lower-left half bounded by an S through the centre.
        val wave = Path().apply {
            moveTo(center.x - r * 1.2f, center.y - r * 0.15f)
            cubicTo(
                center.x - r * 0.55f, center.y - r * 0.85f,
                center.x - r * 0.05f, center.y + r * 0.45f,
                center.x + r * 0.45f, center.y - r * 0.05f,
            )
            cubicTo(
                center.x + r * 0.8f, center.y - r * 0.4f,
                center.x + r * 1.05f, center.y - r * 0.1f,
                center.x + r * 1.2f, center.y + r * 0.2f,
            )
            lineTo(center.x + r * 1.2f, center.y + r * 1.2f)
            lineTo(center.x - r * 1.2f, center.y + r * 1.2f)
            close()
        }
        val peach = Path().apply { op(disc, wave, androidx.compose.ui.graphics.PathOperation.Intersect) }
        drawPath(
            peach,
            Brush.linearGradient(
                listOf(Color(0xFFFFD2BC), Color(0xFFF2A07E)),
                start = center + Offset(-r, 0f),
                end = center + Offset(r * 0.6f, r),
            ),
        )
        // A thin darker line where the waves meet, as the icon draws its strands.
        val seam = Path().apply {
            moveTo(center.x - r * 1.2f, center.y - r * 0.15f)
            cubicTo(
                center.x - r * 0.55f, center.y - r * 0.85f,
                center.x - r * 0.05f, center.y + r * 0.45f,
                center.x + r * 0.45f, center.y - r * 0.05f,
            )
            cubicTo(
                center.x + r * 0.8f, center.y - r * 0.4f,
                center.x + r * 1.05f, center.y - r * 0.1f,
                center.x + r * 1.2f, center.y + r * 0.2f,
            )
        }
        clipPath(disc) {
            drawPath(seam, Color(0xFF3B2A5C).copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.12f))
        }
    }
    drawCircle(Color.White.copy(alpha = 0.6f), radius = r * 0.2f, center = center + Offset(-r * 0.38f, -r * 0.42f))
}
