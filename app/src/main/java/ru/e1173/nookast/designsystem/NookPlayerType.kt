package ru.e1173.nookast.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The player's own text roles (design audit 3, guard 2.6; design/tokens/proposal.md §4). The player
 * was approved as it is (DEC-024), so these keep its exact sizes, weights and line heights: each is
 * a partial style merged over the Material style the screen used before, which keeps the result
 * unchanged. The rest of the app uses [NookType].
 */
object NookPlayerType {
    private fun part(size: Float? = null, weight: FontWeight? = null, line: Float? = null, tracking: TextUnit = TextUnit.Unspecified) =
        TextStyle(
            fontSize = size?.sp ?: TextUnit.Unspecified,
            fontWeight = weight,
            lineHeight = line?.sp ?: TextUnit.Unspecified,
            letterSpacing = tracking,
        )

    // Weights alone, over a Material style.
    val regular = part(weight = FontWeight.Normal)
    val medium = part(weight = FontWeight.Medium)
    val semiBold = part(weight = FontWeight.SemiBold)
    val bold = part(weight = FontWeight.Bold)
    val extraBold = part(weight = FontWeight.ExtraBold)

    /** The episode title (npTitle): 750; its size comes from the fitting. */
    val titleWeight = FontWeight(750)

    /** The podcast under the title (npSubtitle). */
    val subtitleLarge = part(22.67f, line = 28f)
    val subtitleStart = part(17f, line = 23f)
    val subtitle = part(14f, FontWeight(550), 21f)

    /** Up Next header. */
    val queueTitle = part(19f, FontWeight.Bold, 19f)
    val queueTitleLarge = part(23f, FontWeight.Bold, 28f)
    val queueCount = part(11f, FontWeight.SemiBold)

    /** Playback / Description tabs. */
    val tabLarge = part(22f)
    val tabWide = part(15.25f)
    val tab = part(13.5f, tracking = (-0.025).em)
    fun tabWeight(selected: Boolean) = if (selected) FontWeight.Bold else FontWeight.Medium

    /** The source popover. */
    val sourceCaption = part(11f, tracking = 0.6.sp)
    val sourceTitle = part(20f, FontWeight.Bold, 24f)
    val sourceBody = part(13f)
    val sourceQuality = part(14f, FontWeight.ExtraBold, tracking = 0.5.sp)
    val sourceMetric = part(17f, FontWeight.Bold)
    val sourceAction = part(15f, FontWeight.SemiBold)

    /** The audio processing panel. */
    val processingTitle = part(22f, FontWeight.ExtraBold, 27f)

    /** The description and chapters. */
    val descriptionLarge = part(18f, line = 25f)
    val descriptionWide = part(16f, line = 22f)
    val description = part(15f, line = 21f)
    val chaptersLarge = part(18f, line = 24f)
    val chapters = part(15f, line = 20f)
    val chapterTime = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)
    fun chapterWeight(current: Boolean) = if (current) FontWeight.SemiBold else FontWeight.Normal

    /** Detail cards of the description tab. */
    val cardTitle = part(18f, FontWeight.Bold, 23f)
    val cardBadge = part(11f, FontWeight.SemiBold)
    val cardBadgeLarge = part(13f, FontWeight.SemiBold)

    /** The metadata line of a queue row in the car profile. */
    val metaLarge = part(16f)

    /** A size chosen by fitting (the title shrinks to fit its lines). */
    fun fitted(size: TextUnit) = TextStyle(fontSize = size)
}
