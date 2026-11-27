package com.arabtv.iptv.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lets the TV play a video stored on this phone: serves shared files over HTTP on the local
 * network, with Range support so the TV can seek. Each file gets an unguessable link; nothing
 * else on the phone is reachable.
 */
class MediaServer(private val context: Context) {
    private data class Shared(val uri: Uri, val mime: String, val size: Long)

    private val files = ConcurrentHashMap<String, Shared>()
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    @Volatile var lastRequestAt = 0L
        private set

    val isRunning: Boolean get() = server?.isClosed == false

    /**
     * Makes [uri] available and returns the link the TV can open, e.g.
     * http://192.168.0.102:8650/media/<token>/video.mp4. Null when the phone has no local network.
     */
    @Synchronized
    fun share(uri: Uri, displayName: String): String? {
        val ip = Net.localIpv4(context) ?: return null
        val port = ensureStarted()
        if (port <= 0) return null
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        files[token] = Shared(uri, mime, sizeOf(uri))
        lastRequestAt = System.currentTimeMillis()
        val ext = extensionFor(mime, displayName)
        return "http://$ip:$port/media/$token/video.$ext"
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        files.clear()
    }

    private fun ensureStarted(): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        for (port in PORTS) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(port))
                server = s
                Thread({ acceptLoop(s) }, "arabtv-media").apply { isDaemon = true }.start()
                return port
            } catch (e: Exception) {
                Log.w(TAG, "port $port busy")
            }
        }
        return -1
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val client = try { s.accept() } catch (e: Exception) { break }
            runCatching { pool.execute { handle(client) } }.onFailure { runCatching { client.close() } }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 30_000
                val input = BufferedInputStream(sock.getInputStream())
                val parts = readLine(input)?.split(' ') ?: return
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                var range: String? = null
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    if (line.substringBefore(':').trim().equals("range", ignoreCase = true)) {
                        range = line.substringAfter(':').trim()
                    }
                }
                val segments = parts[1].substringBefore('?').trim('/').split('/')
                val shared = if (segments.size >= 2 && segments[0] == "media") files[segments[1]] else null
                val out = sock.getOutputStream()
                if (shared == null || (method != "GET" && method != "HEAD")) {
                    head(out, "404 Not Found", "text/plain", 0, emptyList())
                    return
                }
                lastRequestAt = System.currentTimeMillis()
                serve(out, shared, method == "HEAD", range)
            } catch (e: Exception) {
                // The TV closes connections when it seeks or stops; that is normal.
            }
        }
    }

    private fun serve(out: OutputStream, f: Shared, headOnly: Boolean, range: String?) {
        val size = f.size
        var start = 0L
        var end = if (size > 0) size - 1 else -1L
        var partial = false
        if (range != null && size > 0) {
            val m = Regex("""bytes=(\d*)-(\d*)""").find(range)
            if (m != null) {
                val a = m.groupValues[1]
                val b = m.groupValues[2]
                if (a.isEmpty() && b.isNotEmpty()) {
                    start = (size - b.toLong()).coerceAtLeast(0)
                } else {
                    start = a.toLongOrNull() ?: 0L
                    if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1)
                }
                if (start > end) {
                    head(out, "416 Range Not Satisfiable", f.mime, 0, listOf("Content-Range: bytes */$size"))
                    return
                }
                partial = true
            }
        }
        val length = if (size > 0) end - start + 1 else -1L
        val extra = mutableListOf("Accept-Ranges: bytes")
        if (partial) extra += "Content-Range: bytes $start-$end/$size"
        head(out, if (partial) "206 Partial Content" else "200 OK", f.mime, length, extra)
        if (headOnly) return
        open(f.uri, start).use { input ->
            val buf = ByteArray(64 * 1024)
            var left = if (length >= 0) length else Long.MAX_VALUE
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
            out.flush()
        }
    }

    /** Opens the file at [start]; seeks directly when the provider gives a real file. */
    private fun open(uri: Uri, start: Long): InputStream {
        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            val fis = ParcelInput(pfd)
            runCatching { fis.channel.position(start) }.onSuccess { return fis }
            fis.close()
        }
        val s = context.contentResolver.openInputStream(uri) ?: error("cannot open")
        var skipped = 0L
        while (skipped < start) {
            val k = s.skip(start - skipped)
            if (k <= 0) break
            skipped += k
        }
        return s
    }

    /** A FileInputStream that also closes its ParcelFileDescriptor. */
    private class ParcelInput(private val pfd: android.os.ParcelFileDescriptor) : FileInputStream(pfd.fileDescriptor) {
        override fun close() {
            super.close()
            runCatching { pfd.close() }
        }
    }

    private fun head(out: OutputStream, status: String, mime: String, length: Long, extra: List<String>) {
        val sb = StringBuilder("HTTP/1.1 $status\r\nContent-Type: $mime\r\n")
        if (length >= 0) sb.append("Content-Length: $length\r\n")
        extra.forEach { sb.append(it).append("\r\n") }
        sb.append("Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun sizeOf(uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        }
        return runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
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

    companion object {
        private const val TAG = "MediaServer"
        private val PORTS = 8650..8660

        fun extensionFor(mime: String, name: String): String = when {
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.contains("matroska") -> "mkv"
            mime.contains("quicktime") -> "mov"
            mime.contains("3gpp") -> "3gp"
            mime.startsWith("audio/mpeg") -> "mp3"
            else -> name.substringAfterLast('.', "mp4").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "mp4" }
        }
    }
}
