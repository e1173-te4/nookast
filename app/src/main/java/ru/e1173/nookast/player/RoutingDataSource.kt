package ru.e1173.nookast.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import ru.e1173.nookast.core.network.HttpClients

/** Opens each request with [primary] or, when [useAlternate] says so for its URI, [alternate]. */
@OptIn(UnstableApi::class)
internal class RoutingDataSource(
    private val primary: DataSource,
    private val alternate: DataSource,
    private val useAlternate: (Uri) -> Boolean,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        primary.addTransferListener(transferListener)
        alternate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (useAlternate(dataSpec.uri)) alternate else primary
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(
        private val primary: DataSource.Factory,
        private val alternate: DataSource.Factory,
        private val useAlternate: (Uri) -> Boolean,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RoutingDataSource(primary.createDataSource(), alternate.createDataSource(), useAlternate)
    }
}

/**
 * Network sources for playback over the app's one OkHttp stack (P-08): shared connection pool,
 * cross-protocol redirects, and the guard that drops the API key on a cross-origin hop (X-01).
 *
 * PinePods' `/api/episodes/{id}/download` gets the long read timeout: it downloads the whole
 * episode from its origin before the first byte (16 s for 144 MB on the test server, APK-2.1).
 */
@OptIn(UnstableApi::class)
internal fun playbackNetworkDataSourceFactory(
    context: Context,
    isPinePodsDownload: (Uri) -> Boolean,
): DataSource.Factory = RoutingDataSource.Factory(
    primary = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(HttpClients.media)),
    alternate = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(HttpClients.mediaSlow)),
    useAlternate = isPinePodsDownload,
)
