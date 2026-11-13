package com.islamicwidgets.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Renders every widget with Android's real text engine (Robolectric native graphics) across
 * sizes, font scales, heading fonts and styles. Fails if any text leaves the widget or overlaps
 * other text or the countdown. Saves contact sheets to build/snapshots for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderLayoutTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(ctx)
    private val now = Instant.parse("2026-11-13T19:40:00Z")
    private val today = LocalDate.of(2026, 11, 13)

    private val sizes = listOf(
        340f to 200f, 340f to 170f, 300f to 150f, 250f to 110f, // home screen
        170f to 170f, 150f to 150f, 160f to 110f, 340f to 80f, 110f to 110f, // lock screen / small
    )
    private val scales = listOf(70, 100, 150)
    private val fonts = listOf(1, 2, 3) // Ruqaa, Amiri, Kufi

    private val problems = mutableListOf<String>()

    private fun check(tag: String, w: Float, h: Float) {
        val p = Render.last ?: return
        val items = p.drawn
        for ((name, r) in items) {
            if (r.left < 2f || r.top < 2f || r.right > w - 2f || r.bottom > h - 2f) {
                problems += "$tag: '$name' outside widget $r (size ${w}x$h)"
            }
        }
        for (i in items.indices) for (j in i + 1 until items.size) {
            val (n1, a) = items[i]
            val (n2, b) = items[j]
            if (n1 == "ring" || n2 == "ring") {
                val ring = if (n1 == "ring") a else b
                val other = if (n1 == "ring") b else a
                val inner = RectF(ring.left + 8f, ring.top + 8f, ring.right - 8f, ring.bottom - 8f)
                if (inner.contains(other)) continue
            }
            val ox = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val oy = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (ox > 0.5f && oy > 0.5f) problems += "$tag: '$n1' overlaps '$n2' (${ox}x$oy)"
        }
    }

    private fun sheet(name: String, shots: List<Bitmap>) {
        val d = ctx.resources.displayMetrics.density
        val pad = (12 * d).toInt()
        val cols = 4
        val cellW = shots.maxOf { it.width } + pad
        val cellH = shots.maxOf { it.height } + pad
        val rows = (shots.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * cellW + pad, rows * cellH + pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(46, 64, 104))
        shots.forEachIndexed { i, b -> c.drawBitmap(b, (pad + (i % cols) * cellW).toFloat(), (pad + (i / cols) * cellH).toFloat(), Paint()) }
        val dir = File("build/snapshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun styles(): List<Pair<String, WidgetStyle>> = listOf(
        "glass" to WidgetStyle(true, true, 24, 0, 100, WidgetStyle.DEFAULT_TEXT, WidgetStyle.DEFAULT_ACCENT, true),
        "frameless-dark-text" to WidgetStyle(false, false, 24, 0, 100, 0xFF111111.toInt(), 0xFF9E2B3A.toInt(), true),
    )

    @Test
    fun allWidgetsFitAtAllSizes() {
        prefs.latitude = 21.4858; prefs.longitude = 39.1925; prefs.cityName = "جدة"
        for (type in WidgetType.values()) {
            val shots = mutableListOf<Bitmap>()
            for ((sName, base) in styles()) for (scale in scales) for (font in fonts) {
                prefs.headingFont = font
                prefs.saveStyle(type, base.copy(scale = scale))
                for ((w, h) in sizes) {
                    val tag = "${type.key}/$sName/scale$scale/font$font/${w.toInt()}x${h.toInt()}"
                    val bmp = when (type) {
                        WidgetType.PRAYER -> Render.prayer(ctx, prefs, w, h, now)
                        WidgetType.DATE -> Render.date(ctx, prefs, w, h, today)
                        WidgetType.AYAH -> Render.ayah(ctx, prefs, w, h, today)
                        WidgetType.TASBIH -> { prefs.tasbihCount = 45; Render.tasbih(ctx, prefs, w, h) }
                    }
                    check(tag, w, h)
                    if (font == 1 || type == WidgetType.PRAYER || type == WidgetType.DATE) {
                        if (scale != 70 || sName == "glass") shots += bmp
                    }
                }
            }
            sheet(type.key, shots)
        }
        // Every verse at the default 4x1 and small sizes.
        prefs.saveStyle(WidgetType.AYAH, styles()[0].second)
        for (i in Ayat.list.indices) for ((w, h) in listOf(340f to 96f, 250f to 70f, 170f to 170f)) {
            prefs.ayahOffset = i
            Render.ayah(ctx, prefs, w, h, today)
            check("ayah#$i/${w.toInt()}x${h.toInt()}", w, h)
        }
        File("build/snapshots").mkdirs()
        File("build/snapshots/problems.txt").writeText(problems.joinToString("\n").ifEmpty { "none" })
        println("LAYOUT PROBLEMS: ${problems.size}")
        problems.groupBy { it.substringBefore('/') + " " + it.substringAfter(": ").replace(Regex("\\(.*"), "") }
            .forEach { (k, v) -> println("PROBLEM x${v.size}: $k | e.g. ${v.first()}") }
        assertTrue("Layout problems:\n" + problems.take(60).joinToString("\n"), problems.isEmpty())
    }
}
