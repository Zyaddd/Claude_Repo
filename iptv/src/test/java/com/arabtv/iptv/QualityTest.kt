package com.arabtv.iptv

import com.arabtv.iptv.data.Catalog
import com.arabtv.iptv.data.M3uParser
import com.arabtv.iptv.data.ProbeResult
import com.arabtv.iptv.data.StreamQuality
import com.arabtv.iptv.data.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QualityTest {
    @Test
    fun readsTheBestResolutionFromAMasterPlaylist() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028"
            high.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            mid.m3u8
        """.trimIndent()
        assertEquals(1080, StreamQuality.maxHeight(master))
        // Variants without RESOLUTION are estimated from their bitrate.
        assertEquals(720, StreamQuality.maxHeight("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2400000\na.m3u8"))
        // A media playlist (segments only) does not reveal its resolution.
        assertEquals(0, StreamQuality.maxHeight("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts"))
        assertEquals(0, StreamQuality.maxHeight("not a playlist"))
    }

    @Test
    fun ranksWorkingSharpLinksFirstAndDeadLast() {
        val a = StreamSource("https://a/x.m3u8", quality = "720p")
        val b = StreamSource("https://b/x.m3u8", quality = "360p")
        val c = StreamSource("https://c/x.m3u8")
        val d = StreamSource("https://d/x.m3u8", quality = "1080p")
        val probes = mapOf(
            a.url to ProbeResult(true, 720),
            b.url to ProbeResult(true, 1080), // the playlist undersold it
            c.url to ProbeResult(true, 0),
            d.url to ProbeResult(false),
        )
        assertEquals(listOf(b, a, c, d), StreamQuality.rank(listOf(a, b, c, d), probes))
        // Nothing measured yet: declared resolutions decide.
        assertEquals(listOf(d, a, c, b), StreamQuality.rank(listOf(a, b, c, d), emptyMap()))
    }

    @Test
    fun countryStreamFilesOnlyAddLinks() {
        fun asset(n: String) = listOf("src/main/assets/$n", "iptv/src/main/assets/$n").map(::File).first { it.exists() }.readText()
        val main = Catalog.build(M3uParser.parse(asset("ara.m3u")), emptyMap())
        val extra = Catalog.build(M3uParser.parse(asset("iptvorg_streams.m3u")), emptyMap())
        val merged = Catalog.merge(listOf(main, extra), linksOnly = setOf(1))
        val alone = Catalog.merge(listOf(main))
        assertEquals(alone.map { it.id }, merged.map { it.id }) // no channel added or lost
        val before = alone.sumOf { it.sources.size }
        val after = merged.sumOf { it.sources.size }
        println("links: $before -> $after")
        assertTrue(after > before + 50)
        val aj = merged.first { it.tvgId == "AlJazeera.qa" }
        assertTrue(aj.sources.size >= 5)
    }
}
