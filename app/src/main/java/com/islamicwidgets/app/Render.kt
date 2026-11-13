package com.islamicwidgets.app

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** Colors of the glass style. Text is always light, with a soft shadow, so it reads on any wallpaper. */
class Glass(val fill: Int) {
    val stroke = Color.argb(82, 255, 255, 255)
    val highlight = Color.argb(90, 255, 255, 255)
    val text = Color.WHITE
    val muted = Color.argb(217, 255, 255, 255)
    val accent = Color.rgb(255, 226, 168)
    val track = Color.argb(64, 255, 255, 255)
    val chip = Color.argb(56, 255, 255, 255)
    val chipStroke = Color.argb(153, 255, 255, 255)

    companion object {
        fun from(ctx: Context, prefs: Prefs): Glass {
            val a = (prefs.glassOpacity.coerceIn(5, 60) * 255 / 100)
            val rgb = when (prefs.glassTint) {
                1 -> Color.rgb(255, 255, 255)
                2 -> wallpaperTint(ctx) ?: Color.rgb(14, 20, 38)
                else -> Color.rgb(14, 20, 38)
            }
            return Glass(Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb)))
        }

        /** Main wallpaper color, darkened so light text stays readable. */
        private fun wallpaperTint(ctx: Context): Int? {
            if (Build.VERSION.SDK_INT < 27) return null
            return try {
                val c = WallpaperManager.getInstance(ctx)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() ?: return null
                Color.rgb((Color.red(c) * 0.55f).toInt(), (Color.green(c) * 0.55f).toInt(), (Color.blue(c) * 0.55f).toInt())
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Draws a widget into a bitmap. All sizes are in dp; [px] converts.
 * Text is placed by its measured ink bounds, so tall calligraphy (e.g. Thuluth alif)
 * is always fitted inside its box and never clipped or overlapping.
 */
class Painter(ctx: Context, val w: Float, val h: Float) {
    private val density: Float
    val bitmap: Bitmap
    val canvas: Canvas

    init {
        var d = ctx.resources.displayMetrics.density
        // Keep each widget bitmap under ~6 MB.
        val bytes = w * d * h * d * 4f
        if (bytes > 6_000_000f) d *= sqrt(6_000_000f / bytes)
        density = d
        bitmap = Bitmap.createBitmap(
            ceil(w * d).toInt().coerceAtLeast(1),
            ceil(h * d).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        canvas = Canvas(bitmap)
        canvas.scale(d, d) // from here on, draw in dp
    }

    private val tmp = Rect()

    fun textPaint(tf: Typeface, sizeDp: Float, color: Int, shadow: Boolean = true): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = tf
            textSize = sizeDp
            this.color = color
            textLocale = Locale("ar")
            textAlign = Paint.Align.LEFT
            if (shadow) setShadowLayer(1.5f, 0f, 0.8f, Color.argb(90, 0, 0, 0))
        }

    /** Ink bounds of [text] at the paint's size, relative to the drawing origin. */
    fun ink(p: Paint, text: String): Rect {
        p.getTextBounds(text, 0, text.length, tmp)
        return Rect(tmp)
    }

    /** Shrinks the paint's size (from its current size down to [minSize]) until the ink fits [maxW]x[maxH]. */
    fun fit(p: Paint, text: String, maxW: Float, maxH: Float, minSize: Float) {
        while (p.textSize > minSize) {
            val r = ink(p, text)
            if (r.width() <= maxW && r.height() <= maxH) return
            p.textSize = (p.textSize - 0.5f).coerceAtLeast(minSize)
        }
    }

    enum class H { LEFT, CENTER, RIGHT }

    /** Draws [text] with its ink aligned horizontally per [hAlign] and centred vertically in [box]. Returns ink width. */
    fun drawInk(p: Paint, text: String, box: RectF, hAlign: H): Float {
        val r = ink(p, text)
        val x = when (hAlign) {
            H.RIGHT -> box.right - r.right
            H.LEFT -> box.left - r.left
            H.CENTER -> box.centerX() - (r.left + r.right) / 2f
        }
        val y = box.centerY() - (r.top + r.bottom) / 2f
        canvas.drawText(text, x, y, p)
        return r.width().toFloat()
    }

    fun card(g: Glass, radius: Float = min(28f, h / 2f)) {
        val rect = RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = g.fill }
        canvas.drawRoundRect(rect, radius, radius, fill)
        // soft highlight along the top edge
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = g.highlight; strokeWidth = 1f }
        canvas.drawLine(radius, 1.2f, w - radius, 1.2f, hl)
        canvas.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f; color = g.stroke
        }
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    /** Eight-pointed star (two overlapping squares) centred at (cx, cy). */
    fun star(cx: Float, cy: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val half = size / 2f
        for (angle in listOf(0f, 45f)) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, p)
            canvas.restore()
        }
    }
}

object Render {
    const val PAD = 16f

    // Prayer widget layout (dp). The live countdown is a Chronometer placed over the bitmap
    // by res/layout/widget_prayer.xml at left = CHRONO_LEFT, top = CHRONO_TOP.
    const val CHRONO_LEFT = 18f
    const val CHRONO_TOP = 30f
    private const val GRID_H = 52f
    private const val TOP_H = 86f

    fun prayer(ctx: Context, prefs: Prefs, w: Float, h: Float, now: Instant = Instant.now()): Bitmap {
        val pt = Painter(ctx, w, h)
        val g = Glass.from(ctx, prefs)
        val ar = prefs.arabicDigits
        val st = Prayer.state(prefs, now)
        pt.card(g)

        val showGrid = h >= 150f
        val showBar = h >= PAD + TOP_H + 28f + GRID_H + PAD
        val topH = when {
            showBar -> TOP_H
            showGrid -> h - PAD - GRID_H - 8f - PAD
            else -> h - 2 * PAD
        }.coerceAtLeast(60f)

        // Left block: label, (chronometer overlay), city
        val leftW = (w - 2 * PAD) * 0.42f
        val label = pt.textPaint(Fonts.kufi(ctx), 11f, g.muted)
        pt.fit(label, "الصلاة القادمة بعد", leftW, 14f, 8f)
        pt.drawInk(label, "الصلاة القادمة بعد", RectF(CHRONO_LEFT, PAD, CHRONO_LEFT + leftW, PAD + 13f), Painter.H.LEFT)
        val city = pt.textPaint(Fonts.kufi(ctx), 11f, g.muted)
        pt.fit(city, prefs.cityName, leftW, 14f, 8f)
        pt.drawInk(city, prefs.cityName, RectF(CHRONO_LEFT, CHRONO_TOP + 37f, CHRONO_LEFT + leftW, CHRONO_TOP + 50f), Painter.H.LEFT)

        // Right block: next prayer name in the heading font
        val name = Arabic.prayerNames[st.nextIndex]
        val nameBox = RectF(PAD + leftW + 12f, PAD, w - PAD, PAD + topH)
        val np = pt.textPaint(Fonts.heading(ctx, prefs), 44f, g.accent)
        pt.fit(np, name, nameBox.width(), nameBox.height(), 14f)
        pt.drawInk(np, name, nameBox, Painter.H.RIGHT)

        if (showBar) {
            val gridTop = h - PAD - GRID_H
            val y = (PAD + topH + gridTop) / 2f
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = g.track }
            val l = PAD + 2f
            val r = w - PAD - 2f
            pt.canvas.drawRoundRect(RectF(l, y - 2f, r, y + 2f), 2f, 2f, track)
            val fillW = (r - l) * st.progress(now)
            if (fillW > 0f) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = g.accent }
                pt.canvas.drawRoundRect(RectF(r - fillW, y - 2f, r, y + 2f), 2f, 2f, fill)
            }
        }

        if (showGrid) {
            val top = h - PAD - GRID_H
            val gap = 6f
            val cw = (w - 2 * PAD - 4 * gap) / 5f
            for (i in 0 until 5) {
                val right = w - PAD - i * (cw + gap)
                val cell = RectF(right - cw, top, right, top + GRID_H)
                val isNext = i == st.nextIndex
                if (isNext) {
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = g.chip }
                    pt.canvas.drawRoundRect(cell, 14f, 14f, bg)
                    val sk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1f; color = g.chipStroke
                    }
                    pt.canvas.drawRoundRect(cell, 14f, 14f, sk)
                }
                val np2 = pt.textPaint(Fonts.kufi(ctx), 11f, if (isNext) g.accent else g.muted)
                pt.fit(np2, Arabic.prayerNames[i], cw - 6f, 14f, 7f)
                pt.drawInk(np2, Arabic.prayerNames[i], RectF(cell.left, top + 8f, cell.right, top + 22f), Painter.H.CENTER)
                val t = Arabic.time12(Prayer.localTime(st.nextTimes.times[i]), ar)
                val tp = pt.textPaint(Fonts.kufiBold(ctx), 14f, g.text)
                pt.fit(tp, t, cw - 6f, 14f, 8f)
                pt.drawInk(tp, t, RectF(cell.left, top + 28f, cell.right, top + 43f), Painter.H.CENTER)
            }
        }
        return pt.bitmap
    }

    fun date(ctx: Context, prefs: Prefs, w: Float, h: Float, today: LocalDate = LocalDate.now()): Bitmap {
        val pt = Painter(ctx, w, h)
        val g = Glass.from(ctx, prefs)
        val ar = prefs.arabicDigits
        pt.card(g)
        val padX = 22f
        val padY = 14f
        val row2H = 22f
        val row1 = RectF(padX, padY, w - padX, h - padY - row2H - 6f)

        // Weekday (heading font) on the right
        val day = Arabic.weekdays.getValue(today.dayOfWeek)
        val wp = pt.textPaint(Fonts.heading(ctx, prefs), 46f, g.accent)
        pt.fit(wp, day, row1.width() * 0.5f, row1.height(), 14f)
        val dayInkW = pt.drawInk(wp, day, RectF(row1.left, row1.top, row1.right, row1.bottom), Painter.H.RIGHT)

        // Hijri group on the left: big day number, then month and year
        val (hd, hm, hy) = Prayer.hijri(prefs, today)
        val avail = row1.width() - dayInkW - 14f
        val numText = Arabic.digits(hd.toString(), ar)
        val month = Arabic.hijriMonths[hm - 1]
        val year = Arabic.digits("$hy هـ", ar)
        val numP = pt.textPaint(Fonts.amiriBold(ctx), min(52f, row1.height() * 0.62f), g.text)
        pt.fit(numP, numText, avail * 0.3f, row1.height() * 0.62f, 12f)
        val numW = pt.ink(numP, numText).width().toFloat()
        val mP = pt.textPaint(Fonts.amiriBold(ctx), 19f, g.text)
        pt.fit(mP, month, (avail - numW - 8f).coerceAtLeast(10f), row1.height() * 0.4f, 9f)
        val yP = pt.textPaint(Fonts.kufi(ctx), 12f, g.muted)
        pt.fit(yP, year, (avail - numW - 8f).coerceAtLeast(10f), 14f, 8f)
        val colW = maxOf(pt.ink(mP, month).width(), pt.ink(yP, year).width()).toFloat()
        val groupRight = row1.left + colW + 8f + numW
        val cy = row1.centerY()
        pt.drawInk(numP, numText, RectF(groupRight - numW, row1.top, groupRight, row1.bottom), Painter.H.RIGHT)
        val colRight = groupRight - numW - 8f
        pt.drawInk(mP, month, RectF(row1.left, cy - 20f, colRight, cy + 2f), Painter.H.RIGHT)
        pt.drawInk(yP, year, RectF(row1.left, cy + 5f, colRight, cy + 18f), Painter.H.RIGHT)

        // Gregorian date between ornaments
        val greg = Arabic.digits("${today.dayOfMonth} ${Arabic.gregorianMonths[today.monthValue - 1]} ${today.year} م", ar)
        val gp = pt.textPaint(Fonts.kufi(ctx), 13f, g.text)
        pt.fit(gp, greg, w - 2 * padX - 80f, 16f, 8f)
        val gy = h - padY - row2H / 2f
        val gw = pt.drawInk(gp, greg, RectF(padX, gy - 8f, w - padX, gy + 8f), Painter.H.CENTER)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(102, 255, 255, 255); strokeWidth = 1f }
        val sL = w / 2f - gw / 2f - 12f
        val sR = w / 2f + gw / 2f + 12f
        pt.star(sL, gy, 7f, g.accent)
        pt.star(sR, gy, 7f, g.accent)
        if (sL - 10f > padX) pt.canvas.drawLine(padX, gy, sL - 10f, gy, line)
        if (sR + 10f < w - padX) pt.canvas.drawLine(sR + 10f, gy, w - padX, gy, line)
        return pt.bitmap
    }

    fun ayahIndex(prefs: Prefs, today: LocalDate = LocalDate.now()): Int =
        Math.floorMod(today.dayOfYear + today.year + prefs.ayahOffset, Ayat.list.size)

    fun ayah(ctx: Context, prefs: Prefs, w: Float, h: Float, today: LocalDate = LocalDate.now()): Bitmap {
        val pt = Painter(ctx, w, h)
        val g = Glass.from(ctx, prefs)
        pt.card(g, min(28f, h / 2f))
        val a = Ayat.list[ayahIndex(prefs, today)]
        val padX = 18f
        val padY = 8f
        val refH = 15f
        val ref = "سورة ${a.surah} · ${Arabic.digits(a.number.toString(), prefs.arabicDigits)}"
        val rp = pt.textPaint(Fonts.kufi(ctx), 10.5f, g.muted)
        pt.fit(rp, ref, w - 2 * padX, 13f, 7f)
        pt.drawInk(rp, ref, RectF(padX, h - padY - refH + 1f, w - padX, h - padY), Painter.H.CENTER)

        val text = "﴿" + a.text + "﴾"
        val span = SpannableString(text)
        span.setSpan(ForegroundColorSpan(g.accent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(g.accent), text.length - 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val maxW = (w - 2 * padX).toInt().coerceAtLeast(20)
        val maxH = h - 2 * padY - refH - 2f
        val tp = pt.textPaint(Fonts.quran(ctx), 22f, g.text)
        var layout: StaticLayout
        while (true) {
            layout = StaticLayout.Builder.obtain(span, 0, span.length, tp, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .build()
            if ((layout.height <= maxH && layout.lineCount <= 3) || tp.textSize <= 9f) break
            tp.textSize -= 0.5f
        }
        val top = padY + (maxH - layout.height) / 2f
        pt.canvas.save()
        pt.canvas.translate(padX, top)
        layout.draw(pt.canvas)
        pt.canvas.restore()
        return pt.bitmap
    }

    /** Tasbih phases: 33 × سبحان الله, 33 × الحمد لله, 34 × الله أكبر = 100. */
    private val phases = listOf(
        Triple(0, 33, "سُبْحَانَ اللَّهِ"),
        Triple(33, 33, "الْحَمْدُ لِلَّهِ"),
        Triple(66, 34, "اللَّهُ أَكْبَرُ"),
    )

    fun tasbih(ctx: Context, prefs: Prefs, w: Float, h: Float): Bitmap {
        val pt = Painter(ctx, w, h)
        val g = Glass.from(ctx, prefs)
        pt.card(g)
        val count = prefs.tasbihCount.coerceIn(0, 99)
        val phase = phases.last { count >= it.first }
        val inPhase = count - phase.first
        val phraseH = 26f
        val r = (min(w - 32f, h - phraseH - 34f) / 2f).coerceAtLeast(16f)
        val cx = w / 2f
        val cy = 12f + r + (h - phraseH - 24f - 2 * r) / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = g.track; strokeCap = Paint.Cap.ROUND
        }
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        pt.canvas.drawArc(oval, 0f, 360f, false, ring)
        ring.color = g.accent
        val sweep = 360f * inPhase / phase.second
        if (sweep > 0f) pt.canvas.drawArc(oval, -90f, sweep, false, ring)
        val num = Arabic.digits(inPhase.toString(), prefs.arabicDigits)
        val np = pt.textPaint(Fonts.kufiBold(ctx), 24f, g.text)
        pt.fit(np, num, r * 1.3f, r * 0.8f, 10f)
        pt.drawInk(np, num, oval, Painter.H.CENTER)
        val pp = pt.textPaint(Fonts.amiri(ctx), 19f, g.text)
        pt.fit(pp, phase.third, w - 24f, phraseH - 4f, 9f)
        pt.drawInk(pp, phase.third, RectF(12f, h - 10f - phraseH, w - 12f, h - 10f), Painter.H.CENTER)
        return pt.bitmap
    }

    fun tasbihPhrase(prefs: Prefs): String = phases.last { prefs.tasbihCount >= it.first }.third
}
