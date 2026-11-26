package com.arabtv.iptv.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Blocking GET returning the body as text. Throws IOException on failure or non-2xx. */
    fun getText(url: String, maxBytes: Int = 40 shl 20, timeoutMs: Int = 20_000): String {
        val c = open(url, "GET", timeoutMs)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("Response too large")
                }
                return out.toString("UTF-8")
            }
        } finally {
            c.disconnect()
        }
    }

    fun open(
        url: String,
        method: String,
        timeoutMs: Int,
        userAgent: String? = null,
        referrer: String? = null,
    ): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", userAgent ?: USER_AGENT)
        referrer?.let { c.setRequestProperty("Referer", it) }
        return c
    }

    /**
     * Quick liveness check for a stream: the URL must answer 2xx (following redirects, including
     * http<->https ones which HttpURLConnection does not follow by itself).
     */
    fun probe(src: StreamSource, timeoutMs: Int = 8000): Boolean = probeQuality(src, timeoutMs).alive

    /**
     * Like [probe], and for HLS links also reads the master playlist to find the best resolution
     * on offer, so channels can play their sharpest working link first.
     */
    fun probeQuality(src: StreamSource, timeoutMs: Int = 8000): ProbeResult {
        var url = src.url
        repeat(5) {
            val c = try {
                open(url, "GET", timeoutMs, src.userAgent, src.referrer).apply { instanceFollowRedirects = false }
            } catch (e: Exception) {
                return ProbeResult(false)
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: return ProbeResult(false)
                    url = URL(URL(url), next).toString()
                    return@repeat
                }
                if (code !in 200..299) return ProbeResult(false)
                // Read the start of the body: an empty answer counts as dead; an HLS master
                // playlist tells us the resolutions on offer.
                val buf = ByteArray(32 * 1024)
                var n = 0
                c.inputStream.use { input ->
                    while (n < buf.size) {
                        val r = input.read(buf, n, buf.size - n)
                        if (r < 0) break
                        n += r
                        // A video stream (not a playlist): the first bytes are enough.
                        if (n >= 64 && buf[0] != '#'.code.toByte()) break
                    }
                }
                if (n <= 0) return ProbeResult(false)
                val text = if (buf[0] == '#'.code.toByte()) String(buf, 0, n, Charsets.UTF_8) else ""
                return ProbeResult(true, StreamQuality.maxHeight(text))
            } catch (e: Exception) {
                return ProbeResult(false)
            } finally {
                c.disconnect()
            }
        }
        return ProbeResult(false)
    }
}
