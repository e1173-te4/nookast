package ru.e1173.nookast.player

import ru.e1173.nookast.core.network.HttpClients
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class StreamReachability {
    IDLE,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    LOCAL,
}

data class EndpointProbeResult(
    val requestedHost: String,
    val resolvedHost: String,
    val isAvailable: Boolean,
    val responseCode: Int?,
    val latencyMs: Long,
)

class StreamReachabilityClient(
    private val httpClient: OkHttpClient = HttpClients.public.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    fun probe(
        rawUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): EndpointProbeResult {
        val url = rawUrl.toHttpUrlOrNull()
        if (url == null || (url.scheme != "https" && url.scheme != "http")) {
            return EndpointProbeResult(
                requestedHost = "",
                resolvedHost = "",
                isAvailable = false,
                responseCode = null,
                latencyMs = 0,
            )
        }

        val startedAt = System.nanoTime()
        return try {
            // Authenticated probes never follow redirects, so a PinePods API key
            // cannot be forwarded to a third-party host.
            val probeClient = if (requestHeaders.isEmpty()) {
                httpClient
            } else {
                httpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }
            probeClient.newCall(buildProbeRequest(rawUrl, requestHeaders)).execute().use { response ->
                EndpointProbeResult(
                    requestedHost = url.host,
                    resolvedHost = response.request.url.host,
                    isAvailable = isReachableResponseCode(response.code),
                    responseCode = response.code,
                    latencyMs = elapsedMilliseconds(startedAt),
                )
            }
        } catch (_: IOException) {
            EndpointProbeResult(
                requestedHost = url.host,
                resolvedHost = url.host,
                isAvailable = false,
                responseCode = null,
                latencyMs = elapsedMilliseconds(startedAt),
            )
        }
    }

    internal fun buildProbeRequest(
        rawUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): Request = Request.Builder()
        .url(rawUrl)
        .header("Range", "bytes=0-0")
        .header("Accept-Encoding", "identity")
        .header("Cache-Control", "no-cache")
        .apply {
            requestHeaders.forEach { (name, value) -> header(name, value) }
        }
        .get()
        .build()

    private fun elapsedMilliseconds(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).coerceAtLeast(0)

    companion object {
        /** Android's own connectivity check: answers 204 without content. */
        const val CONNECTIVITY_CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204"
        const val YANDEX_CHECK_URL = "https://ya.ru/"
        private const val PROBE_TIMEOUT_SECONDS = 6L

        internal fun isReachableResponseCode(code: Int): Boolean = code in 200..299
    }
}
