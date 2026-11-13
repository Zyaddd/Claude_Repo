package com.islamicwidgets.app

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date

/** The five daily prayers for one day, as instants. */
class DayTimes(val date: LocalDate, val times: List<Instant>)

/** Everything a widget needs about prayers at a moment in time. */
class PrayerState(
    val today: DayTimes,
    /** Index 0..4 of the next prayer (in [nextTimes]). */
    val nextIndex: Int,
    /** Instant of the next prayer (may be tomorrow's Fajr). */
    val nextAt: Instant,
    /** Instant of the previous prayer (may be yesterday's Isha). */
    val prevAt: Instant,
    /** Times shown in the row: today's, or tomorrow's after Isha. */
    val nextTimes: DayTimes,
) {
    fun progress(now: Instant): Float {
        val total = (nextAt.toEpochMilli() - prevAt.toEpochMilli()).toFloat()
        if (total <= 0f) return 0f
        return ((now.toEpochMilli() - prevAt.toEpochMilli()) / total).coerceIn(0f, 1f)
    }
}

object Prayer {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayTimes(prefs: Prefs, date: LocalDate): DayTimes {
        val method = runCatching { CalculationMethod.valueOf(prefs.method) }
            .getOrDefault(CalculationMethod.UMM_AL_QURA)
        val params = method.parameters
        params.madhab = if (prefs.hanafi) Madhab.HANAFI else Madhab.SHAFI
        val pt = PrayerTimes(
            Coordinates(prefs.latitude, prefs.longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params,
        )
        return DayTimes(date, listOf(pt.fajr, pt.dhuhr, pt.asr, pt.maghrib, pt.isha).map(Date::toInstant))
    }

    fun state(prefs: Prefs, now: Instant = Instant.now()): PrayerState {
        val todayDate = now.atZone(zone).toLocalDate()
        val today = dayTimes(prefs, todayDate)
        val idx = today.times.indexOfFirst { it.isAfter(now) }
        return if (idx >= 0) {
            val prev = if (idx > 0) today.times[idx - 1]
            else dayTimes(prefs, todayDate.minusDays(1)).times[4]
            PrayerState(today, idx, today.times[idx], prev, today)
        } else {
            val tomorrow = dayTimes(prefs, todayDate.plusDays(1))
            PrayerState(today, 0, tomorrow.times[0], today.times[4], tomorrow)
        }
    }

    fun localTime(i: Instant): LocalTime = i.atZone(zone).toLocalTime()

    /** Umm al-Qura Hijri date for [date] shifted by the user's adjustment. Returns (day, month 1..12, year). */
    fun hijri(prefs: Prefs, date: LocalDate = LocalDate.now()): Triple<Int, Int, Int> {
        val h = HijrahDate.from(date.plusDays(prefs.hijriAdjust.toLong()))
        return Triple(
            h.get(ChronoField.DAY_OF_MONTH),
            h.get(ChronoField.MONTH_OF_YEAR),
            h.get(ChronoField.YEAR),
        )
    }
}
