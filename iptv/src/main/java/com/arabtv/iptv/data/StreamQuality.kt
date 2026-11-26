package com.arabtv.iptv.data

/** Result of checking one link: whether it answers, and the best resolution it offers (0 = unknown). */
data class ProbeResult(val alive: Boolean, val height: Int = 0)

object StreamQuality {
    private val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
    private val bandwidth = Regex("""[^-]BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Highest vertical resolution an HLS master playlist offers. Variants without a RESOLUTION tag
     * are estimated from their bitrate. Returns 0 for a media playlist or anything else (alive,
     * but the resolution cannot be told without decoding video).
     */
    fun maxHeight(playlist: String): Int {
        if (!playlist.trimStart().startsWith("#EXTM3U")) return 0
        var best = 0
        for (line in playlist.lineSequence()) {
            if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue
            val h = resolution.find(line)?.groupValues?.get(2)?.toIntOrNull()
                ?: bandwidth.find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { heightForBitrate(it) }
                ?: 0
            if (h > best) best = h
        }
        return best
    }

    /** Rough resolution for a variant that only states its bitrate. */
    fun heightForBitrate(bps: Long): Int = when {
        bps >= 4_500_000 -> 1080
        bps >= 2_000_000 -> 720
        bps >= 1_000_000 -> 540
        bps >= 600_000 -> 360
        else -> 240
    }

    /**
     * Orders a channel's links: working ones first, highest measured resolution first (falling
     * back to what the playlist claims), then the original order. Dead links go last.
     */
    fun rank(sources: List<StreamSource>, results: Map<String, ProbeResult>): List<StreamSource> =
        sources.withIndex().sortedWith(
            compareBy<IndexedValue<StreamSource>>(
                { if (results[it.value.url]?.alive == false) 1 else 0 },
                { -(results[it.value.url]?.height?.takeIf { h -> h > 0 } ?: Catalog.declaredHeight(it.value)) },
                { it.index },
            ),
        ).map { it.value }
}
