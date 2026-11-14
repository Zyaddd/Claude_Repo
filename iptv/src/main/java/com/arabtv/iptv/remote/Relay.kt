package com.arabtv.iptv.remote

import com.arabtv.iptv.data.Http
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * Internet relay over an ntfy server (https://ntfy.sh by default, or a self-hosted one).
 * The TV keeps a streaming subscription open to a topic derived from its pairing code; the phone
 * publishes signed commands to that topic. Works across any two networks with internet access.
 */
object Relay {
    fun topicUrl(server: String, code: String) = server.trimEnd('/') + "/" + Protocol.topic(code)

    /** Phone side. Throws IOException if the relay did not accept the message. */
    fun publish(server: String, code: String, cmd: Command) {
        val c = Http.open(topicUrl(server, code), "POST", 10_000)
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            // Keep the relay from turning the message into a phone notification or e-mail.
            c.setRequestProperty("X-Priority", "1")
            val body = Protocol.seal(cmd, code).toByteArray(Charsets.UTF_8)
            if (body.size > 4000) throw IOException("Command too large for the relay")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code2 = c.responseCode
            if (code2 !in 200..299) throw IOException("Relay answered HTTP $code2")
        } finally {
            c.disconnect()
        }
    }

    /**
     * TV side. Blocks (suspending between reconnects) until the coroutine is cancelled, calling
     * [onEnvelope] with each message body. [current] holds the live connection so the caller can
     * close it to unblock a read on cancellation.
     */
    suspend fun subscribe(
        server: String,
        code: String,
        current: AtomicReference<HttpURLConnection?>,
        onState: (connected: Boolean) -> Unit,
        onEnvelope: (String) -> Unit,
    ) {
        var backoff = 2_000L
        while (coroutineContext.isActive) {
            try {
                val c = Http.open(topicUrl(server, code) + "/json", "GET", 15_000)
                // The server sends a keepalive every ~45 s; anything quieter than this is a dead link.
                c.readTimeout = 120_000
                current.set(c)
                try {
                    if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                    onState(true)
                    backoff = 2_000L
                    c.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (!coroutineContext.isActive) break
                            if (line.isBlank()) continue
                            val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                            if (o.optString("event") == "message") onEnvelope(o.optString("message"))
                        }
                    }
                } finally {
                    current.set(null)
                    c.disconnect()
                }
            } catch (e: Exception) {
                // Network change, relay restart, timeout: reconnect below.
            }
            onState(false)
            if (!coroutineContext.isActive) break
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }
}
