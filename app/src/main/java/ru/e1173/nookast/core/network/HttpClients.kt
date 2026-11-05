package ru.e1173.nookast.core.network

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Process-wide HTTP stack. Every client derives from one base [OkHttpClient] via newBuilder(), so
 * they share one connection pool, dispatcher and TLS session cache (previously ~13 independent
 * clients, finding N-08).
 */
object HttpClients {
    const val API_KEY_HEADER = "Api-Key"

    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addNetworkInterceptor(CredentialOriginGuard)
            .build()
    }

    /** Authenticated PinePods JSON API: never follows redirects (the key must not move hosts). */
    val pinePods: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** Playback streams: redirects allowed; the origin guard strips the key on a foreign hop. */
    val media: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** PinePods' on-demand download: the first byte comes after the server fetched everything. */
    val mediaSlow: OkHttpClient by lazy {
        media.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** Public feeds and other unauthenticated resources: redirects allowed. */
    val public: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Artwork downloads; [followRedirects] is false for requests that carry the API key. */
    fun artwork(followRedirects: Boolean): OkHttpClient =
        if (followRedirects) artworkPublic else artworkPinePods

    /** Short-timeout reachability and media-metadata probes. */
    fun probe(followRedirects: Boolean): OkHttpClient =
        if (followRedirects) probePublic else probePinePods

    private val artworkPublic by lazy { artworkBuilder(true) }
    private val artworkPinePods by lazy { artworkBuilder(false) }
    private val probePublic by lazy { probeBuilder(true) }
    private val probePinePods by lazy { probeBuilder(false) }

    private fun artworkBuilder(followRedirects: Boolean) = base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(followRedirects)
        .followSslRedirects(followRedirects)
        .build()

    private fun probeBuilder(followRedirects: Boolean) = base.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(followRedirects)
        .followSslRedirects(followRedirects)
        .build()
}

/**
 * Defense in depth for finding X-01: a network interceptor sees every hop, including redirects.
 * If a hop targets a different origin (scheme, host or port) than the request the caller made,
 * the API key header is removed before it leaves the device.
 */
internal object CredentialOriginGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val original = chain.call().request().url
        if (request.header(HttpClients.API_KEY_HEADER) != null && !sameOrigin(original, request.url)) {
            return chain.proceed(request.newBuilder().removeHeader(HttpClients.API_KEY_HEADER).build())
        }
        return chain.proceed(request)
    }

    internal fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host.equals(b.host, ignoreCase = true) && a.port == b.port
}
