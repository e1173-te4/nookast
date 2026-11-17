package ru.e1173.nookast.designsystem.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.e1173.nookast.designsystem.Manrope
import ru.e1173.nookast.designsystem.NookIcons
import ru.e1173.nookast.designsystem.NookSwitch

/** The card's bottom slot: two tiles, the offer to download to the server, or its progress. */
sealed interface CardSlot {
    data class Tiles(
        val qualityLabel: String,
        val tier: QualityTier?,
        val kbps: String,
        val sizeLabel: String,
        val sizeNote: String,
        /** The merged tile's top line: «128 кбит/с · 34,3 МБ». */
        val merged: String,
    ) : CardSlot

    data class Download(val full: String, val short: String) : CardSlot

    data class Progress(val percent: Int, val text: String) : CardSlot
}

/** A node's last five checks, opened by a tap on the scheme. */
@Immutable
data class CardHistory(val name: String, val unit: String, val values: List<String>, val failed: List<Boolean>)

/** What the card shows; built by the player's UI layer. */
@Immutable
data class SourceCardModel(
    val title: String,
    val subtitle: String,
    val why: String,
    /** Every explanation of this language with whether it has the switch: the line count fits the longest. */
    val whyAll: List<Pair<String, Boolean>>,
    /** «Этот подкаст всегда через сервер»: shown after the host failed again; null hides it. */
    val always: Boolean?,
    val showCheck: Boolean,
    val checking: Boolean,
    val checkLabel: String,
    val scene: SceneModel,
    val labels: SceneLabels,
    val sceneDescription: String,
    val history: CardHistory?,
    val slot: CardSlot,
    val intro: String?,
    val introClose: String,
)

/**
 * The source card (DEC-069): one height in every state at any language and font size. Header 48 dp,
 * the scheme, an explanation of as many lines as the longest state needs, a bottom slot of 48 dp; the
 * text blocks grow with the font. Fitting keeps 1 dp to spare: a text that fits to the last fraction
 * of a pixel still gets an ellipsis from rounding.
 */
@Composable
fun NookSourceCard(
    model: SourceCardModel,
    story: ConnectionStory,
    palette: ConnectionPalette,
    onCheck: () -> Unit,
    onSceneTap: (SceneFocus) -> Unit,
    onAlways: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCloseIntro: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    flash: () -> Float = { 0f },
) {
    val fs = LocalDensity.current.fontScale
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(model, palette, measurer, width, fs, onCheck)
            val skyHeight = sceneHeightFor(width)
            Box(Modifier.fillMaxWidth().height(skyHeight)) {
                NookConnectionScene(
                    model = model.scene,
                    labels = model.labels,
                    story = story,
                    colors = palette.scene,
                    contentDescription = model.sceneDescription,
                    modifier = Modifier.fillMaxSize(),
                    live = live,
                    flash = flash,
                    onTap = onSceneTap,
                )
                if (model.intro != null) IntroHint(model.intro, model.introClose, palette, skyHeight, onCloseIntro, Modifier.align(Alignment.BottomCenter))
            }
            WhyRow(model, palette, measurer, width, fs, onAlways)
            Box(Modifier.fillMaxWidth().height(48.dp * fs)) {
                when (val slot = model.slot) {
                    is CardSlot.Tiles -> Tiles(slot, palette, measurer, width, fs)
                    is CardSlot.Download -> DownloadButton(slot, palette, measurer, width, fs, onDownload)
                    is CardSlot.Progress -> ProgressSlot(slot, palette, fs)
                }
            }
        }
    }
}

private fun Density.textWidth(measurer: TextMeasurer, text: String, style: TextStyle): Dp =
    measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp()

/** [text] cut in the middle until it fits [max]: the start and the end of an address stay. */
internal fun Density.middleEllipsis(measurer: TextMeasurer, text: String, style: TextStyle, max: Dp): String {
    if (textWidth(measurer, text, style) <= max - SLACK) return text
    var cut = 0
    var shown = text
    while (textWidth(measurer, shown, style) > max - SLACK && cut < text.length - 8) {
        cut += 2
        val keep = text.length - cut
        val head = kotlin.math.ceil(keep * .45).toInt()
        shown = text.take(head) + "…" + text.takeLast(keep - head)
    }
    return shown
}

private fun style(size: TextUnit, weight: FontWeight, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Manrope, fontSize = size, fontWeight = weight, lineHeight = lineHeight, fontFeatureSettings = "tnum")

@Composable
private fun CardHeader(model: SourceCardModel, palette: ConnectionPalette, measurer: TextMeasurer, width: Dp, fs: Float, onCheck: () -> Unit) {
    val density = LocalDensity.current
    val textWidth = width - if (model.showCheck) 54.dp else 0.dp
    // The title shrinks from 18 to 13 sp in 0.5 steps; past that it takes an ellipsis.
    val titleSize = remember(model.title, textWidth, fs) {
        with(density) {
            var sz = 18f
            while (sz > 13f && textWidth(measurer, model.title, style(sz.sp, FontWeight.ExtraBold)) > textWidth - SLACK) sz -= .5f
            sz
        }
    }
    val subStyle = style(12.sp, FontWeight.Normal, 18.sp)
    val subtitle = remember(model.subtitle, textWidth, fs) { with(density) { middleEllipsis(measurer, model.subtitle, subStyle, textWidth) } }
    Row(Modifier.fillMaxWidth().height(48.dp * fs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                model.title,
                style = style(titleSize.sp, FontWeight.ExtraBold, (titleSize * 24f / 18f).sp).copy(letterSpacing = (-.01).em),
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = subStyle, color = palette.muted, maxLines = 1, overflow = TextOverflow.Clip)
        }
        if (model.showCheck) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.tile)
                    .clickable(enabled = !model.checking, role = Role.Button, onClick = onCheck)
                    .semantics { contentDescription = model.checkLabel },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(model.checking, label = "cardCheck") { running ->
                    if (running) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = palette.muted, strokeWidth = 2.dp)
                    } else {
                        Icon(NookIcons.Refresh, null, tint = palette.accentText, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WhyRow(model: SourceCardModel, palette: ConnectionPalette, measurer: TextMeasurer, width: Dp, fs: Float, onAlways: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val whyStyle = style(13.sp, FontWeight.Normal, 19.sp)
    // As many lines as the longest explanation needs here, the same for every state.
    val lines = remember(model.whyAll, width, fs) {
        with(density) {
            model.whyAll.maxOfOrNull { (text, switch) ->
                val w = (width - if (switch) SWITCH_ROOM else 0.dp).roundToPx().coerceAtLeast(1)
                measurer.measure(text, whyStyle, constraints = Constraints(maxWidth = w)).lineCount
            }?.coerceAtLeast(2) ?: 2
        }
    }
    Box(Modifier.fillMaxWidth().height(19.dp * fs * lines), contentAlignment = Alignment.CenterStart) {
        val history = model.history
        if (history != null) {
            HistoryRow(history, palette, measurer, width, fs)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(model.why, style = whyStyle, color = palette.ink, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                model.always?.let { on -> NookSwitch(checked = on, onCheckedChange = onAlways) }
            }
        }
    }
}

@Composable
private fun HistoryRow(history: CardHistory, palette: ConnectionPalette, measurer: TextMeasurer, width: Dp, fs: Float) {
    val density = LocalDensity.current
    val head = style(11.5.sp, FontWeight.Bold)
    val unitWidth = with(density) { textWidth(measurer, history.unit, head.copy(fontWeight = FontWeight.SemiBold)) }
    val name = remember(history.name, width, fs) { with(density) { middleEllipsis(measurer, history.name, head, width - unitWidth - 8.dp) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = head, color = palette.muted, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
            Text(history.unit, style = head.copy(fontWeight = FontWeight.SemiBold), color = palette.muted, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            history.values.forEachIndexed { i, value ->
                val failed = history.failed.getOrElse(i) { false }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (failed) palette.badSoft else palette.tile).padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(value, style = head, color = if (failed) palette.badText else palette.ink, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

@Composable
private fun Tiles(slot: CardSlot.Tiles, palette: ConnectionPalette, measurer: TextMeasurer, width: Dp, fs: Float) {
    val density = LocalDensity.current
    val lab = style(11.5.sp, FontWeight.SemiBold)
    val value = style(13.5.sp, FontWeight.Bold)
    val badge = style(9.5.sp, FontWeight.ExtraBold, 15.sp).copy(letterSpacing = .04.em)
    // Each tile as wide as its content; when the two do not fit with room to spare they merge into one.
    val (merged, share) = remember(slot, width, fs) {
        with(density) {
            val badgeW = slot.tier?.let { textWidth(measurer, it.label, badge) + 8.dp * fs + 6.dp } ?: 0.dp
            val first = maxOf(textWidth(measurer, slot.qualityLabel, lab), badgeW + textWidth(measurer, slot.kbps, value)) + 18.dp
            val second = maxOf(textWidth(measurer, slot.sizeLabel, lab), textWidth(measurer, slot.sizeNote, value)) + 18.dp
            // The room left over goes to both tiles in proportion, so each stays at least as wide as its content.
            (first + second + 8.dp > width - SLACK) to (first.value / (first.value + second.value))
        }
    }
    val tile = Modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(palette.tile).padding(horizontal = 9.dp)
    if (merged) {
        Column(tile.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slot.tier?.let { QualityBadge(it, palette, badge, fs) }
                Text(slot.merged, style = lab, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(slot.sizeNote, style = value, color = palette.coverText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(tile.weight(share), verticalArrangement = Arrangement.Center) {
                Text(slot.qualityLabel, style = lab, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slot.tier?.let { QualityBadge(it, palette, badge, fs) }
                    Text(slot.kbps, style = value, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(tile.weight(1f - share), verticalArrangement = Arrangement.Center) {
                Text(slot.sizeLabel, style = lab, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(slot.sizeNote, style = value, color = palette.coverText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DownloadButton(slot: CardSlot.Download, palette: ConnectionPalette, measurer: TextMeasurer, width: Dp, fs: Float, onDownload: () -> Unit) {
    val density = LocalDensity.current
    // The full label, then the short one, then the font down to 80 %.
    val (label, k) = remember(slot, width, fs) {
        with(density) {
            val room = width - 28.dp
            fun need(text: String, s: Float) = 18.dp + 8.dp + textWidth(measurer, text, style((14.5f * s).sp, FontWeight.Bold))
            val text = if (need(slot.full, 1f) <= room) slot.full else slot.short
            var s = 1f
            while (need(text, s) > width - 24.dp && s > .8f) s -= .04f
            text to s
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(palette.coverFill)
            .clickable(role = Role.Button, onClick = onDownload),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NookIcons.Download, null, tint = palette.onCover, modifier = Modifier.size(18.dp))
        Text(label, style = style((14.5f * k).sp, FontWeight.Bold), color = palette.onCover, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ProgressSlot(slot: CardSlot.Progress, palette: ConnectionPalette, fs: Float) {
    val fill by animateFloatAsState(slot.percent / 100f, tween(400), label = "serverFetch")
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(palette.cover.copy(alpha = .16f))
            .drawBehind { drawRect(palette.cover.copy(alpha = .34f), size = Size(size.width * fill.coerceIn(0f, 1f), size.height)) }
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(slot.percent / 100f, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Text(slot.text, style = style(14.5.sp, FontWeight.Bold), color = palette.coverText, maxLines = 1, softWrap = false)
    }
}

/** The story's line at the card's first opening; closed once, never shown again. */
@Composable
private fun IntroHint(text: String, close: String, palette: ConnectionPalette, skyHeight: Dp, onClose: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .padding(8.dp)
            .fillMaxWidth()
            .heightIn(max = skyHeight - 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.card.copy(alpha = .9f))
            .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = style(12.sp, FontWeight.Normal, 16.8.sp), color = palette.ink, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) {
            Icon(NookIcons.Close, null, tint = palette.muted, modifier = Modifier.size(12.dp))
        }
    }
}

private val SLACK = 1.dp

// The switch (52 dp) and its 10 dp gap.
private val SWITCH_ROOM = 64.dp
