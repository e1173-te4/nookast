package ru.e1173.nookast.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.e1173.nookast.auth.ServerAddress

object ArtworkUrlResolver {
    data class Sources(
        val primaryUrl: String?,
        val fallbackUrl: String?,
    )

    /**
     * Keeps the RSS URL intact while preferring PinePods' image proxy.
     * The fallback must remain independent because the proxy and the source CDN
     * can fail separately.
     */
    fun sources(serverUrl: String, artworkUrl: String): Sources {
        val baseUrl = ServerAddress.normalize(serverUrl).getOrNull()
            ?: return Sources(null, null)
        val sourceUrl = resolveSource(baseUrl, artworkUrl) ?: return Sources(null, null)

        if (sourceUrl.scheme == baseUrl.scheme &&
            sourceUrl.host == baseUrl.host &&
            sourceUrl.port == baseUrl.port
        ) {
            return Sources(sourceUrl.toString(), null)
        }

        // A feed must not make the server fetch addresses inside its own network (X-09).
        if (isPrivateHost(sourceUrl.host)) return Sources(sourceUrl.toString(), null)

        val proxiedUrl = requireNotNull(baseUrl.resolve("/api/proxy/image"))
            .newBuilder()
            .addQueryParameter("url", sourceUrl.toString())
            .build()
            .toString()
        return Sources(
            primaryUrl = proxiedUrl,
            fallbackUrl = sourceUrl.toString(),
        )
    }

    fun sourcesForRequest(serverUrl: String, artworkUrl: String): Sources {
        val proxyFallback = fallbackFromProxiedUrl(artworkUrl)
        return if (proxyFallback != null && isPinePodsOriginRequest(serverUrl, artworkUrl)) {
            Sources(primaryUrl = artworkUrl, fallbackUrl = proxyFallback)
        } else {
            sources(serverUrl, artworkUrl)
        }
    }

    /** The key goes only to the image proxy of the configured server (X-09), never elsewhere. */
    fun isPinePodsImageProxyRequest(serverUrl: String, requestUrl: String): Boolean =
        isPinePodsOriginRequest(serverUrl, requestUrl) &&
            requestUrl.toHttpUrlOrNull()?.encodedPath == "/api/proxy/image"

    /** Loopback, link-local, private and `.local` hosts, by literal address or name. */
    internal fun isPrivateHost(host: String): Boolean {
        val name = host.lowercase().trim('[', ']')
        if (name == "localhost" || name.endsWith(".localhost") || name.endsWith(".local")) return true
        val v4 = name.split('.').takeIf { it.size == 4 }?.map { it.toIntOrNull() ?: return false }
        if (v4 != null) {
            val (a, b) = v4
            return a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
        }
        if (':' in name) {
            return name == "::1" || name.startsWith("fc") || name.startsWith("fd") || name.startsWith("fe80")
        }
        return false
    }

    /** API credentials may only be attached to the configured server origin. */
    fun isPinePodsOriginRequest(serverUrl: String, requestUrl: String): Boolean {
        val server = ServerAddress.normalize(serverUrl).getOrNull() ?: return false
        val request = requestUrl.toHttpUrlOrNull() ?: return false
        return request.scheme == server.scheme && request.host == server.host &&
            request.port == server.port
    }

    fun fallbackFromProxiedUrl(proxiedUrl: String): String? {
        val parsed = proxiedUrl.toHttpUrlOrNull() ?: return null
        if (parsed.encodedPath != "/api/proxy/image") return null
        return parsed.queryParameter("url")
            ?.toHttpUrlOrNull()
            ?.takeIf { it.scheme == "https" || it.scheme == "http" }
            ?.toString()
    }

    private fun resolveSource(baseUrl: HttpUrl, rawArtworkUrl: String): HttpUrl? {
        val value = rawArtworkUrl.trim()
        if (value.isEmpty()) return null
        return value.toHttpUrlOrNull()
            ?: baseUrl.resolve(value)
    }
}
