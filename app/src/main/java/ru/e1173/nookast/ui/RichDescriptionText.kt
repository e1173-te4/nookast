package ru.e1173.nookast.ui

import androidx.core.net.toUri
import android.text.Html
import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private data class DescriptionLink(
    val start: Int,
    val end: Int,
    val uri: String,
)

internal data class DescriptionTimestamp(
    val positionMs: Long,
    val label: String,
    /** Read from the episode description, not chapters of the stream or the feed. */
    val fromDescription: Boolean = false,
)

// 1:02:03, 12:34, and h.mm.ss with dots such as 0.26.57 (user, 29.10.2026); a dotted time needs all
// three parts, so prices and versions such as 12.50 stay text.
private val DescriptionTimestampRegex = Regex(
    """(?<![\d.:])(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)(?![\d:])|(?<![\d.:])(\d{1,2})\.([0-5]\d)\.([0-5]\d)(?![\d.])""",
)
private val BareUrlRegex = Regex("(?i)https?://[^\\s<>()]+")
private val SemanticDescriptionLineStartRegex = Regex(
    """^(?:[-*•‣▪◦]\s+|\d{1,3}[.)]\s+|[\[(]?(?:\d{1,2}[:.])?[0-5]?\d[:.][0-5]\d[\])]?(?:\s|$))""",
)

internal fun normalizeDescriptionText(value: String): String {
    if (value.isBlank()) return ""
    val spanned = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
    return normalizeDescriptionPlainText(spanned.toString())
}

internal fun normalizeDescriptionPlainText(value: String): String {
    val normalizedLines = value
        .replace('\u00a0', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { line -> line.trim().replace(Regex("[\\t ]{2,}"), " ") }

    var previousLine = ""
    val logicalBlocks = mutableListOf<String>()
    val currentBlock = StringBuilder()

    fun flushBlock() {
        currentBlock.toString().trim().takeIf(String::isNotEmpty)?.let(logicalBlocks::add)
        currentBlock.clear()
    }

    normalizedLines.forEach { line ->
        when {
            line.isEmpty() -> flushBlock()
            SemanticDescriptionLineStartRegex.containsMatchIn(line) -> {
                // Lists and chapter markers are intentional visual rows, not wrapped prose.
                flushBlock()
                currentBlock.append(line)
            }
            currentBlock.isEmpty() -> currentBlock.append(line)
            isProseWrap(previousLine, line) -> currentBlock.append(' ').append(line)
            // A short line or one that ends a sentence is the author's own line break: lists
            // and track lists stay one entry per line (user, 29.10.2026).
            else -> currentBlock.append('\n').append(line)
        }
        previousLine = line
    }
    flushBlock()
    return logicalBlocks.joinToString("\n\n")
}

internal fun extractDescriptionTimestamps(html: String): List<DescriptionTimestamp> {
    val normalized = if ('<' in html && '>' in html) {
        normalizeDescriptionText(html)
    } else {
        normalizeDescriptionPlainText(html)
    }
    if (normalized.isBlank()) return emptyList()
    val matches = timestampMatches(normalized)
    return matches.mapIndexedNotNull { index, match ->
        val positionMs = timestampSeconds(match) * 1_000L
        val nextStart = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
        val label = cleanChapterLabel(normalized.substring(match.range.last + 1, nextStart))
            .ifBlank { match.value }
        DescriptionTimestamp(positionMs = positionMs, label = label)
    }.distinctBy(DescriptionTimestamp::positionMs)
}

/**
 * Timestamps of [html] when they form a list (two or more): those become the chapter panel of the
 * Description tab and leave the text (user, APK-3.4 review). A lone time in prose stays inline.
 */
internal fun descriptionChapterList(html: String): List<DescriptionTimestamp> =
    extractDescriptionTimestamps(html).takeIf { it.size >= 2 }.orEmpty()

/**
 * [normalized] without its timestamp list: from the first timestamp (with an opening bracket
 * before it) to the end of the last timestamp's line, as [descriptionChapterList] reads it.
 */
internal fun descriptionWithoutTimestampList(normalized: String): String {
    val matches = timestampMatches(normalized)
    if (matches.size < 2) return normalized
    var start = matches.first().range.first
    while (start > 0 && normalized[start - 1] in "[({ \t") start--
    val end = normalized.indexOf('\n', matches.last().range.last).let { if (it < 0) normalized.length else it }
    return listOf(normalized.substring(0, start).trimEnd(), normalized.substring(end).trimStart())
        .filter(String::isNotBlank)
        .joinToString("\n\n")
}

/**
 * Description as one text per paragraph with link annotations. It used to be a FlowRow of one
 * Text per word, which made long descriptions scroll in jerks (APK-3.4 review).
 */
@Composable
internal fun RichDescriptionText(
    html: String,
    fallback: String,
    style: TextStyle,
    color: Color,
    onSeekTo: ((Long) -> Unit)? = null,
    hideTimestampList: Boolean = false,
) {
    val accent = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText
    val text = remember(html, fallback, hideTimestampList) {
        normalizeDescriptionText(html)
            .let { if (hideTimestampList) descriptionWithoutTimestampList(it) else it }
            .ifBlank { fallback }
    }
    val links = remember(html, text) { descriptionLinks(html, text) }
    val paragraphs = remember(text, links, accent, onSeekTo) {
        val linkStyle = TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
        val timeStyle = TextLinkStyles(SpanStyle(color = accent, fontWeight = ru.e1173.nookast.designsystem.NookType.emphasis))
        var offset = 0
        text.split("\n\n").map { paragraph ->
            val base = offset
            offset += paragraph.length + 2
            buildAnnotatedString {
                append(paragraph)
                links.forEach { link ->
                    val start = (link.start - base).coerceAtLeast(0)
                    val end = (link.end - base).coerceAtMost(paragraph.length)
                    if (start < end && isSafeDescriptionLink(link.uri)) {
                        addLink(LinkAnnotation.Url(link.uri, linkStyle), start, end)
                    }
                }
                if (onSeekTo != null) {
                    timestampMatches(paragraph).forEach { match ->
                        val positionMs = timestampSeconds(match) * 1_000L
                        addLink(
                            LinkAnnotation.Clickable("t$positionMs", timeStyle) { onSeekTo(positionMs) },
                            match.range.first,
                            match.range.last + 1,
                        )
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paragraphs.forEach { paragraph -> Text(paragraph, style = style.copy(color = color)) }
    }
}

private fun descriptionLinks(html: String, normalized: String): List<DescriptionLink> {
    if (html.isBlank()) return emptyList()
    val result = mutableListOf<DescriptionLink>()
    val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) as Spanned
    var searchFrom = 0
    spanned.getSpans(0, spanned.length, URLSpan::class.java)
        .sortedBy(spanned::getSpanStart)
        .forEach { span ->
            val label = normalizeDescriptionPlainText(
                spanned.subSequence(spanned.getSpanStart(span), spanned.getSpanEnd(span)).toString(),
            )
            val start = normalized.indexOf(label, searchFrom, ignoreCase = false)
            if (start >= 0 && label.isNotEmpty()) {
                result += DescriptionLink(start, start + label.length, span.url)
                searchFrom = start + label.length
            }
        }
    BareUrlRegex.findAll(normalized).forEach { match ->
        if (result.none { match.range.first < it.end && match.range.last + 1 > it.start }) {
            result += DescriptionLink(match.range.first, match.range.last + 1, match.value)
        }
    }
    return result
}

/**
 * The times of [text] that mark a moment of the episode. Once a line has one, the rest of the line
 * is its label: a later time there counts only when it is not quoted or bracketed and comes after
 * the previous one (user, 5.11.2026: «01:42:58 … эпоха «Кино в 21:00 на СТС»» made a chapter at 21:00).
 */
internal fun timestampMatches(text: String): List<MatchResult> {
    val kept = mutableListOf<MatchResult>()
    var lineStart = -1
    var lastSeconds = -1L
    var lastEnd = 0
    DescriptionTimestampRegex.findAll(text).forEach { match ->
        val start = text.lastIndexOf('\n', match.range.first - 1) + 1
        if (start != lineStart) {
            lineStart = start
            lastSeconds = -1L
        }
        val seconds = timestampSeconds(match)
        val inLabel = lastSeconds >= 0 &&
            (seconds <= lastSeconds || isQuoted(text.substring(lastEnd, match.range.first)))
        if (!inLabel) {
            kept += match
            lastSeconds = seconds
            lastEnd = match.range.last + 1
        }
    }
    return kept
}

/** Whether the end of [prefix] (the text since the previous time) is inside quotes or brackets opened in it. */
private fun isQuoted(prefix: String): Boolean =
    prefix.count { it == '«' } > prefix.count { it == '»' } ||
        prefix.count { it == '“' } > prefix.count { it == '”' } ||
        prefix.count { it == '(' } > prefix.count { it == ')' } ||
        prefix.count { it == '"' } % 2 == 1

private fun timestampSeconds(match: MatchResult): Long {
    // Groups 1–3 hold a colon time, 4–6 a dotted one.
    val dotted = match.groups[6] != null
    val hours = match.groups[if (dotted) 4 else 1]?.value?.toLongOrNull() ?: 0L
    val minutes = match.groups[if (dotted) 5 else 2]?.value?.toLongOrNull() ?: 0L
    val seconds = match.groups[if (dotted) 6 else 3]?.value?.toLongOrNull() ?: 0L
    return hours * 3_600L + minutes * 60L + seconds
}

private fun isSafeDescriptionLink(value: String): Boolean = runCatching {
    when (value.toUri().scheme?.lowercase()) {
        "http", "https", "mailto" -> true
        else -> false
    }
}.getOrDefault(false)

/**
 * The text after a timestamp up to the next one, as a chapter title: one line, without the
 * brackets, dashes and separators around timestamps like "[00:00] — Intro (" (user, APK-3.1).
 */
internal fun cleanChapterLabel(raw: String): String {
    val line = raw.substringBefore('\n').trim()
    val separators = charArrayOf(']', ')', '}', '|', '-', '—', '–', ':', '.', ',', '·', '•', ' ', '\t')
    val opening = charArrayOf('[', '(', '{', '|', '-', '—', '–', ':', ',', '·', '•', ' ', '\t')
    var label = line.trimStart(*separators).trimEnd(*opening)
    // A timestamp written as "[00:00]" leaves a bracket pair around nothing; drop empty ones.
    label = label.replace(Regex("""\(\s*\)|\[\s*]"""), "").trim()
    return if (label.length > MAX_CHAPTER_LABEL) label.take(MAX_CHAPTER_LABEL - 1).trimEnd() + "…" else label
}

private const val MAX_CHAPTER_LABEL = 80

/**
 * Whether [line] continues [previous] as wrapped prose: the editor broke a long sentence, so the
 * line before is long, does not end a sentence and the next one goes on in lower case.
 */
private fun isProseWrap(previous: String, line: String): Boolean =
    previous.length >= PROSE_WRAP_MIN_LENGTH &&
        previous.last() !in ".!?:;…»\"" &&
        line.firstOrNull()?.isLowerCase() == true

private const val PROSE_WRAP_MIN_LENGTH = 60
