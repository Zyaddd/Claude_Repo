package com.arabtv.iptv.data

/** A raw playlist entry, before entries of the same channel are merged. */
data class M3uEntry(
    val title: String,
    val url: String,
    val attrs: Map<String, String>,
    val userAgent: String?,
    val referrer: String?,
    val group: String?,
)

/**
 * Parses extended M3U playlists (the format used by iptv-org and by most IPTV providers):
 *
 *   #EXTINF:-1 tvg-id="X" tvg-logo="..." group-title="News",Channel Name (720p)
 *   #EXTVLCOPT:http-user-agent=...
 *   http://host/stream.m3u8
 */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        var title: String? = null
        var attrs: Map<String, String> = emptyMap()
        var ua: String? = null
        var ref: String? = null
        var group: String? = null

        fun reset() {
            title = null; attrs = emptyMap(); ua = null; ref = null; group = null
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    reset()
                    val body = line.substringAfter(':', "")
                    val comma = firstCommaOutsideQuotes(body)
                    val head = if (comma >= 0) body.substring(0, comma) else body
                    title = if (comma >= 0) body.substring(comma + 1).trim() else ""
                    attrs = attrRegex.findAll(head).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    ua = attrs["http-user-agent"]?.ifBlank { null }
                    ref = attrs["http-referrer"]?.ifBlank { null } ?: attrs["http-referer"]?.ifBlank { null }
                }
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val opt = line.substringAfter(':', "")
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim().ifBlank { null }
                    when (key) {
                        "http-user-agent" -> ua = value ?: ua
                        "http-referrer", "http-referer" -> ref = value ?: ref
                    }
                }
                line.startsWith("#EXTGRP", ignoreCase = true) -> group = line.substringAfter(':', "").trim().ifBlank { null }
                line.startsWith("#") -> Unit
                else -> {
                    if (StreamSource.isPlayableUrl(line)) {
                        val t = title?.ifBlank { null } ?: attrs["tvg-name"]?.ifBlank { null } ?: line
                        out += M3uEntry(t, line, attrs, ua, ref, attrs["group-title"]?.ifBlank { null } ?: group)
                    }
                    reset()
                }
            }
        }
        return out
    }

    private fun firstCommaOutsideQuotes(s: String): Int {
        var quoted = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }
}
