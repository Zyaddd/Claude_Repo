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
    fun probe(src: StreamSource, timeoutMs: Int = 8000): Boolean {
        var url = src.url
        repeat(5) {
            val c = try {
                open(url, "GET", timeoutMs, src.userAgent, src.referrer).apply { instanceFollowRedirects = false }
            } catch (e: Exception) {
                return false
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: return false
                    url = URL(URL(url), next).toString()
                    return@repeat
                }
                if (code !in 200..299) return false
                // Read a little so servers that answer 200 with an empty body count as dead.
                val buf = ByteArray(64)
                return c.inputStream.use { it.read(buf) > 0 }
            } catch (e: Exception) {
                return false
            } finally {
                c.disconnect()
            }
        }
        return false
    }
}
