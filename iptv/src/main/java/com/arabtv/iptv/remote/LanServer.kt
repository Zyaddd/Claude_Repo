package com.arabtv.iptv.remote

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

/**
 * A tiny HTTP server the TV runs on the local network. Endpoints:
 *   GET  /info -> {"app":"arabtv","name":...,"v":1}
 *   POST /cmd  -> body is a [Command] as JSON
 */
class LanServer(
    private val deviceName: String,
    private val onCommand: (Command) -> Boolean,
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)

    /** Starts on the first free port in [ports]; returns the port, or -1 if none was free. */
    fun start(ports: IntRange): Int {
        for (port in ports) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(port))
                server = s
                Thread({ acceptLoop(s) }, "arabtv-lan").apply { isDaemon = true }.start()
                return port
            } catch (e: Exception) {
                Log.w(TAG, "port $port unavailable: ${e.message}")
            }
        }
        return -1
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        pool.shutdownNow()
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val client = try { s.accept() } catch (e: Exception) { break }
            try {
                pool.execute { handle(client) }
            } catch (e: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 5000
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return respond(sock, 400, """{"error":"bad request"}""")
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                var length = 0
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    val name = line.substringBefore(':').trim().lowercase()
                    if (name == "content-length") length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
                when {
                    method == "OPTIONS" -> respond(sock, 204, "")
                    method == "GET" && (path == "/info" || path == "/") -> respond(
                        sock, 200,
                        JSONObject().put("app", "arabtv").put("name", deviceName).put("v", Protocol.VERSION).toString(),
                    )
                    method == "POST" && path == "/cmd" -> {
                        if (length <= 0 || length > MAX_BODY) return respond(sock, 413, """{"error":"bad length"}""")
                        val body = ByteArray(length)
                        var read = 0
                        while (read < length) {
                            val n = input.read(body, read, length - read)
                            if (n < 0) break
                            read += n
                        }
                        val cmd = Command.parse(String(body, 0, read, Charsets.UTF_8))
                            ?: return respond(sock, 400, """{"error":"bad command"}""")
                        if (onCommand(cmd)) respond(sock, 200, """{"ok":true}""")
                        else respond(sock, 409, """{"error":"rejected"}""")
                    }
                    else -> respond(sock, 404, """{"error":"not found"}""")
                }
            } catch (e: SocketTimeoutException) {
                // Client went quiet; drop it.
            } catch (e: Exception) {
                Log.w(TAG, "request failed", e)
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun respond(sock: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 404 -> "Not Found"
            409 -> "Conflict"; 413 -> "Payload Too Large"; else -> "Error"
        }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Headers: Content-Type\r\n" +
            "Connection: close\r\n\r\n"
        val out = sock.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val TAG = "LanServer"
        private const val MAX_BODY = 64 * 1024
    }
}
