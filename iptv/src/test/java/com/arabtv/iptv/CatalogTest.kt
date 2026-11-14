package com.arabtv.iptv

import com.arabtv.iptv.data.Catalog
import com.arabtv.iptv.data.Category
import com.arabtv.iptv.data.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {
    private val sample = """
        #EXTM3U
        #EXTINF:-1 tvg-id="AlJazeera.qa@SD" tvg-logo="https://x/aj.png" group-title="News",Al Jazeera (1080p)
        https://live.example/aj/index.m3u8
        #EXTINF:-1 tvg-id="AlJazeera.qa@SD" tvg-logo="https://x/aj.png" group-title="News",Al Jazeera (720p)
        http://backup.example/aj.m3u8
        #EXTINF:-1 tvg-id="2MMonde.ma@SD" http-referrer="http://www.radio2m.ma/" group-title="General",2M Monde, Maroc (360p) [Geo-blocked]
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Test)
        https://cdn.example/2m.m3u8
        #EXTINF:-1 tvg-id="SpacetoonArabic.ae@SD" group-title="Animation;Kids",Spacetoon Arabic (576p)
        https://cdn.example/spacetoon.m3u8
        #EXTINF:-1 tvg-id="Ch4TeenDocumentary.kw@SD" group-title="Religious",Ch4Teen Documentary (1080p) [Not 24/7]
        https://cdn.example/ch4.m3u8
        #EXTINF:-1,No Attributes Channel
        ftp://not-allowed/stream
        #EXTINF:-1 group-title="أطفال",قناة كرتون
        https://cdn.example/kids.m3u8
    """.trimIndent()

    @Test
    fun parsesEntriesHeadersAndTitlesWithCommas() {
        val entries = M3uParser.parse(sample)
        assertEquals(6, entries.size) // the ftp:// entry is dropped
        val m2 = entries.first { it.attrs["tvg-id"] == "2MMonde.ma@SD" }
        assertEquals("2M Monde, Maroc (360p) [Geo-blocked]", m2.title)
        assertEquals("http://www.radio2m.ma/", m2.referrer)
        assertEquals("Mozilla/5.0 (Test)", m2.userAgent)
    }

    @Test
    fun mergesSourcesAndClassifies() {
        val names = mapOf("AlJazeera.qa" to "الجزيرة")
        val channels = Catalog.build(M3uParser.parse(sample), names)
        assertEquals(5, channels.size)

        val aj = channels.first { it.name == "Al Jazeera" }
        assertEquals(2, aj.sources.size)
        assertEquals("1080p", aj.sources[0].quality)
        assertEquals("الجزيرة", aj.displayName(arabic = true))
        assertEquals("QA", aj.country)
        assertEquals(setOf(Category.NEWS), aj.categories)

        val m2 = channels.first { it.name == "2M Monde, Maroc" }
        assertTrue(m2.geoBlocked)
        assertEquals(setOf(Category.OTHER), m2.categories)

        assertTrue(Category.KIDS in channels.first { it.name == "Spacetoon Arabic" }.categories)
        val ch4 = channels.first { it.name == "Ch4Teen Documentary" }
        assertTrue(Category.DOCUMENTARY in ch4.categories)
        assertTrue(ch4.notAlwaysOn)
        assertTrue(Category.KIDS in channels.first { it.name == "قناة كرتون" }.categories)
    }

    @Test
    fun singerNamedMajidIsNotACartoon() {
        assertFalse(Category.KIDS in Catalog.classify("Music", "Majid Al Mohandis"))
    }

    /** The bundled snapshot must give every requested section real channels. */
    @Test
    fun bundledCatalogCoversNewsDocumentaryAndKids() {
        val file = listOf("src/main/assets/ara.m3u", "iptv/src/main/assets/ara.m3u").map(::File).first { it.exists() }
        val channels = Catalog.build(M3uParser.parse(file.readText()), emptyMap())
        val news = channels.count { Category.NEWS in it.categories }
        val docs = channels.count { Category.DOCUMENTARY in it.categories }
        val kids = channels.count { Category.KIDS in it.categories }
        println("catalog: ${channels.size} channels, news=$news documentary=$docs kids=$kids")
        assertTrue("news=$news", news >= 50)
        assertTrue("docs=$docs", docs >= 5)
        assertTrue("kids=$kids", kids >= 8)
        listOf("Al Jazeera", "Alarabiya", "Sky News Arabia", "Asharq Documentary", "Spacetoon Arabic").forEach { n ->
            assertTrue("missing $n", channels.any { it.name.equals(n, ignoreCase = true) })
        }
        assertTrue(channels.all { ch -> ch.sources.all { it.url.startsWith("http") } })
    }
}
