package ru.e1173.nookast.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.e1173.nookast.R

/**
 * Manrope, the typeface of every approved mockup (design/tokens/proposal.md §4). One variable
 * file (OFL, third_party/manrope) serves all weights, including the 550 and 750 of the mockups.
 */
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(400),
    manrope(500),
    manrope(550),
    manrope(600),
    manrope(700),
    manrope(750),
    manrope(800),
)

private fun style(size: Float, weight: Int, lineHeight: Float, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

/**
 * Material type scale with the mockup face and weights. Sizes stay those the screens were laid out
 * with (already close to the mockup roles: titleLarge = npTitle 20/750, titleSmall = rowTitle 14/700);
 * the exact role table of proposal §4 lands with the components (D3) after Q-10.
 */
val NookastTypography = Typography(
    displaySmall = style(30f, 700, 36f, -0.02f),
    headlineLarge = style(26f, 700, 32f, -0.02f),
    headlineMedium = style(23f, 700, 28f, -0.02f),
    headlineSmall = style(21f, 700, 26f, -0.02f),
    titleLarge = style(20f, 750, 25f, -0.02f),
    titleMedium = style(16f, 700, 21f, -0.015f),
    titleSmall = style(14f, 700, 19f, -0.01f),
    bodyLarge = style(15f, 500, 21f),
    bodyMedium = style(13f, 500, 18f),
    bodySmall = style(11f, 500, 16f),
    labelLarge = style(13f, 600, 17f),
    labelMedium = style(11f, 600, 15f),
    labelSmall = style(10f, 600, 13f),
)
