package com.islamicwidgets.app

import android.content.Context
import android.content.SharedPreferences

/** All user settings, stored in one SharedPreferences file. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var latitude: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("lat", java.lang.Double.doubleToRawLongBits(21.4858)))
        set(v) = sp.edit().putLong("lat", java.lang.Double.doubleToRawLongBits(v)).apply()

    var longitude: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("lng", java.lang.Double.doubleToRawLongBits(39.1925)))
        set(v) = sp.edit().putLong("lng", java.lang.Double.doubleToRawLongBits(v)).apply()

    var cityName: String
        get() = sp.getString("city", "جدة") ?: "جدة"
        set(v) = sp.edit().putString("city", v).apply()

    var locationChosen: Boolean
        get() = sp.getBoolean("locationChosen", false)
        set(v) = sp.edit().putBoolean("locationChosen", v).apply()

    /** Name of a com.batoulapps.adhan.CalculationMethod constant. */
    var method: String
        get() = sp.getString("method", "UMM_AL_QURA") ?: "UMM_AL_QURA"
        set(v) = sp.edit().putString("method", v).apply()

    var hanafi: Boolean
        get() = sp.getBoolean("hanafi", false)
        set(v) = sp.edit().putBoolean("hanafi", v).apply()

    /** Days added to the computed Umm al-Qura Hijri date (-2..+2). */
    var hijriAdjust: Int
        get() = sp.getInt("hijriAdjust", 0)
        set(v) = sp.edit().putInt("hijriAdjust", v).apply()

    var arabicDigits: Boolean
        get() = sp.getBoolean("arabicDigits", true)
        set(v) = sp.edit().putBoolean("arabicDigits", v).apply()

    /** Glass fill opacity in percent (5..60). */
    var glassOpacity: Int
        get() = sp.getInt("glassOpacity", 24)
        set(v) = sp.edit().putInt("glassOpacity", v).apply()

    /** 0 = dark glass, 1 = light glass, 2 = tinted from wallpaper. */
    var glassTint: Int
        get() = sp.getInt("glassTint", 0)
        set(v) = sp.edit().putInt("glassTint", v).apply()

    /** 0 = imported font (Thuluth), 1 = Ruqaa, 2 = Naskh (Amiri), 3 = Kufi. */
    var headingFont: Int
        get() = sp.getInt("headingFont", 1)
        set(v) = sp.edit().putInt("headingFont", v).apply()

    var importedFontName: String?
        get() = sp.getString("importedFontName", null)
        set(v) = sp.edit().putString("importedFontName", v).apply()

    var tasbihCount: Int
        get() = sp.getInt("tasbih", 0)
        set(v) = sp.edit().putInt("tasbih", v).apply()

    /** Extra offset added to the daily verse index when the user taps the widget. */
    var ayahOffset: Int
        get() = sp.getInt("ayahOffset", 0)
        set(v) = sp.edit().putInt("ayahOffset", v).apply()
}
