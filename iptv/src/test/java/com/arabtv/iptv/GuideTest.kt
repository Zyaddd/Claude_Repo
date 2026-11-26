package com.arabtv.iptv

import com.arabtv.iptv.data.Category
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.StreamSource
import com.arabtv.iptv.data.XmlTv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="Al.Jazeera.qa"><display-name lang="ar">الجزيرة</display-name><icon src="x"/></channel>
          <channel id="MBC3.ae"><display-name>MBC 3</display-name></channel>
          <channel id="Unknown.xx"><display-name>Nobody</display-name></channel>
          <programme start="20261126180000 +0000" stop="20261126190000 +0000" channel="Al.Jazeera.qa">
            <title lang="ar">نشرة الأخبار</title><desc>…</desc>
          </programme>
          <programme start="20261126190000 +0000" stop="20261126200000 +0000" channel="Al.Jazeera.qa">
            <title>Inside Story</title>
          </programme>
          <programme start="20261120190000 +0000" stop="20261120200000 +0000" channel="MBC3.ae"><title>Old</title></programme>
          <programme start="20261126183000 +0000" stop="20261126193000 +0000" channel="MBC3.ae"><title>Cartoon time</title></programme>
          <programme start="20261126180000 +0000" stop="20261126190000 +0000" channel="Unknown.xx"><title>X</title></programme>
        </tv>"""

    private fun ch(id: String, name: String, tvg: String?) =
        Channel(id = id, name = name, tvgId = tvg, categories = setOf(Category.NEWS), sources = listOf(StreamSource("https://x/y.m3u8")))

    @Test
    fun keepsOnlyKnownChannelsInsideTheWindow() {
        val channels = listOf(ch("a", "Al Jazeera", "AlJazeera.qa"), ch("b", "MBC 3", "MBC3.ae"))
        val m = XmlTv.Matcher(channels)
        val from = 1795716000000L // 2026-11-26 18:00 UTC
        val guide = XmlTv.parse(xml.byteInputStream(), from, from + 6 * 3_600_000L, m::match)
        assertEquals(listOf("نشرة الأخبار", "Inside Story"), guide["a"]?.map { it.title })
        assertEquals(listOf("Cartoon time"), guide["b"]?.map { it.title })
        assertEquals(2, guide.size)
        assertNull(m.match("Unknown.xx", listOf("Nobody")))
    }
}
