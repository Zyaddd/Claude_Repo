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

    private fun asset(name: String) =
        listOf("src/main/assets/$name", "iptv/src/main/assets/$name").map(::File).first { it.exists() }.readText()

    /** Both bundled lists together: no channel twice, Nat Geo Abu Dhabi present, Free-TV backups merged in. */
    @Test
    fun mergedCatalogHasNoDuplicatesAndNatGeo() {
        val ara = Catalog.build(M3uParser.parse(asset("ara.m3u")), emptyMap())
        val free = Catalog.build(
            M3uParser.parse(asset("freetv_arab.m3u")).filter { com.arabtv.iptv.data.ChannelRepository.keepFreeTv(it) },
            emptyMap(),
        )
        val merged = Catalog.merge(listOf(ara, free))
        println("ara=${ara.size} freetv=${free.size} merged=${merged.size}")
        val dupes = merged.groupBy { Catalog.normalizedName(it.name) }.filter { it.value.size > 1 }
        assertTrue("duplicates: ${dupes.keys}", dupes.isEmpty())

        val natGeo = merged.single { it.name == "National Geographic Abu Dhabi" }
        assertTrue(Category.DOCUMENTARY in natGeo.categories)
        assertTrue(natGeo.sources.any { it.url.contains("mangomolo.com/nagtv") })

        // Al Jazeera is in both lists under different ids: one channel with both lists' links.
        val aj = merged.filter { Catalog.normalizedName(it.name) == "aljazeera" }
        assertEquals(1, aj.size)
        assertTrue(aj[0].id.startsWith("id:AlJazeera.qa@"))
        assertTrue(aj[0].sources.size >= 2)

        assertTrue(merged.any { it.name == "MBC 3" && Category.KIDS in it.categories })
        val kids = merged.count { Category.KIDS in it.categories }
        val docs = merged.count { Category.DOCUMENTARY in it.categories }
        val news = merged.count { Category.NEWS in it.categories }
        println("news=$news docs=$docs kids=$kids")
        assertFalse(merged.any { it.name.contains("Persia") || it.name.contains('\u24C8') })
    }

    @Test
    fun normalizedNamesMatchAcrossSpellings() {
        assertEquals("aljazeera", Catalog.normalizedName("Al Jazeera العربية"))
        assertEquals("aljazeera", Catalog.normalizedName("Al-Jazeera HD"))
        assertEquals(Catalog.normalizedName("Sky News Arabia (1080p)"), Catalog.normalizedName("Sky News العربية"))
        assertEquals(Catalog.normalizedName("France 24 Arabic"), Catalog.normalizedName("France 24 العربية Ⓨ"))
        assertTrue(Catalog.normalizedName("Al Jazeera Mubasher") != Catalog.normalizedName("Al Jazeera"))
    }

    @Test
    fun quranChannelsUseOfficialLinksAndSkipTheMislabelledOne() {
        val repoSources = com.arabtv.iptv.data.ChannelRepository.SOURCES
        val lists = repoSources.map { src ->
            Catalog.build(M3uParser.parse(asset(src.asset)).filter(src.keep), emptyMap())
        }
        val merged = Catalog.merge(lists)
        val haram = merged.filter { it.tvgId == "AlQuranAlKareemTV.sa" }
        assertEquals(1, haram.size)
        assertEquals("id:AlQuranAlKareemTV.sa@SD", haram[0].id) // same id as before, favorites keep working
        assertTrue(haram[0].sources.first().url.contains("m.live.net.sa"))
        assertFalse(haram[0].sources.any { it.url.contains("al-ekhbaria") })
        assertTrue(Category.QURAN in haram[0].categories)

        val nabawi = merged.single { it.tvgId == "AlSunnahAlNabawiyahTV.sa" }
        assertTrue(nabawi.sources.size >= 3)
        assertTrue(Category.QURAN in nabawi.categories)

        val quran = merged.filter { Category.QURAN in it.categories }
        println("quran=${quran.size}: " + quran.joinToString { it.name })
        // Christian channels share the "Religious" group but are not Quran channels.
        assertFalse(quran.any { it.name.contains("Aghapy") || it.name.contains("Coptic") || it.name.contains("Noursat", true) })
    }
}
