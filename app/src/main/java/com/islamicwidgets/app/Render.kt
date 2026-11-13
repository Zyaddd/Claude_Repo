package com.islamicwidgets.app

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/** Resolved colors for one widget, derived from its [WidgetStyle]. */
class Look(ctx: Context, val style: WidgetStyle) {
    val scale = style.scale.coerceIn(70, 150) / 100f
    val text = style.textColor
    val accent = style.accentColor
    val muted = withAlpha(text, 217)
    val track = withAlpha(text, 64)
    val chip = withAlpha(text, 46)
    val chipStroke = withAlpha(text, 150)
    val stroke = withAlpha(text, 82)
    val fill: Int? = if (!style.background) null else {
        val a = style.opacity.coerceIn(0, 90) * 255 / 100
        val rgb = when (style.tint) {
            1 -> Color.rgb(255, 255, 255)
            2 -> wallpaperTint(ctx) ?: Color.rgb(14, 20, 38)
            else -> Color.rgb(14, 20, 38)
        }
        Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    /** Light text gets a dark shadow, dark text a light one. */
    val shadowColor: Int? = if (!style.shadow) null
    else if (luminance(text) > 0.5f) Color.argb(100, 0, 0, 0) else Color.argb(110, 255, 255, 255)

    companion object {
        fun withAlpha(c: Int, a: Int) = Color.argb(a * Color.alpha(c) / 255, Color.red(c), Color.green(c), Color.blue(c))
        fun luminance(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

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
 * Draws a widget into a bitmap. All sizes are in dp.
 * Text is placed by its measured ink bounds, so tall calligraphy (e.g. Thuluth alif)
 * is fitted inside its box and never clipped or overlapping.
 */
class Painter(ctx: Context, val w: Float, val h: Float, val look: Look) {
    val bitmap: Bitmap
    val canvas: Canvas

    init {
        var d = ctx.resources.displayMetrics.density
        val bytes = w * d * h * d * 4f
        if (bytes > 6_000_000f) d *= sqrt(6_000_000f / bytes) // keep each bitmap under ~6 MB
        bitmap = Bitmap.createBitmap(
            ceil(w * d).toInt().coerceAtLeast(1),
            ceil(h * d).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        canvas = Canvas(bitmap)
        canvas.scale(d, d)
    }

    private val tmp = Rect()

    /** Ink boxes of everything drawn (and areas reserved for overlays), for layout checks in tests. */
    val drawn = mutableListOf<Pair<String, RectF>>()
    fun reserve(name: String, r: RectF) { drawn += name to RectF(r) }

    fun paint(tf: Typeface, sizeDp: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = tf
            textSize = sizeDp
            this.color = color
            @Suppress("DEPRECATION")
            textLocale = Locale("ar")
            textAlign = Paint.Align.LEFT
            look.shadowColor?.let { setShadowLayer(1.5f, 0f, 0.8f, it) }
        }

    fun ink(p: Paint, text: String): Rect {
        p.getTextBounds(text, 0, text.length, tmp)
        return Rect(tmp)
    }

    /** Shrinks the paint's size down to [minSize] until the ink fits [maxW]x[maxH]. */
    fun fit(p: Paint, text: String, maxW: Float, maxH: Float, minSize: Float) {
        while (p.textSize > minSize) {
            val r = ink(p, text)
            if (r.width() <= maxW && r.height() <= maxH) return
            p.textSize = (p.textSize - 0.5f).coerceAtLeast(minSize)
        }
    }

    enum class H { LEFT, CENTER, RIGHT }

    /** Draws [text] with its ink aligned per [hAlign] and centred vertically in [box]. Returns ink width. */
    fun drawInk(p: Paint, text: String, box: RectF, hAlign: H): Float {
        val r = ink(p, text)
        val x = when (hAlign) {
            H.RIGHT -> box.right - r.right
            H.LEFT -> box.left - r.left
            H.CENTER -> box.centerX() - (r.left + r.right) / 2f
        }
        val y = box.centerY() - (r.top + r.bottom) / 2f
        canvas.drawText(text, x, y, p)
        drawn += text to RectF(x + r.left, y + r.top, x + r.right, y + r.bottom)
        return r.width().toFloat()
    }

    /** Fits and draws in one step. */
    fun text(tf: Typeface, size: Float, color: Int, s: String, box: RectF, hAlign: H, minSize: Float = 7f): Float {
        val p = paint(tf, size, color)
        fit(p, s, box.width(), box.height(), minSize)
        return drawInk(p, s, box, hAlign)
    }

    /** Background and border. The border is one uniform 1 dp stroke on all four sides. */
    fun card() {
        val radius = min(28f, min(w, h) / 2f)
        val rect = RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f)
        look.fill?.let { c ->
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c })
        }
        if (look.style.border) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f; color = look.stroke
            }
            canvas.drawRoundRect(rect, radius, radius, stroke)
        }
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

/** Where the live Chronometer sits over the prayer bitmap. */
class PrayerGeom(
    val small: Boolean,
    /** Chronometer text size, dp. */
    val chronoSize: Float,
    /** Wide: left/top margins. Small: bottom margin (centred horizontally). */
    val chronoLeft: Float,
    val chronoTop: Float,
    val chronoBottom: Float,
)

object Render {
    /** The painter used by the most recent render call (read by layout tests). */
    var last: Painter? = null
        private set

    private fun painter(ctx: Context, w: Float, h: Float, look: Look) = Painter(ctx, w, h, look).also { last = it }

    private const val PAD = 16f
    const val WIDE_MIN_W = 230f

    // Positions used by the layout XML when margins can't be set at runtime (Android < 12).
    const val FIXED_TOP = 30f
    const val FIXED_LEFT = 18f
    const val FIXED_BOTTOM = 30f
    const val FIXED_SIZE_WIDE = 28f
    const val FIXED_SIZE_SMALL = 26f

    private val dynamic get() = Build.VERSION.SDK_INT >= 31

    /** Width of the countdown text per dp of text size, for the longest value ("10:04:00"). */
    const val CHRONO_EM_W = 4.6f

    fun prayerGeom(w: Float, h: Float, look: Look): PrayerGeom {
        val s = look.scale
        return if (w >= WIDE_MIN_W) {
            val labelSize = if (dynamic) (11f * s).coerceIn(8f, 15f) else 11f
            val top = if (dynamic) PAD + labelSize * 1.3f + 1f else FIXED_TOP
            val wanted = if (dynamic) (28f * s).coerceIn(18f, 40f) else FIXED_SIZE_WIDE
            val maxByW = (w - 2 * PAD) * 0.56f / CHRONO_EM_W
            val maxByH = (h - 6f - top) / 1.18f
            PrayerGeom(
                small = false,
                chronoSize = minOf(wanted, maxByW, maxByH).coerceAtLeast(10f),
                chronoLeft = FIXED_LEFT,
                chronoTop = top,
                chronoBottom = 0f,
            )
        } else {
            val small = if (dynamic) (12f * s).coerceIn(8f, 15f) else 12f
            val wanted = if (dynamic) (26f * s).coerceIn(16f, 36f) else FIXED_SIZE_SMALL
            val bottom = if (dynamic) 12f + small * 1.3f + 2f else FIXED_BOTTOM
            val maxByW = (w - 16f) / CHRONO_EM_W
            val maxByH = (h - bottom - 24f) / 1.18f // leave room for the prayer name above
            PrayerGeom(
                small = true,
                chronoSize = minOf(wanted, maxByW, maxByH).coerceAtLeast(10f),
                chronoLeft = 0f,
                chronoTop = 0f,
                chronoBottom = bottom,
            )
        }
    }

    fun prayer(ctx: Context, prefs: Prefs, w: Float, h: Float, now: Instant = Instant.now()): Bitmap {
        val look = Look(ctx, prefs.style(WidgetType.PRAYER))
        val pt = painter(ctx, w, h, look)
        val geo = prayerGeom(w, h, look)
        val s = look.scale
        val ar = prefs.arabicDigits
        val st = Prayer.state(prefs, now)
        val name = Arabic.prayerNames[st.nextIndex]
        val heading = Fonts.heading(ctx, prefs)
        val kufi = Fonts.kufi(ctx)
        pt.card()
        val chronoLine = geo.chronoSize * 1.18f
        val chronoW = geo.chronoSize * CHRONO_EM_W
        if (geo.small) {
            pt.reserve("countdown", RectF(w / 2f - chronoW / 2f, h - geo.chronoBottom - chronoLine, w / 2f + chronoW / 2f, h - geo.chronoBottom))
        } else {
            pt.reserve("countdown", RectF(geo.chronoLeft, geo.chronoTop, geo.chronoLeft + chronoW, geo.chronoTop + chronoLine))
        }

        if (geo.small) {
            // Name on top, countdown (Chronometer overlay) below, then time · city.
            val smallSize = if (dynamic) (12f * s).coerceIn(8f, 15f) else 12f
            val info = "${Arabic.time12(Prayer.localTime(st.nextAt), ar)} · ${prefs.cityName}"
            pt.text(kufi, smallSize, look.muted, info, RectF(10f, h - 12f - smallSize * 1.3f, w - 10f, h - 12f), Painter.H.CENTER)
            val nameBottom = h - geo.chronoBottom - chronoLine - 2f
            pt.text(heading, 44f * s, look.accent, name, RectF(12f, 10f, w - 12f, nameBottom), Painter.H.CENTER, 12f)
            return pt.bitmap
        }

        val labelSize = if (dynamic) (11f * s).coerceIn(8f, 15f) else 11f
        val gridH = (52f * s).coerceIn(44f, 66f)
        val cityTop = geo.chronoTop + chronoLine + 2f
        val blockBottom = cityTop + labelSize * 1.3f
        val showGrid = h >= blockBottom + 8f + gridH + PAD
        val showBar = h >= blockBottom + 26f + gridH + PAD
        val showCity = showGrid || blockBottom <= h - 10f
        val nameBottom = when {
            showBar -> h - PAD - gridH - 26f
            showGrid -> h - PAD - gridH - 8f
            else -> h - PAD
        }

        val leftW = ((w - 2 * PAD) * 0.42f).coerceAtLeast(geo.chronoSize * CHRONO_EM_W).coerceAtMost((w - 2 * PAD) * 0.56f)
        val left = geo.chronoLeft
        pt.text(kufi, labelSize, look.muted, "الصلاة القادمة بعد", RectF(left, PAD, left + leftW, PAD + labelSize * 1.2f), Painter.H.LEFT)
        if (showCity) {
            pt.text(kufi, labelSize, look.muted, prefs.cityName, RectF(left, cityTop, left + leftW, blockBottom), Painter.H.LEFT)
        }
        val nameBox = RectF(PAD + leftW + 12f, PAD, w - PAD, nameBottom.coerceAtLeast(PAD + 30f))
        pt.text(heading, 44f * s, look.accent, name, nameBox, Painter.H.RIGHT, 12f)

        if (showBar) {
            val gridTop = h - PAD - gridH
            val y = (nameBottom + gridTop) / 2f
            val l = PAD + 2f
            val r = w - PAD - 2f
            pt.canvas.drawRoundRect(RectF(l, y - 2f, r, y + 2f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.track })
            val fillW = (r - l) * st.progress(now)
            if (fillW > 0f) {
                pt.canvas.drawRoundRect(RectF(r - fillW, y - 2f, r, y + 2f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.accent })
            }
        }

        if (showGrid) {
            val top = h - PAD - gridH
            val gap = 6f
            val cw = (w - 2 * PAD - 4 * gap) / 5f
            val nameSize = (11f * s).coerceIn(8f, 15f)
            val timeSize = (14f * s).coerceIn(10f, 19f)
            for (i in 0 until 5) {
                val right = w - PAD - i * (cw + gap)
                val cell = RectF(right - cw, top, right, top + gridH)
                val isNext = i == st.nextIndex
                if (isNext) {
                    pt.canvas.drawRoundRect(cell, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.chip })
                    pt.canvas.drawRoundRect(cell, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1f; color = look.chipStroke
                    })
                }
                val half = gridH / 2f
                pt.text(kufi, nameSize, if (isNext) look.accent else look.muted, Arabic.prayerNames[i],
                    RectF(cell.left + 3f, top + 6f, cell.right - 3f, top + half - 1f), Painter.H.CENTER)
                pt.text(Fonts.kufiBold(ctx), timeSize, look.text, Arabic.time12(Prayer.localTime(st.nextTimes.times[i]), ar),
                    RectF(cell.left + 3f, top + half + 1f, cell.right - 3f, top + gridH - 6f), Painter.H.CENTER)
            }
        }
        return pt.bitmap
    }

    fun date(ctx: Context, prefs: Prefs, w: Float, h: Float, today: LocalDate = LocalDate.now()): Bitmap {
        val look = Look(ctx, prefs.style(WidgetType.DATE))
        val pt = painter(ctx, w, h, look)
        val s = look.scale
        val ar = prefs.arabicDigits
        pt.card()
        val day = Arabic.weekdays.getValue(today.dayOfWeek)
        val (hd, hm, hy) = Prayer.hijri(prefs, today)
        val month = Arabic.hijriMonths[hm - 1]
        val greg = Arabic.digits("${today.dayOfMonth} ${Arabic.gregorianMonths[today.monthValue - 1]} ${today.year} م", ar)
        val heading = Fonts.heading(ctx, prefs)

        if (w < WIDE_MIN_W) {
            // Small: weekday, Hijri date, Gregorian date stacked and centred.
            val gSize = (11f * s).coerceIn(8f, 15f)
            val hSize = (16f * s).coerceIn(10f, 22f)
            val gBox = RectF(10f, h - 12f - gSize * 1.3f, w - 10f, h - 12f)
            val hBox = RectF(10f, gBox.top - 4f - hSize * 1.4f, w - 10f, gBox.top - 4f)
            pt.text(Fonts.kufi(ctx), gSize, look.muted, greg, gBox, Painter.H.CENTER)
            pt.text(Fonts.amiriBold(ctx), hSize, look.text, Arabic.digits("$hd $month", ar), hBox, Painter.H.CENTER)
            pt.text(heading, 46f * s, look.accent, day, RectF(12f, 10f, w - 12f, hBox.top - 2f), Painter.H.CENTER, 12f)
            return pt.bitmap
        }

        val padX = 22f
        val padY = 14f
        val row2H = (22f * s).coerceIn(18f, 30f)
        val row1 = RectF(padX, padY, w - padX, h - padY - row2H - 6f)

        val wp = pt.paint(heading, 46f * s, look.accent)
        pt.fit(wp, day, row1.width() * 0.5f, row1.height(), 12f)
        val dayInkW = pt.drawInk(wp, day, row1, Painter.H.RIGHT)

        val avail = row1.width() - dayInkW - 14f
        val numText = Arabic.digits(hd.toString(), ar)
        val year = Arabic.digits("$hy هـ", ar)
        val numP = pt.paint(Fonts.amiriBold(ctx), min(52f * s, row1.height() * 0.62f), look.text)
        pt.fit(numP, numText, avail * 0.3f, row1.height() * 0.62f, 10f)
        val numW = pt.ink(numP, numText).width().toFloat()
        val colMax = (avail - numW - 8f).coerceAtLeast(10f)
        val mP = pt.paint(Fonts.amiriBold(ctx), 19f * s, look.text)
        pt.fit(mP, month, colMax, row1.height() * 0.4f, 8f)
        val yP = pt.paint(Fonts.kufi(ctx), (12f * s).coerceIn(8f, 16f), look.muted)
        pt.fit(yP, year, colMax, row1.height() * 0.25f, 7f)
        val colW = maxOf(pt.ink(mP, month).width(), pt.ink(yP, year).width()).toFloat()
        val groupRight = row1.left + colW + 8f + numW
        val cy = row1.centerY()
        val mH = mP.textSize * 1.25f
        val yH = yP.textSize * 1.25f
        val colTop = cy - (mH + yH) / 2f
        pt.drawInk(numP, numText, RectF(groupRight - numW, row1.top, groupRight, row1.bottom), Painter.H.RIGHT)
        val colRight = groupRight - numW - 8f
        pt.drawInk(mP, month, RectF(row1.left, colTop, colRight, colTop + mH), Painter.H.RIGHT)
        pt.drawInk(yP, year, RectF(row1.left, colTop + mH, colRight, colTop + mH + yH), Painter.H.RIGHT)

        val gy = h - padY - row2H / 2f
        val gw = pt.text(Fonts.kufi(ctx), (13f * s).coerceIn(9f, 18f), look.text, greg,
            RectF(padX + 40f, gy - row2H / 2f, w - padX - 40f, gy + row2H / 2f), Painter.H.CENTER)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Look.withAlpha(look.text, 102); strokeWidth = 1f }
        val sL = w / 2f - gw / 2f - 12f
        val sR = w / 2f + gw / 2f + 12f
        pt.star(sL, gy, 7f, look.accent)
        pt.star(sR, gy, 7f, look.accent)
        if (sL - 10f > padX) pt.canvas.drawLine(padX, gy, sL - 10f, gy, line)
        if (sR + 10f < w - padX) pt.canvas.drawLine(sR + 10f, gy, w - padX, gy, line)
        return pt.bitmap
    }

    fun ayahIndex(prefs: Prefs, today: LocalDate = LocalDate.now()): Int =
        Math.floorMod(today.dayOfYear + today.year + prefs.ayahOffset, Ayat.list.size)

    fun ayah(ctx: Context, prefs: Prefs, w: Float, h: Float, today: LocalDate = LocalDate.now()): Bitmap {
        val look = Look(ctx, prefs.style(WidgetType.AYAH))
        val pt = painter(ctx, w, h, look)
        val s = look.scale
        pt.card()
        val a = Ayat.list[ayahIndex(prefs, today)]
        val padX = if (w < WIDE_MIN_W) 12f else 18f
        val padY = 8f
        val refSize = (10.5f * s).coerceIn(7f, 14f)
        val refH = refSize * 1.4f
        val ref = "سورة ${a.surah} · ${Arabic.digits(a.number.toString(), prefs.arabicDigits)}"
        pt.text(Fonts.kufi(ctx), refSize, look.muted, ref, RectF(padX, h - padY - refH, w - padX, h - padY), Painter.H.CENTER)

        val text = "﴿" + a.text + "﴾"
        val span = SpannableString(text)
        span.setSpan(ForegroundColorSpan(look.accent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(look.accent), text.length - 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val maxW = (w - 2 * padX).toInt().coerceAtLeast(20)
        val maxH = h - 2 * padY - refH - 2f
        val maxLines = if (h > 140f) 7 else 3
        val tp = pt.paint(Fonts.quran(ctx), 22f * s, look.text)
        var layout: StaticLayout
        while (true) {
            layout = StaticLayout.Builder.obtain(span, 0, span.length, tp, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            if ((layout.height <= maxH && layout.lineCount <= maxLines) || tp.textSize <= 8f) break
            tp.textSize -= 0.5f
        }
        val top = padY + (maxH - layout.height) / 2f
        pt.reserve("verse", RectF(padX, top, padX + maxW, top + layout.height))
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
        val look = Look(ctx, prefs.style(WidgetType.TASBIH))
        val pt = painter(ctx, w, h, look)
        val s = look.scale
        pt.card()
        val count = prefs.tasbihCount.coerceIn(0, 99)
        val phase = phases.last { count >= it.first }
        val inPhase = count - phase.first
        val horizontal = w >= h * 1.6f
        val r: Float
        val cx: Float
        val cy: Float
        val phraseBox: RectF
        if (horizontal) {
            // Ring on the right (start of RTL reading), phrase beside it.
            r = ((h - 24f) / 2f).coerceAtLeast(10f)
            cx = w - 12f - 2.5f - r
            cy = h / 2f
            phraseBox = RectF(12f, 10f, cx - r - 2.5f - 12f, h - 10f)
        } else {
            val phraseH = (24f * s).coerceIn(16f, 34f)
            r = (min(w - 32f, h - phraseH - 34f) / 2f).coerceAtLeast(10f)
            cx = w / 2f
            cy = 12f + r + ((h - phraseH - 24f - 2 * r) / 2f).coerceAtLeast(0f)
            phraseBox = RectF(10f, h - 10f - phraseH, w - 10f, h - 10f)
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = look.track; strokeCap = Paint.Cap.ROUND
        }
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        pt.reserve("ring", RectF(cx - r - 2.5f, cy - r - 2.5f, cx + r + 2.5f, cy + r + 2.5f))
        pt.canvas.drawArc(oval, 0f, 360f, false, ring)
        ring.color = look.accent
        val sweep = 360f * inPhase / phase.second
        if (sweep > 0f) pt.canvas.drawArc(oval, -90f, sweep, false, ring)
        pt.text(Fonts.kufiBold(ctx), 24f * s, look.text, Arabic.digits(inPhase.toString(), prefs.arabicDigits),
            RectF(cx - r * 0.65f, cy - r * 0.45f, cx + r * 0.65f, cy + r * 0.45f), Painter.H.CENTER, 7f)
        pt.text(Fonts.amiri(ctx), 19f * s, look.text, phase.third, phraseBox, Painter.H.CENTER, 7f)
        return pt.bitmap
    }

    fun tasbihPhrase(prefs: Prefs): String = phases.last { prefs.tasbihCount >= it.first }.third
}
