package com.islamicwidgets.app

import android.content.Context
import android.graphics.Typeface
import java.io.File

object Fonts {
    private val cache = HashMap<Int, Typeface>()
    private var imported: Typeface? = null
    private var importedLoaded = false

    fun importedFile(ctx: Context) = File(ctx.filesDir, "heading_font.ttf")

    private fun res(ctx: Context, id: Int): Typeface =
        cache.getOrPut(id) { ctx.resources.getFont(id) }

    fun kufi(ctx: Context) = res(ctx, R.font.noto_kufi_regular)
    fun kufiBold(ctx: Context) = res(ctx, R.font.noto_kufi_semibold)
    fun amiri(ctx: Context) = res(ctx, R.font.amiri_regular)
    fun amiriBold(ctx: Context) = res(ctx, R.font.amiri_bold)
    fun quran(ctx: Context) = res(ctx, R.font.amiri_quran)
    fun ruqaa(ctx: Context) = res(ctx, R.font.aref_ruqaa_bold)

    fun imported(ctx: Context): Typeface? {
        if (!importedLoaded) {
            importedLoaded = true
            val f = importedFile(ctx)
            imported = if (f.exists()) Typeface.Builder(f).build() else null
        }
        return imported
    }

    fun clearImported() {
        importedLoaded = false
        imported = null
    }

    /** Typeface for the large calligraphic words (weekday, prayer name). */
    fun heading(ctx: Context, prefs: Prefs): Typeface = when (prefs.headingFont) {
        0 -> imported(ctx) ?: ruqaa(ctx)
        2 -> amiriBold(ctx)
        3 -> res(ctx, R.font.reem_kufi)
        else -> ruqaa(ctx)
    }
}
