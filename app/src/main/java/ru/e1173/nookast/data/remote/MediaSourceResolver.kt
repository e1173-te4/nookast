package ru.e1173.nookast.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.e1173.nookast.auth.ServerAddress

data class MediaSourceCandidates(
    val primaryUrl: String?,
    val fallbackUrl: String?,
    val pinePodsUrl: String? = null,
)

/**
 * Source order (DEC-023): a file on the device (handled by the caller), then the copy stored on
 * the PinePods server, then the RSS origin, then PinePods' on-demand download as the rescue path.
 */
object MediaSourceResolver {
    fun sources(
        serverUrl: String,
        episodeId: Int,
        rssMediaUrl: String,
        userId: Int = 0,
        onServer: Boolean = false,
    ): MediaSourceCandidates {
        val baseUrl = ServerAddress.normalize(serverUrl).getOrNull()
        val pinePodsUrl = baseUrl
            ?.takeIf { episodeId > 0 }
            ?.resolve("/api/episodes/$episodeId/download")
            ?.toString()
        val rssUrl = rssMediaUrl.trim()
            .toHttpUrlOrNull()
            ?.takeIf { it.scheme == "https" || it.scheme == "http" }
            ?.toString()
        // The stored copy streams in ~0.2 s. The key is added by the player's data source when the
        // request is made, so it never appears in a MediaItem, a controller or a journal.
        val streamUrl = baseUrl
            ?.takeIf { onServer && episodeId > 0 && userId > 0 }
            ?.resolve("/api/data/stream/$episodeId")
            ?.newBuilder()
            ?.addQueryParameter("user_id", userId.toString())
            ?.build()
            ?.toString()
        // This PinePods endpoint exports a complete file: the server first downloads the upstream
        // episode and writes metadata. It is a useful rescue path, but not a streaming proxy.
        return if (streamUrl != null) {
            MediaSourceCandidates(primaryUrl = streamUrl, fallbackUrl = rssUrl ?: pinePodsUrl, pinePodsUrl = pinePodsUrl)
        } else {
            MediaSourceCandidates(primaryUrl = rssUrl, fallbackUrl = pinePodsUrl, pinePodsUrl = pinePodsUrl)
        }
    }

    /** Any PinePods media request: the stored-copy stream or the on-demand download. */
    fun isPinePodsMediaRequest(serverUrl: String, requestUrl: String): Boolean =
        matchesServerPath(serverUrl, requestUrl, PINEPODS_EPISODE_MEDIA_PATH) ||
            matchesServerPath(serverUrl, requestUrl, PINEPODS_STREAM_PATH)

    /** The on-demand download, which answers only after fetching the whole episode upstream. */
    fun isPinePodsDownloadRequest(serverUrl: String, requestUrl: String): Boolean =
        matchesServerPath(serverUrl, requestUrl, PINEPODS_EPISODE_MEDIA_PATH)

    fun isPinePodsStreamRequest(serverUrl: String, requestUrl: String): Boolean =
        matchesServerPath(serverUrl, requestUrl, PINEPODS_STREAM_PATH)

    private fun matchesServerPath(serverUrl: String, requestUrl: String, path: Regex): Boolean {
        val baseUrl = ServerAddress.normalize(serverUrl).getOrNull() ?: return false
        val request = requestUrl.toHttpUrlOrNull() ?: return false
        return request.scheme == baseUrl.scheme &&
            request.host == baseUrl.host &&
            request.port == baseUrl.port &&
            request.encodedPath.matches(path)
    }

    private val PINEPODS_EPISODE_MEDIA_PATH =
        Regex("^/api/episodes/[1-9][0-9]*/download/?$")
    private val PINEPODS_STREAM_PATH =
        Regex("^/api/data/stream/[1-9][0-9]*/?$")
}
