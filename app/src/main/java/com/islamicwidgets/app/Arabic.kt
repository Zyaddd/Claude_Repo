package com.islamicwidgets.app

import java.time.DayOfWeek
import java.time.LocalTime

object Arabic {
    val weekdays = mapOf(
        DayOfWeek.SATURDAY to "السبت",
        DayOfWeek.SUNDAY to "الأحد",
        DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء",
        DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
    )

    val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    val gregorianMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    /** Converts Western digits to Arabic-Indic digits when [arabic] is true. */
    fun digits(s: String, arabic: Boolean): String {
        if (!arabic) return s
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') ('٠' + (c - '0')) else c)
        return sb.toString()
    }

    /** 12-hour clock without AM/PM, e.g. 5:15 or 12:08. */
    fun time12(t: LocalTime, arabic: Boolean): String {
        var h = t.hour % 12
        if (h == 0) h = 12
        return digits("%d:%02d".format(java.util.Locale.US, h, t.minute), arabic)
    }
}
