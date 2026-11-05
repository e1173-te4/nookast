package ru.e1173.nookast.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.data.local.QueueListEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MediaLibraryTreeTest {
    private val titles = MediaLibraryTree.FolderTitles("Root", "Continue", "Up Next", "Downloads")

    @Test
    fun rootHasThreeBrowsableFolders() {
        val children = MediaLibraryTree.rootChildren(titles)

        assertEquals(
            listOf(MediaLibraryTree.CONTINUE_ID, MediaLibraryTree.QUEUE_ID, MediaLibraryTree.DOWNLOADS_ID),
            children.map { it.mediaId },
        )
        assertTrue(children.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
        assertEquals(MediaLibraryTree.RECENT_ROOT_ID, MediaLibraryTree.root(titles, recent = true).mediaId)
        assertNull(MediaLibraryTree.folderItem("42", titles))
    }

    @Test
    fun episodeItemCarriesNoMediaUriAndProviderArtwork() {
        val item = MediaLibraryTree.episodeItem(entry(42, durationSeconds = 90)) { id ->
            Uri.parse("content://test.artwork/episode/$id")
        }

        assertEquals("42", item.mediaId)
        assertNull(item.localConfiguration)
        assertEquals("content://test.artwork/episode/42", item.mediaMetadata.artworkUri.toString())
        assertEquals(90_000L, item.mediaMetadata.durationMs)
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, item.mediaMetadata.mediaType)
        assertTrue(item.mediaMetadata.isPlayable == true)
    }

    @Test
    fun pagingFollowsMedia3Contract() {
        val items = (1..5).toList()

        assertEquals(listOf(1, 2), MediaLibraryTree.page(items, page = 0, pageSize = 2))
        assertEquals(listOf(5), MediaLibraryTree.page(items, page = 2, pageSize = 2))
        assertEquals(emptyList<Int>(), MediaLibraryTree.page(items, page = 3, pageSize = 2))
        assertEquals(emptyList<Int>(), MediaLibraryTree.page(items, page = 0, pageSize = 0))
        assertEquals(items, MediaLibraryTree.page(items, page = 0, pageSize = Int.MAX_VALUE))
    }

    @Test
    fun playingOneQueuedEpisodePlaysTheQueueFromIt() {
        val queue = listOf(1, 2, 3)

        assertEquals(queue to 1, MediaLibraryTree.expandToQueue(listOf(2), queue) { a, b -> a == b })
        assertEquals(listOf(9) to 0, MediaLibraryTree.expandToQueue(listOf(9), queue) { a, b -> a == b })
        assertEquals(listOf(3, 1) to 0, MediaLibraryTree.expandToQueue(listOf(3, 1), queue) { a, b -> a == b })
    }

    @Test
    fun artworkUrisRoundTripAndRejectForeignPaths() {
        val uri = ArtworkContentProvider.uriFor(-17)

        assertEquals(-17, ArtworkContentProvider.playbackId(uri))
        assertNull(ArtworkContentProvider.playbackId(Uri.parse("content://other/episode/1")))
        assertNull(
            ArtworkContentProvider.playbackId(
                Uri.parse("content://${ArtworkContentProvider.AUTHORITY}/episode/1/x"),
            ),
        )
        assertNull(
            ArtworkContentProvider.playbackId(
                Uri.parse("content://${ArtworkContentProvider.AUTHORITY}/episode/0"),
            ),
        )
        assertFalse(MediaLibraryTree.isFolder("1"))
    }

    @Test
    fun artworkIsScaledToTheLongestEdge() {
        assertEquals(512 to 256, ArtworkContentProvider.scaledSize(2048, 1024))
        assertEquals(300 to 300, ArtworkContentProvider.scaledSize(300, 300))
        assertEquals(1 to 512, ArtworkContentProvider.scaledSize(1, 3000))
    }

    private fun entry(id: Int, durationSeconds: Int? = null) = QueueListEntry(
        episodeId = id,
        queuePosition = 0,
        episodeTitle = "Episode",
        podcastName = "Podcast",
        artworkUrl = "https://example.invalid/a.jpg",
        mediaUrl = "https://example.invalid/a.mp3",
        durationSeconds = durationSeconds,
        listenDurationSeconds = null,
        completed = false,
        description = null,
        publishedAt = null,
        downloaded = null,
        refreshedAtEpochMs = null,
    )
}
