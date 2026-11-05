package ru.e1173.nookast.player

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.ChapterFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Chapters embedded in the playing file (ID3 CHAP with their APIC pictures), read by the service's
 * own player. A MediaController never sees them: the ID3 header travels in the track formats and
 * timed metadata is not forwarded across the session (user, 29.10.2026: embedded chapters lost to
 * the description's timestamps). UI and service share the process, so a flow carries them over.
 */
@OptIn(markerClass = [UnstableApi::class])
internal object NativeChapters {
    data class Snapshot(val mediaId: String?, val chapters: List<PlaybackChapter>)

    private val mutableState = MutableStateFlow(Snapshot(null, emptyList()))
    val state: StateFlow<Snapshot> = mutableState.asStateFlow()

    fun reset(mediaId: String?) {
        mutableState.value = Snapshot(mediaId, emptyList())
    }

    fun add(mediaId: String?, chapters: List<PlaybackChapter>) {
        if (chapters.isEmpty()) return
        mutableState.value = mutableState.value.let { current ->
            val base = if (current.mediaId == mediaId) current.chapters else emptyList()
            Snapshot(
                mediaId,
                (base + chapters).sortedBy(PlaybackChapter::startTimeMs).distinctBy(PlaybackChapter::startTimeMs),
            )
        }
    }

    fun fromTracks(tracks: Tracks): List<PlaybackChapter> = buildList {
        tracks.groups.forEach { group ->
            for (index in 0 until group.length) {
                group.getTrackFormat(index).metadata?.let { addAll(fromMetadata(it)) }
            }
        }
    }

    fun fromMetadata(metadata: Metadata): List<PlaybackChapter> = buildList {
        for (index in 0 until metadata.length()) {
            val chapter = metadata[index] as? Chapter ?: continue
            if (chapter.isHidden || chapter.startTimeMs < 0) continue
            add(
                PlaybackChapter(
                    startTimeMs = chapter.startTimeMs,
                    endTimeMs = chapter.endTimeMs.takeIf { it > chapter.startTimeMs },
                    title = chapter.title?.value.orEmpty(),
                    image = (chapter as? ChapterFrame)?.let { frame ->
                        (0 until frame.subFrameCount).firstNotNullOfOrNull { sub ->
                            (frame.getSubFrame(sub) as? ApicFrame)?.pictureData
                        }
                    },
                ),
            )
        }
    }
}
