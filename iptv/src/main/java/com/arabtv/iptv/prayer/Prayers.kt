package com.arabtv.iptv.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date

/** A city for prayer times, with the calculation method its country uses officially. */
data class City(
    val id: String,
    val en: String,
    val ar: String,
    val lat: Double,
    val lng: Double,
    val method: CalculationMethod,
    val zone: String,
) {
    fun name(arabic: Boolean) = if (arabic) ar else en
}

/** One prayer: 0 Fajr, 1 Dhuhr, 2 Asr, 3 Maghrib, 4 Isha. */
data class PrayerTime(val index: Int, val at: Instant)

data class PrayerDay(val date: LocalDate, val times: List<PrayerTime>)

object Prayers {
    private val UQ = CalculationMethod.UMM_AL_QURA

    val CITIES = listOf(
        City("makkah", "Makkah", "مكة المكرمة", 21.4225, 39.8262, UQ, "Asia/Riyadh"),
        City("madinah", "Madinah", "المدينة المنورة", 24.4672, 39.6111, UQ, "Asia/Riyadh"),
        City("jeddah", "Jeddah", "جدة", 21.4858, 39.1925, UQ, "Asia/Riyadh"),
        City("thuwal", "Thuwal", "ثول", 22.3095, 39.1047, UQ, "Asia/Riyadh"),
        City("riyadh", "Riyadh", "الرياض", 24.7136, 46.6753, UQ, "Asia/Riyadh"),
        City("dammam", "Dammam", "الدمام", 26.4207, 50.0888, UQ, "Asia/Riyadh"),
        City("abha", "Abha", "أبها", 18.2164, 42.5053, UQ, "Asia/Riyadh"),
        City("tabuk", "Tabuk", "تبوك", 28.3835, 36.5662, UQ, "Asia/Riyadh"),
        City("cairo", "Cairo", "القاهرة", 30.0444, 31.2357, CalculationMethod.EGYPTIAN, "Africa/Cairo"),
        City("alexandria", "Alexandria", "الإسكندرية", 31.2001, 29.9187, CalculationMethod.EGYPTIAN, "Africa/Cairo"),
        City("dubai", "Dubai", "دبي", 25.2048, 55.2708, CalculationMethod.DUBAI, "Asia/Dubai"),
        City("abudhabi", "Abu Dhabi", "أبوظبي", 24.4539, 54.3773, CalculationMethod.DUBAI, "Asia/Dubai"),
        City("doha", "Doha", "الدوحة", 25.2854, 51.5310, CalculationMethod.QATAR, "Asia/Qatar"),
        City("kuwait", "Kuwait City", "الكويت", 29.3759, 47.9774, CalculationMethod.KUWAIT, "Asia/Kuwait"),
        City("manama", "Manama", "المنامة", 26.2285, 50.5860, UQ, "Asia/Bahrain"),
        City("muscat", "Muscat", "مسقط", 23.5880, 58.3829, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Muscat"),
        City("amman", "Amman", "عمّان", 31.9454, 35.9284, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Amman"),
        City("jerusalem", "Al-Quds", "القدس", 31.7683, 35.2137, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Jerusalem"),
        City("beirut", "Beirut", "بيروت", 33.8938, 35.5018, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Beirut"),
        City("damascus", "Damascus", "دمشق", 33.5138, 36.2765, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Damascus"),
        City("baghdad", "Baghdad", "بغداد", 33.3152, 44.3661, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Asia/Baghdad"),
        City("sanaa", "Sana'a", "صنعاء", 15.3694, 44.1910, UQ, "Asia/Aden"),
        City("khartoum", "Khartoum", "الخرطوم", 15.5007, 32.5599, CalculationMethod.EGYPTIAN, "Africa/Khartoum"),
        City("tripoli", "Tripoli", "طرابلس", 32.8872, 13.1913, CalculationMethod.EGYPTIAN, "Africa/Tripoli"),
        City("tunis", "Tunis", "تونس", 36.8065, 10.1815, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Tunis"),
        City("algiers", "Algiers", "الجزائر", 36.7538, 3.0588, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Algiers"),
        City("casablanca", "Casablanca", "الدار البيضاء", 33.5731, -7.5898, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Casablanca"),
        City("rabat", "Rabat", "الرباط", 34.0209, -6.8416, CalculationMethod.MUSLIM_WORLD_LEAGUE, "Africa/Casablanca"),
    )

    private val byZone = mapOf(
        "Asia/Riyadh" to "makkah", "Africa/Cairo" to "cairo", "Asia/Dubai" to "dubai", "Asia/Qatar" to "doha",
        "Asia/Kuwait" to "kuwait", "Asia/Bahrain" to "manama", "Asia/Muscat" to "muscat", "Asia/Amman" to "amman",
        "Asia/Beirut" to "beirut", "Asia/Damascus" to "damascus", "Asia/Baghdad" to "baghdad", "Asia/Aden" to "sanaa",
        "Africa/Khartoum" to "khartoum", "Africa/Tripoli" to "tripoli", "Africa/Tunis" to "tunis",
        "Africa/Algiers" to "algiers", "Africa/Casablanca" to "casablanca", "Asia/Jerusalem" to "jerusalem",
        "Asia/Gaza" to "jerusalem", "Asia/Hebron" to "jerusalem",
    )

    fun city(id: String?): City = CITIES.firstOrNull { it.id == id } ?: defaultCity()

    /** A city in the device's time zone, else Makkah. */
    fun defaultCity(zone: String = ZoneId.systemDefault().id): City =
        CITIES.first { it.id == (byZone[zone] ?: "makkah") }

    fun day(city: City, date: LocalDate): PrayerDay {
        val params = city.method.parameters
        params.madhab = Madhab.SHAFI
        val pt = PrayerTimes(
            Coordinates(city.lat, city.lng),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params,
        )
        val times = listOf(pt.fajr, pt.dhuhr, pt.asr, pt.maghrib, pt.isha)
            .mapIndexed { i, d: Date -> PrayerTime(i, d.toInstant()) }
        return PrayerDay(date, times)
    }

    fun today(city: City, now: Instant = Instant.now()): PrayerDay =
        day(city, now.atZone(ZoneId.of(city.zone)).toLocalDate())

    /** The next prayer after [now] (tomorrow's Fajr after Isha). */
    fun next(city: City, now: Instant = Instant.now()): PrayerTime {
        val d = today(city, now)
        return d.times.firstOrNull { it.at.isAfter(now) } ?: day(city, d.date.plusDays(1)).times[0]
    }

    /** The prayer whose time began within the last [withinMs], if any (for the adhan alert). */
    fun justStarted(city: City, now: Instant = Instant.now(), withinMs: Long = 3 * 60_000L): PrayerTime? =
        today(city, now).times.lastOrNull { !it.at.isAfter(now) && now.toEpochMilli() - it.at.toEpochMilli() <= withinMs }

    /** True during Ramadan (Umm al-Qura calendar) in the city's time zone. */
    fun isRamadan(city: City, now: Instant = Instant.now()): Boolean =
        HijrahDate.from(now.atZone(ZoneId.of(city.zone)).toLocalDate()).get(ChronoField.MONTH_OF_YEAR) == 9
}
