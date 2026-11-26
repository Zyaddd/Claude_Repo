package com.arabtv.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/** What's on now and next, from free XMLTV guides; cached for 12 hours. */
class GuideRepository(private val context: Context) {
    private val _guide = MutableStateFlow<Map<String, List<Programme>>>(emptyMap())
    val guide: StateFlow<Map<String, List<Programme>>> = _guide.asStateFlow()

    private val cache get() = File(context.filesDir, "guide.json")

    suspend fun load(channels: List<Channel>, urls: List<String>, force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force) {
            readCache(now)?.let { (savedAt, data) ->
                _guide.value = data
                if (now - savedAt < MAX_AGE) return@withContext
            }
        }
        if (channels.isEmpty() || urls.isEmpty()) return@withContext
        val matcher = XmlTv.Matcher(channels)
        val merged = HashMap<String, List<Programme>>()
        var anyOk = false
        for (url in urls) {
            try {
                val c = Http.open(url, "GET", 30_000)
                try {
                    if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                    val raw = BufferedInputStream(c.inputStream, 64 * 1024)
                    raw.mark(4)
                    val gz = raw.read() == 0x1f && raw.read() == 0x8b
                    raw.reset()
                    val input = if (gz) GZIPInputStream(raw, 64 * 1024) else raw
                    input.use {
                        val part = XmlTv.parse(it, now - HOUR, now + WINDOW, matcher::match)
                        for ((id, list) in part) merged.merge(id, list) { a, b -> (a + b).distinctBy { p -> p.start }.sortedBy { p -> p.start } }
                    }
                    anyOk = true
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                // One guide failing is fine; the others still fill in.
            }
        }
        if (anyOk) {
            _guide.value = merged
            writeCache(now, merged)
        }
    }

    private fun readCache(now: Long): Pair<Long, Map<String, List<Programme>>>? = runCatching {
        val o = JSONObject(cache.readText())
        val savedAt = o.getLong("savedAt")
        val data = o.getJSONObject("data")
        val map = HashMap<String, List<Programme>>()
        for (id in data.keys()) {
            val a = data.getJSONArray(id)
            map[id] = (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                Programme(p.getString("t"), p.getLong("s"), p.getLong("e"))
            }.filter { it.stop > now }
        }
        savedAt to map
    }.getOrNull()

    private fun writeCache(now: Long, data: Map<String, List<Programme>>) = runCatching {
        val d = JSONObject()
        for ((id, list) in data) {
            d.put(id, JSONArray(list.map { JSONObject().put("t", it.title).put("s", it.start).put("e", it.stop) }))
        }
        cache.writeText(JSONObject().put("savedAt", now).put("data", d).toString())
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val WINDOW = 18 * HOUR
        private const val MAX_AGE = 12 * HOUR

        fun nowAndNext(list: List<Programme>?, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
            if (list.isNullOrEmpty()) return null to null
            val i = list.indexOfFirst { it.start <= now && it.stop > now }
            return if (i >= 0) list[i] to list.getOrNull(i + 1) else null to list.firstOrNull { it.start > now }
        }
    }
}
