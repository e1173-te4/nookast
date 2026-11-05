package ru.e1173.nookast.designcatalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingControl
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingControlActions
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingControlState
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingControlStrings
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingControlTab
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingControlTheme
import ru.e1173.nookast.ui.designsystem.nowplaying.NowPlayingDownloadVisualState

internal val NowPlayingControlPhoneLightPlaybackScene = CatalogScene(
    id = "now-playing-control-phone-light-playback-r1",
    title = "Now Playing · Phone · Light",
    state = "playback",
    revision = "now-playing-control/r1",
    behaviorContract = "docs/design/catalog/components/now-playing-control/r1/BEHAVIOR.md",
    content = { context, modifier ->
        NowPlayingControlPhoneLightPlaybackSample(context, modifier)
    },
)

@Composable
private fun NowPlayingControlPhoneLightPlaybackSample(
    context: CatalogSceneContext,
    modifier: Modifier,
) {
    val strings = if (context.locale == CatalogLocale.RU) russianStrings() else englishStrings()
    var selectedTab by remember { mutableStateOf(NowPlayingControlTab.PLAYBACK) }
    var isPlaying by remember { mutableStateOf(true) }
    var isSaved by remember { mutableStateOf(false) }
    var positionFraction by remember { mutableFloatStateOf(0.28f) }
    var downloadState by remember {
        mutableStateOf(NowPlayingDownloadVisualState.NOT_DOWNLOADED)
    }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isListened by remember { mutableStateOf(true) }

    val durationSeconds = 3_000
    val elapsedSeconds = (durationSeconds * positionFraction).toInt().coerceIn(0, durationSeconds)
    val remainingSeconds = (durationSeconds - elapsedSeconds).coerceAtLeast(0)
    val localizedStrings = strings.copy(
        elapsedTime = formatDuration(elapsedSeconds),
        remainingTime = "−${formatDuration(remainingSeconds)}",
    )
    val markers = listOf(0.14f, 0.29f, 0.47f, 0.68f, 0.84f)

    NowPlayingControl(
        state = NowPlayingControlState(
            selectedTab = selectedTab,
            theme = NowPlayingControlTheme.LIGHT,
            isPlaying = isPlaying,
            isSaved = isSaved,
            isConnected = true,
            positionFraction = positionFraction,
            seekBackSeconds = 10,
            seekForwardSeconds = 30,
            chapterMarkers = markers,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            isListened = isListened,
        ),
        strings = localizedStrings,
        actions = NowPlayingControlActions(
            onCollapse = {},
            onTabSelected = { selectedTab = it },
            onToggleSaved = { isSaved = !isSaved },
            onOpenQueue = {},
            onTogglePlayback = { isPlaying = !isPlaying },
            onSeekBack = {
                positionFraction = (positionFraction - 10f / durationSeconds).coerceAtLeast(0f)
            },
            onSeekForward = {
                positionFraction = (positionFraction + 30f / durationSeconds).coerceAtMost(1f)
            },
            onPositionFractionChanged = { positionFraction = it.coerceIn(0f, 1f) },
            onPreviousChapter = {
                positionFraction = markers.lastOrNull { it < positionFraction - 0.01f } ?: 0f
            },
            onNextChapter = {
                positionFraction = markers.firstOrNull { it > positionFraction + 0.01f } ?: 1f
            },
            onDownloadAction = {
                when (downloadState) {
                    NowPlayingDownloadVisualState.NOT_DOWNLOADED -> {
                        downloadState = NowPlayingDownloadVisualState.DOWNLOADING
                        downloadProgress = 0.62f
                    }
                    NowPlayingDownloadVisualState.DOWNLOADING -> {
                        downloadState = NowPlayingDownloadVisualState.DOWNLOADED
                        downloadProgress = 1f
                    }
                    NowPlayingDownloadVisualState.DOWNLOADED -> {
                        downloadState = NowPlayingDownloadVisualState.NOT_DOWNLOADED
                        downloadProgress = 0f
                    }
                }
            },
            onMarkListened = { isListened = true },
        ),
        modifier = modifier,
    )
}

private fun russianStrings() = NowPlayingControlStrings(
    playbackTab = "Воспроизведение",
    descriptionTab = "Описание",
    episodeTitle = "Онбординг родителя",
    podcastTitle = "ПапСовет",
    elapsedTime = "12:24",
    remainingTime = "−37:36",
    currentChapter = "Текущая глава",
    chapterTitle = "Почему мы откладываем разговор",
    download = "Скачать",
    downloading = "Загрузка 62%",
    deleteDownload = "Удалить",
    listened = "Прослушано",
    collapsePlayer = "Свернуть проигрыватель",
    addToSaved = "Добавить выпуск в избранное",
    removeFromSaved = "Удалить выпуск из избранного",
    openQueue = "Открыть очередь воспроизведения",
    play = "Воспроизвести",
    pause = "Пауза",
    seekBack = "Перемотать назад на 10 секунд",
    seekForward = "Перемотать вперёд на 30 секунд",
    seekTimeline = "Позиция воспроизведения",
    previousChapter = "Предыдущая глава",
    nextChapter = "Следующая глава",
    downloadAction = "Скачать выпуск",
    deleteDownloadAction = "Удалить скачанный выпуск",
    markListened = "Пометить прослушанным",
    connected = "Источник доступен",
)

private fun englishStrings() = NowPlayingControlStrings(
    playbackTab = "Playback",
    descriptionTab = "Description",
    episodeTitle = "Parent onboarding",
    podcastTitle = "PapSovet",
    elapsedTime = "12:24",
    remainingTime = "−37:36",
    currentChapter = "Current chapter",
    chapterTitle = "Why we postpone the conversation",
    download = "Download",
    downloading = "Downloading 62%",
    deleteDownload = "Delete",
    listened = "Listened",
    collapsePlayer = "Collapse player",
    addToSaved = "Save episode",
    removeFromSaved = "Remove saved episode",
    openQueue = "Open playback queue",
    play = "Play",
    pause = "Pause",
    seekBack = "Seek back 10 seconds",
    seekForward = "Seek forward 30 seconds",
    seekTimeline = "Playback position",
    previousChapter = "Previous chapter",
    nextChapter = "Next chapter",
    downloadAction = "Download episode",
    deleteDownloadAction = "Delete downloaded episode",
    markListened = "Mark as listened",
    connected = "Source available",
)

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
