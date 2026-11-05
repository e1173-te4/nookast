package ru.e1173.nookast.ui.designsystem.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NowPlayingControlTab {
    PLAYBACK,
    DESCRIPTION,
}

enum class NowPlayingControlTheme {
    LIGHT,
    DARK,
}

enum class NowPlayingDownloadVisualState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
}

data class NowPlayingControlStrings(
    val playbackTab: String,
    val descriptionTab: String,
    val episodeTitle: String,
    val podcastTitle: String,
    val elapsedTime: String,
    val remainingTime: String,
    val currentChapter: String,
    val chapterTitle: String,
    val download: String,
    val downloading: String,
    val deleteDownload: String,
    val listened: String,
    val collapsePlayer: String,
    val addToSaved: String,
    val removeFromSaved: String,
    val openQueue: String,
    val play: String,
    val pause: String,
    val seekBack: String,
    val seekForward: String,
    val seekTimeline: String,
    val previousChapter: String,
    val nextChapter: String,
    val downloadAction: String,
    val deleteDownloadAction: String,
    val markListened: String,
    val connected: String,
)

data class NowPlayingControlState(
    val selectedTab: NowPlayingControlTab = NowPlayingControlTab.PLAYBACK,
    val theme: NowPlayingControlTheme = NowPlayingControlTheme.LIGHT,
    val isPlaying: Boolean = true,
    val isSaved: Boolean = false,
    val isConnected: Boolean = true,
    val positionFraction: Float = 0.28f,
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    val chapterMarkers: List<Float> = listOf(0.14f, 0.29f, 0.47f, 0.68f, 0.84f),
    val downloadState: NowPlayingDownloadVisualState = NowPlayingDownloadVisualState.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val isListened: Boolean = true,
)

data class NowPlayingControlActions(
    val onCollapse: () -> Unit,
    val onTabSelected: (NowPlayingControlTab) -> Unit,
    val onToggleSaved: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onTogglePlayback: () -> Unit,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val onPositionFractionChanged: (Float) -> Unit,
    val onPreviousChapter: () -> Unit,
    val onNextChapter: () -> Unit,
    val onDownloadAction: () -> Unit,
    val onMarkListened: () -> Unit,
)

private data class NowPlayingControlColors(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val text: Color,
    val secondaryText: Color,
    val outline: Color,
    val track: Color,
    val primary: Color,
    val primaryBright: Color,
    val primaryDeep: Color,
    val coral: Color,
    val connected: Color,
)

private val LightColors = NowPlayingControlColors(
    background = Color(0xFFF9F9FE),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF0ECFA),
    text = Color(0xFF080626),
    secondaryText = Color(0xFF6E687A),
    outline = Color(0xFFE3DFF0),
    track = Color(0xFFE8E6F1),
    primary = Color(0xFF6418FD),
    primaryBright = Color(0xFF8A3CFF),
    primaryDeep = Color(0xFF321073),
    coral = Color(0xFFFF6E61),
    connected = Color(0xFF20C779),
)

private val DarkColors = NowPlayingControlColors(
    background = Color(0xFF0E0D18),
    surface = Color(0xFF151320),
    surfaceSoft = Color(0xFF201932),
    text = Color(0xFFF7F3FF),
    secondaryText = Color(0xFFB9AED1),
    outline = Color(0xFF312A46),
    track = Color(0xFF3A3154),
    primary = Color(0xFFB35CFF),
    primaryBright = Color(0xFF8D45FF),
    primaryDeep = Color(0xFF5D12F3),
    coral = Color(0xFFFF6E61),
    connected = Color(0xFF21C77A),
)

/**
 * Revision r1 of the reusable Now Playing control composition.
 *
 * The debug Design Catalog and the future production route must call this exact symbol. The
 * catalog owns sample state only; geometry and interaction targets live here.
 */
@Composable
fun NowPlayingControl(
    state: NowPlayingControlState,
    strings: NowPlayingControlStrings,
    actions: NowPlayingControlActions,
    modifier: Modifier = Modifier,
) {
    val colors = if (state.theme == NowPlayingControlTheme.DARK) DarkColors else LightColors
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(colors.surface, colors.background, colors.surface),
                ),
            ),
    ) {
        Header(
            state = state,
            strings = strings,
            colors = colors,
            actions = actions,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .offset(y = 34.dp),
        )

        Artwork(
            connected = state.isConnected,
            connectedDescription = strings.connected,
            colors = colors,
            modifier = Modifier
                .offset(x = 24.dp, y = 103.dp)
                .size(width = 298.dp, height = 270.dp),
        )

        Text(
            text = strings.episodeTitle,
            color = colors.text,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 393.dp)
                .padding(horizontal = 22.dp),
        )
        Text(
            text = strings.podcastTitle,
            color = colors.secondaryText,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 430.dp)
                .padding(horizontal = 30.dp),
        )

        PlaybackTimeline(
            positionFraction = state.positionFraction,
            markers = state.chapterMarkers,
            description = strings.seekTimeline,
            colors = colors,
            onPositionFractionChanged = actions.onPositionFractionChanged,
            modifier = Modifier
                .offset(x = 24.dp, y = 462.dp)
                .size(width = 298.dp, height = 22.dp),
        )
        Text(
            text = strings.elapsedTime,
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            modifier = Modifier.offset(x = 24.dp, y = 487.dp),
        )
        Text(
            text = strings.remainingTime,
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(82.dp)
                .offset(x = 240.dp, y = 487.dp),
        )

        SeekButton(
            seconds = state.seekBackSeconds,
            backwards = true,
            contentDescription = strings.seekBack,
            colors = colors,
            onClick = actions.onSeekBack,
            modifier = Modifier.offset(x = 38.dp, y = 524.dp),
        )
        PlaybackToggle(
            isPlaying = state.isPlaying,
            playDescription = strings.play,
            pauseDescription = strings.pause,
            colors = colors,
            onClick = actions.onTogglePlayback,
            modifier = Modifier.offset(x = 131.dp, y = 512.dp),
        )
        SeekButton(
            seconds = state.seekForwardSeconds,
            backwards = false,
            contentDescription = strings.seekForward,
            colors = colors,
            onClick = actions.onSeekForward,
            modifier = Modifier.offset(x = 236.dp, y = 524.dp),
        )

        ChapterNavigator(
            label = strings.currentChapter,
            title = strings.chapterTitle,
            previousDescription = strings.previousChapter,
            nextDescription = strings.nextChapter,
            colors = colors,
            onPrevious = actions.onPreviousChapter,
            onNext = actions.onNextChapter,
            modifier = Modifier
                .offset(x = 24.dp, y = 613.dp)
                .size(width = 298.dp, height = 42.dp),
        )

        ServiceActions(
            state = state,
            strings = strings,
            colors = colors,
            onDownloadAction = actions.onDownloadAction,
            onMarkListened = actions.onMarkListened,
            modifier = Modifier
                .offset(x = 38.dp, y = 672.dp)
                .size(width = 270.dp, height = 74.dp),
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .size(width = 116.dp, height = 4.dp)
                .background(colors.secondaryText.copy(alpha = 0.78f), CircleShape),
        )
    }
}

@Composable
private fun Header(
    state: NowPlayingControlState,
    strings: NowPlayingControlStrings,
    colors: NowPlayingControlColors,
    actions: NowPlayingControlActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        CanvasAction(
            description = strings.collapsePlayer,
            onClick = actions.onCollapse,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 8.dp)
                .size(48.dp),
        ) {
            val stroke = 3.dp.toPx()
            drawLine(colors.text, Offset(size.width * 0.27f, size.height * 0.42f), Offset(size.width * 0.5f, size.height * 0.65f), stroke, StrokeCap.Round)
            drawLine(colors.text, Offset(size.width * 0.5f, size.height * 0.65f), Offset(size.width * 0.73f, size.height * 0.42f), stroke, StrokeCap.Round)
        }

        Row(
            modifier = Modifier
                .offset(x = 63.dp, y = 2.dp)
                .height(54.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderTab(
                text = strings.playbackTab,
                selected = state.selectedTab == NowPlayingControlTab.PLAYBACK,
                colors = colors,
                onClick = { actions.onTabSelected(NowPlayingControlTab.PLAYBACK) },
            )
            HeaderTab(
                text = strings.descriptionTab,
                selected = state.selectedTab == NowPlayingControlTab.DESCRIPTION,
                colors = colors,
                onClick = { actions.onTabSelected(NowPlayingControlTab.DESCRIPTION) },
            )
        }

        CanvasAction(
            description = if (state.isSaved) strings.removeFromSaved else strings.addToSaved,
            onClick = actions.onToggleSaved,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-48).dp)
                .size(48.dp),
        ) {
            val heart = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.82f)
                cubicTo(size.width * 0.43f, size.height * 0.70f, size.width * 0.25f, size.height * 0.55f, size.width * 0.25f, size.height * 0.37f)
                cubicTo(size.width * 0.25f, size.height * 0.22f, size.width * 0.42f, size.height * 0.19f, size.width * 0.5f, size.height * 0.32f)
                cubicTo(size.width * 0.58f, size.height * 0.19f, size.width * 0.75f, size.height * 0.22f, size.width * 0.75f, size.height * 0.37f)
                cubicTo(size.width * 0.75f, size.height * 0.55f, size.width * 0.57f, size.height * 0.70f, size.width * 0.5f, size.height * 0.82f)
                close()
            }
            if (state.isSaved) {
                drawPath(heart, colors.primary)
            } else {
                drawPath(heart, colors.secondaryText, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        CanvasAction(
            description = strings.openQueue,
            onClick = actions.onOpenQueue,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-1).dp)
                .size(48.dp),
        ) {
            val color = colors.text
            val stroke = 2.7.dp.toPx()
            listOf(0.29f, 0.50f, 0.71f).forEach { y ->
                drawLine(color, Offset(size.width * 0.09f, size.height * y), Offset(size.width * 0.43f, size.height * y), stroke, StrokeCap.Round)
            }
            val triangle = Path().apply {
                moveTo(size.width * 0.56f, size.height * 0.20f)
                lineTo(size.width * 0.91f, size.height * 0.50f)
                lineTo(size.width * 0.56f, size.height * 0.80f)
                close()
            }
            drawPath(triangle, color)
        }
    }
}

@Composable
private fun HeaderTab(
    text: String,
    selected: Boolean,
    colors: NowPlayingControlColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .height(50.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colors.primary else colors.secondaryText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(if (selected) colors.primary else Color.Transparent, CircleShape),
        )
    }
}

@Composable
private fun Artwork(
    connected: Boolean,
    connectedDescription: String,
    colors: NowPlayingControlColors,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .shadow(7.dp, shape, ambientColor = Color(0x332A1559), spotColor = Color(0x332A1559))
            .clip(shape)
            .border(1.dp, colors.outline, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF7748E7), Color(0xFFF7A2C9), Color(0xFFFF7454)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            val sx = size.width / 640f
            val sy = size.height / 610f
            withTransform({ scale(sx, sy) }) {
                val violetWave = Path().apply {
                    moveTo(-20f, 32f)
                    cubicTo(150f, -73f, 238f, -33f, 315f, 80f)
                    cubicTo(388f, 188f, 510f, 147f, 730f, -18f)
                    lineTo(730f, -178f)
                    lineTo(-20f, -178f)
                    close()
                }
                drawPath(
                    violetWave,
                    Brush.linearGradient(
                        listOf(Color(0xFF7A4EE2), Color(0xFF9E68F4)),
                        Offset(20f, 80f),
                        Offset(520f, 430f),
                    ),
                )
                val pinkWave = Path().apply {
                    moveTo(286f, -78f)
                    cubicTo(390f, -42f, 432f, 66f, 405f, 197f)
                    cubicTo(382f, 306f, 470f, 342f, 732f, 310f)
                    lineTo(732f, -73f)
                    close()
                }
                drawPath(
                    pinkWave,
                    Brush.linearGradient(
                        listOf(Color(0xFFFFB8D1), Color(0xFFFF91BD)),
                        Offset(290f, 170f),
                        Offset(680f, 560f),
                    ),
                )
                val coralWave = Path().apply {
                    moveTo(-12f, 510f)
                    cubicTo(132f, 382f, 242f, 327f, 376f, 378f)
                    cubicTo(492f, 422f, 612f, 387f, 744f, 269f)
                    lineTo(744f, 682f)
                    lineTo(-12f, 682f)
                    close()
                }
                drawPath(
                    coralWave,
                    Brush.linearGradient(
                        listOf(Color(0xFFFF815E), Color(0xFFFF5F4D)),
                        Offset(20f, 560f),
                        Offset(640f, 760f),
                    ),
                )
                val silhouette = Path().apply {
                    moveTo(303f, 604f)
                    cubicTo(278f, 434f, 300f, 275f, 363f, 172f)
                    cubicTo(414f, 90f, 518f, 80f, 572f, 148f)
                    cubicTo(620f, 207f, 594f, 298f, 515f, 332f)
                    cubicTo(472f, 351f, 448f, 390f, 438f, 604f)
                    close()
                }
                drawPath(
                    silhouette,
                    Brush.linearGradient(
                        listOf(Color(0xFF24145F), Color(0xFF362080)),
                        Offset(320f, 350f),
                        Offset(540f, 790f),
                    ),
                )
                val shoulder = Path().apply {
                    moveTo(390f, 604f)
                    cubicTo(396f, 482f, 425f, 405f, 505f, 361f)
                    cubicTo(555f, 334f, 610f, 356f, 664f, 412f)
                    lineTo(708f, 604f)
                    close()
                }
                drawPath(shoulder, Color(0xFF352077))
                drawCircle(Color(0xFFFFA4C0), 30f, Offset(445f, 296f))
                drawLine(Color(0xFFFFA4C0), Offset(443f, 320f), Offset(430f, 407f), 13f, StrokeCap.Round)
                val lavenderStroke = Path().apply {
                    moveTo(0f, 152f)
                    cubicTo(104f, 226f, 174f, 264f, 250f, 245f)
                }
                drawPath(lavenderStroke, Color(0x55C895FF), style = Stroke(92f, cap = StrokeCap.Round))
            }
        }
        Canvas(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 10.dp)
                .size(16.dp)
                .semantics { contentDescription = connectedDescription },
        ) {
            drawCircle(if (connected) colors.connected else colors.coral)
        }
    }
}

@Composable
private fun PlaybackTimeline(
    positionFraction: Float,
    markers: List<Float>,
    description: String,
    colors: NowPlayingControlColors,
    onPositionFractionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = positionFraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(onPositionFractionChanged) {
                detectTapGestures { offset ->
                    onPositionFractionChanged((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    onPositionFractionChanged(target.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        val centerY = size.height / 2f
        val side = 8.dp.toPx()
        val startX = side
        val endX = size.width - side
        val activeX = startX + (endX - startX) * fraction
        val trackWidth = 5.dp.toPx()
        drawLine(colors.track, Offset(startX, centerY), Offset(endX, centerY), trackWidth, StrokeCap.Round)
        drawLine(colors.primary, Offset(startX, centerY), Offset(activeX, centerY), trackWidth, StrokeCap.Round)
        markers.forEachIndexed { index, marker ->
            val x = startX + (endX - startX) * marker.coerceIn(0f, 1f)
            drawLine(
                if (index < 2) colors.coral else colors.secondaryText.copy(alpha = 0.55f),
                Offset(x, centerY - 6.dp.toPx()),
                Offset(x, centerY + 6.dp.toPx()),
                1.5.dp.toPx(),
                StrokeCap.Round,
            )
        }
        drawCircle(colors.primary, 9.dp.toPx(), Offset(activeX, centerY))
        drawCircle(colors.surface.copy(alpha = 0.86f), 9.dp.toPx(), Offset(activeX, centerY), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun SeekButton(
    seconds: Int,
    backwards: Boolean,
    contentDescription: String,
    colors: NowPlayingControlColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(72.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.minDimension / 154f
            val center = Offset(77f * scale, 77f * scale)
            drawCircle(colors.surface.copy(alpha = 0.76f), 74f * scale, center)
            drawCircle(colors.outline, 74f * scale, center, style = Stroke(3f * scale))
            val radius = 46.3249f * scale
            drawArc(
                color = colors.text,
                startAngle = if (backwards) -57.34f else 29.999f,
                sweepAngle = if (backwards) 207.34f else -207.34f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(9f * scale, cap = StrokeCap.Round),
            )
            fun x(value: Float): Float = (if (backwards) value else 154f - value) * scale
            val arrow = Path().apply {
                moveTo(x(72f), 38f * scale)
                lineTo(x(102f), 20f * scale)
                lineTo(x(102f), 56f * scale)
                close()
            }
            drawPath(arrow, colors.text)
        }
        Text(
            text = seconds.toString(),
            color = colors.text,
            fontSize = 19.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaybackToggle(
    isPlaying: Boolean,
    playDescription: String,
    pauseDescription: String,
    colors: NowPlayingControlColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .size(84.dp)
            .shadow(10.dp, CircleShape, ambientColor = colors.primary.copy(alpha = 0.30f), spotColor = colors.primary.copy(alpha = 0.36f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = if (isPlaying) pauseDescription else playDescription },
    ) {
        drawCircle(
            Brush.linearGradient(
                listOf(colors.primaryBright, colors.primaryDeep),
                Offset.Zero,
                Offset(size.width, size.height),
            ),
        )
        if (isPlaying) {
            val barWidth = size.width * 0.13f
            drawRoundRect(
                Color.White,
                Offset(size.width * 0.34f, size.height * 0.27f),
                Size(barWidth, size.height * 0.46f),
                CornerRadius(barWidth / 2f),
            )
            drawRoundRect(
                Color.White,
                Offset(size.width * 0.56f, size.height * 0.27f),
                Size(barWidth, size.height * 0.46f),
                CornerRadius(barWidth / 2f),
            )
        } else {
            val triangle = Path().apply {
                moveTo(size.width * 0.39f, size.height * 0.28f)
                lineTo(size.width * 0.73f, size.height * 0.50f)
                lineTo(size.width * 0.39f, size.height * 0.72f)
                close()
            }
            drawPath(triangle, Color.White)
        }
    }
}

@Composable
private fun ChapterNavigator(
    label: String,
    title: String,
    previousDescription: String,
    nextDescription: String,
    colors: NowPlayingControlColors,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(colors.surfaceSoft.copy(alpha = 0.78f))
            .border(1.dp, colors.outline, RoundedCornerShape(13.dp)),
    ) {
        CanvasAction(
            description = previousDescription,
            onClick = onPrevious,
            modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
        ) {
            drawLine(colors.primaryDeep, Offset(size.width * 0.60f, size.height * 0.28f), Offset(size.width * 0.40f, size.height * 0.50f), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(colors.primaryDeep, Offset(size.width * 0.40f, size.height * 0.50f), Offset(size.width * 0.60f, size.height * 0.72f), 2.5.dp.toPx(), StrokeCap.Round)
        }
        Column(
            modifier = Modifier.align(Alignment.Center).width(205.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = colors.secondaryText, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1)
            Text(
                title,
                color = colors.text,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CanvasAction(
            description = nextDescription,
            onClick = onNext,
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
        ) {
            drawLine(colors.primaryDeep, Offset(size.width * 0.40f, size.height * 0.28f), Offset(size.width * 0.60f, size.height * 0.50f), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(colors.primaryDeep, Offset(size.width * 0.60f, size.height * 0.50f), Offset(size.width * 0.40f, size.height * 0.72f), 2.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun ServiceActions(
    state: NowPlayingControlState,
    strings: NowPlayingControlStrings,
    colors: NowPlayingControlColors,
    onDownloadAction: () -> Unit,
    onMarkListened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ServiceAction(
            label = when (state.downloadState) {
                NowPlayingDownloadVisualState.NOT_DOWNLOADED -> strings.download
                NowPlayingDownloadVisualState.DOWNLOADING -> strings.downloading
                NowPlayingDownloadVisualState.DOWNLOADED -> strings.deleteDownload
            },
            description = if (state.downloadState == NowPlayingDownloadVisualState.DOWNLOADED) {
                strings.deleteDownloadAction
            } else {
                strings.downloadAction
            },
            enabled = true,
            onClick = onDownloadAction,
            modifier = Modifier.width(128.dp),
            icon = {
                DownloadActionGlyph(
                    state = state.downloadState,
                    progress = state.downloadProgress,
                    colors = colors,
                    modifier = Modifier.size(34.dp),
                )
            },
            colors = colors,
        )
        Box(Modifier.width(1.dp).height(62.dp).background(colors.outline))
        ServiceAction(
            label = strings.listened,
            description = strings.markListened,
            enabled = !state.isListened,
            onClick = onMarkListened,
            modifier = Modifier.width(128.dp),
            icon = {
                ListenedGlyph(
                    checked = state.isListened,
                    colors = colors,
                    modifier = Modifier.size(34.dp),
                )
            },
            colors = colors,
        )
    }
}

@Composable
private fun ServiceAction(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    colors: NowPlayingControlColors,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(74.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        icon()
        Text(
            text = label,
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadActionGlyph(
    state: NowPlayingDownloadVisualState,
    progress: Float,
    colors: NowPlayingControlColors,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = 2.4.dp.toPx()
        when (state) {
            NowPlayingDownloadVisualState.NOT_DOWNLOADED -> {
                drawLine(colors.primaryDeep, Offset(size.width * 0.5f, size.height * 0.08f), Offset(size.width * 0.5f, size.height * 0.60f), stroke, StrokeCap.Round)
                drawLine(colors.primaryDeep, Offset(size.width * 0.26f, size.height * 0.41f), Offset(size.width * 0.5f, size.height * 0.66f), stroke, StrokeCap.Round)
                drawLine(colors.primaryDeep, Offset(size.width * 0.74f, size.height * 0.41f), Offset(size.width * 0.5f, size.height * 0.66f), stroke, StrokeCap.Round)
                drawLine(colors.primaryDeep, Offset(size.width * 0.18f, size.height * 0.87f), Offset(size.width * 0.82f, size.height * 0.87f), stroke, StrokeCap.Round)
            }
            NowPlayingDownloadVisualState.DOWNLOADING -> {
                drawCircle(colors.outline, size.minDimension * 0.43f, style = Stroke(3.dp.toPx()))
                drawArc(
                    colors.primary,
                    -90f,
                    360f * progress.coerceIn(0f, 1f),
                    false,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(colors.text, Offset(size.width * 0.5f, size.height * 0.24f), Offset(size.width * 0.5f, size.height * 0.62f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(colors.text, Offset(size.width * 0.36f, size.height * 0.50f), Offset(size.width * 0.5f, size.height * 0.65f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(colors.text, Offset(size.width * 0.64f, size.height * 0.50f), Offset(size.width * 0.5f, size.height * 0.65f), 2.dp.toPx(), StrokeCap.Round)
            }
            NowPlayingDownloadVisualState.DOWNLOADED -> {
                drawRoundRect(
                    colors.primaryDeep,
                    Offset(size.width * 0.29f, size.height * 0.27f),
                    Size(size.width * 0.42f, size.height * 0.60f),
                    CornerRadius(size.width * 0.07f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawLine(colors.primaryDeep, Offset(size.width * 0.21f, size.height * 0.25f), Offset(size.width * 0.79f, size.height * 0.25f), stroke, StrokeCap.Round)
                drawLine(colors.primaryDeep, Offset(size.width * 0.39f, size.height * 0.12f), Offset(size.width * 0.61f, size.height * 0.12f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ListenedGlyph(
    checked: Boolean,
    colors: NowPlayingControlColors,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (checked) {
            drawCircle(colors.coral)
            drawLine(Color.White, Offset(size.width * 0.27f, size.height * 0.51f), Offset(size.width * 0.44f, size.height * 0.68f), 2.7.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(size.width * 0.44f, size.height * 0.68f), Offset(size.width * 0.75f, size.height * 0.31f), 2.7.dp.toPx(), StrokeCap.Round)
        } else {
            drawCircle(colors.secondaryText, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun CanvasAction(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        onDraw = draw,
    )
}
