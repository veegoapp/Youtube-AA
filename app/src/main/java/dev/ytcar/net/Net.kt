package dev.ytcar.net

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * The ONLY allow-list of hosts this app may talk to. Every component that touches the network
 * (NewPipeExtractor, ExoPlayer, Coil, notification artwork) goes through [Net.client], so
 * anything else is refused before DNS resolution and before a connection is opened.
 */
object HostPolicy {
    /** Exact hostnames. */
    private val exact = setOf(
        "youtubei.googleapis.com",
        "yt3.ggpht.com",
        "yt4.ggpht.com",
        "yt3.googleusercontent.com",
    )

    /** The domain itself and any subdomain of it. */
    private val suffixes = listOf(
        "youtube.com",     // www, music, charts, suggestqueries-clients6 ...
        "googlevideo.com", // video/audio streams (rr1---sn-xxxx.googlevideo.com ...)
        "ytimg.com",       // thumbnails (i.ytimg.com, i9.ytimg.com ...)
    )

    fun allowed(rawHost: String): Boolean {
        val host = rawHost.lowercase().trimEnd('.')
        if (host.isEmpty()) return false
        if (host in exact) return true
        return suffixes.any { host == it || host.endsWith(".$it") }
    }
}

object Net {
    private val policyDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (!HostPolicy.allowed(hostname)) throw UnknownHostException("Blocked by host policy: $hostname")
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun check(url: HttpUrl) {
        if (!url.isHttps) throw IOException("Blocked by host policy: non-HTTPS request")
        if (!HostPolicy.allowed(url.host)) throw IOException("Blocked by host policy: ${url.host}")
    }

    private val guard = Interceptor { chain ->
        check(chain.request().url)
        chain.proceed(chain.request())
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(policyDns)                 // blocks before any DNS query / connection
        .followSslRedirects(false)      // never follow https -> http
        .addInterceptor(guard)          // initial request
        .addNetworkInterceptor(guard)   // every redirect hop (also catches IP-literal hosts)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
