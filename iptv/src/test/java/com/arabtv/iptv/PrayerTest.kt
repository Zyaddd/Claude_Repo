package com.arabtv.iptv

import com.arabtv.iptv.prayer.Prayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PrayerTest {
    private val makkah = Prayers.city("makkah")

    @Test
    fun makkahTimesAreInOrderAndPlausible() {
        val day = Prayers.day(makkah, LocalDate.of(2026, 11, 27))
        val local = day.times.map { it.at.atZone(ZoneId.of("Asia/Riyadh")).toLocalTime() }
        println("Makkah 2026-11-27: $local")
        assertEquals(5, local.size)
        assertTrue(local.zipWithNext().all { (a, b) -> a < b })
        assertEquals(5, local[0].hour) // Fajr around 05:20
        assertEquals(17, local[3].hour) // Maghrib around 17:40 in late November
    }

    @Test
    fun nextPrayerRollsOverToTomorrowsFajr() {
        val lateNight = Instant.parse("2026-11-27T21:30:00Z") // 00:30 in Makkah, before Fajr
        assertEquals(0, Prayers.next(makkah, lateNight).index)
        val afternoon = Instant.parse("2026-11-27T11:00:00Z") // 14:00 Makkah: next is Asr
        assertEquals(2, Prayers.next(makkah, afternoon).index)
    }

    @Test
    fun adhanWindowAndRamadan() {
        val dhuhr = Prayers.day(makkah, LocalDate.of(2026, 11, 27)).times[1].at
        assertEquals(1, Prayers.justStarted(makkah, dhuhr.plusSeconds(60))?.index)
        assertEquals(null, Prayers.justStarted(makkah, dhuhr.plusSeconds(600)))
        assertTrue(Prayers.isRamadan(makkah, Instant.parse("2026-03-01T12:00:00Z"))) // Ramadan 1447
        assertFalse(Prayers.isRamadan(makkah, Instant.parse("2026-11-27T12:00:00Z")))
    }

    @Test
    fun defaultCityFollowsTimeZone() {
        assertEquals("cairo", Prayers.defaultCity("Africa/Cairo").id)
        assertEquals("makkah", Prayers.defaultCity("Asia/Riyadh").id)
        assertEquals("makkah", Prayers.defaultCity("Europe/London").id)
    }
}
