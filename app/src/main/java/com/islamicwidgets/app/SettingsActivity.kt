package com.islamicwidgets.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private val refreshers = mutableListOf<() -> Unit>()

    private val bg = Color.rgb(11, 21, 38)
    private val cardBg = Color.argb(20, 255, 255, 255)
    private val textColor = Color.rgb(244, 239, 228)
    private val muted = Color.rgb(184, 194, 208)
    private val gold = Color.rgb(217, 178, 106)

    private val cities = listOf(
        Triple("ثول", 22.3095, 39.1047),
        Triple("جدة", 21.4858, 39.1925),
        Triple("مكة المكرمة", 21.4225, 39.8262),
        Triple("المدينة المنورة", 24.4672, 39.6111),
        Triple("الرياض", 24.7136, 46.6753),
        Triple("الدمام", 26.4207, 50.0888),
        Triple("القاهرة", 30.0444, 31.2357),
        Triple("الإسكندرية", 31.2001, 29.9187),
        Triple("دبي", 25.2048, 55.2708),
        Triple("الدوحة", 25.2854, 51.5310),
        Triple("الكويت", 29.3759, 47.9774),
        Triple("عمّان", 31.9454, 35.9284),
    )

    private val methods = listOf(
        "UMM_AL_QURA" to "أم القرى (السعودية)",
        "EGYPTIAN" to "الهيئة المصرية العامة للمساحة",
        "MUSLIM_WORLD_LEAGUE" to "رابطة العالم الإسلامي",
        "KARACHI" to "جامعة العلوم الإسلامية بكراتشي",
        "DUBAI" to "دبي",
        "KUWAIT" to "الكويت",
        "QATAR" to "قطر",
        "NORTH_AMERICA" to "أمريكا الشمالية",
        "SINGAPORE" to "سنغافورة",
        "MOON_SIGHTING_COMMITTEE" to "لجنة رؤية الهلال",
    )

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            fitsSystemWindows = true
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(20f), dpi(16f), dpi(32f))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(list)
        setContentView(scroll)
        buildUi()
        refresh()
        if (!prefs.locationChosen) useMyLocation()
    }

    // ---------- UI building ----------

    private fun label(text: String, size: Float, color: Int, tf: Typeface = Fonts.kufi(this)) =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            typeface = tf
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
        }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius)
        if (stroke != null) setStroke(dpi(1f), stroke)
    }

    private fun section(title: String) {
        list.addView(label(title, 13f, gold).apply { setPadding(dpi(4f), dpi(18f), dpi(4f), dpi(8f)) })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cardBg, 20f, Color.argb(26, 255, 255, 255))
        setPadding(dpi(16f), dpi(6f), dpi(16f), dpi(6f))
        list.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    /** A tappable row with a title and a current value. */
    private fun row(parent: LinearLayout, title: String, value: () -> String, onClick: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dpi(56f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(8f), 0, dpi(8f))
            isClickable = true
            isFocusable = true
            val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = attrs.getDrawable(0)
            attrs.recycle()
            setOnClickListener { onClick() }
        }
        box.addView(label(title, 15f, textColor))
        val v = label("", 13f, muted)
        box.addView(v)
        refreshers += { v.text = value() }
        parent.addView(box)
    }

    private fun button(parent: LinearLayout, text: String, onClick: () -> Unit) {
        val b = Button(this).apply {
            this.text = text
            isAllCaps = false
            typeface = Fonts.kufiBold(this@SettingsActivity)
            setTextColor(bg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = rounded(gold, 14f)
            minHeight = dpi(48f)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dpi(8f), 0, dpi(8f)) })
    }

    // ---------- Per-widget customization ----------

    private var current = WidgetType.PRAYER
    private var lightPreview = false
    private lateinit var previewBox: LinearLayout
    private lateinit var previewMain: ImageView
    private lateinit var previewSmall: ImageView
    private lateinit var previewSmallLabel: TextView
    private val chips = mutableMapOf<WidgetType, TextView>()

    private val textColors = listOf(
        "أبيض" to 0xFFFFFFFF, "عاجي" to 0xFFF4EFE4, "ذهبي فاتح" to 0xFFFFE2A8, "ذهبي" to 0xFFD9B26A,
        "نعناعي" to 0xFFBFF0DC, "سماوي" to 0xFFCDE7FF, "وردي" to 0xFFFFD3D6,
        "رمادي داكن" to 0xFF2B2F36, "أسود" to 0xFF111111,
    ).map { it.first to it.second.toInt() }

    private val accentColors = listOf(
        "ذهبي فاتح" to 0xFFFFE2A8, "ذهبي" to 0xFFD9B26A, "أبيض" to 0xFFFFFFFF, "نعناعي" to 0xFF7FD1B9,
        "فيروزي" to 0xFF4FB3BF, "مرجاني" to 0xFFE8A87C, "عنابي" to 0xFF9E2B3A, "كحلي" to 0xFF1E3A6E,
        "أسود" to 0xFF111111,
    ).map { it.first to it.second.toInt() }

    private val tints = listOf("زجاج داكن", "زجاج فاتح", "لون من خلفية الشاشة")

    private fun style() = prefs.style(current)
    private fun edit(change: (WidgetStyle) -> WidgetStyle) {
        prefs.saveStyle(current, change(style()))
    }

    private fun colorName(list: List<Pair<String, Int>>, c: Int) =
        list.firstOrNull { it.second == c }?.first ?: String.format(Locale.US, "#%06X", c and 0xFFFFFF)

    private fun colorRow(parent: LinearLayout, title: String, list: List<Pair<String, Int>>, get: () -> Int, set: (Int) -> Unit) {
        row(parent, title, { "● " + colorName(list, get()) }) {
            val items = list.map { (n, c) ->
                SpannableString("●  $n").apply {
                    setSpan(ForegroundColorSpan(c), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }.toTypedArray<CharSequence>()
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(items, list.indexOfFirst { it.second == get() }) { d, which ->
                    set(list[which].second)
                    d.dismiss()
                    refresh()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun toggleRow(parent: LinearLayout, title: String, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpi(52f)
        }
        box.addView(label(title, 15f, textColor), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this).apply {
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(gold, muted))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.argb(120, 217, 178, 106), Color.argb(70, 255, 255, 255)))
            contentDescription = title
        }
        var updating = false
        sw.setOnCheckedChangeListener { _, checked -> if (!updating) { set(checked); refresh() } }
        refreshers += { updating = true; sw.isChecked = get(); updating = false }
        box.setOnClickListener { sw.toggle() }
        box.addView(sw)
        parent.addView(box)
        return box
    }

    private fun seekRow(parent: LinearLayout, title: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit) {
        val l = label("", 15f, textColor).apply { setPadding(0, dpi(10f), 0, 0) }
        parent.addView(l)
        val sb = SeekBar(this).apply {
            this.max = max - min
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minimumHeight = dpi(44f)
            progressTintList = android.content.res.ColorStateList.valueOf(gold)
            thumbTintList = android.content.res.ColorStateList.valueOf(gold)
            contentDescription = title
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { set(p + min); refreshPreviewsOnly() }
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) = refresh()
        })
        refreshers += {
            val v = get()
            l.text = "$title: ${Arabic.digits(v.toString(), prefs.arabicDigits)}٪"
            sb.progress = v - min
        }
        parent.addView(sb)
    }

    private fun buildCustomizer() {
        section("تخصيص الويدجت")
        // Widget picker chips
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in WidgetType.values()) {
            val chip = label(t.title, 13f, textColor, Fonts.kufiBold(this)).apply {
                gravity = Gravity.CENTER
                minHeight = dpi(44f)
                isClickable = true
                setOnClickListener { current = t; refreshPreviewsOnly() }
            }
            chips[t] = chip
            chipRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        }
        list.addView(chipRow)

        // Preview on a sample wallpaper
        previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(12f), dpi(14f), dpi(12f), dpi(14f))
        }
        previewMain = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true }
        previewSmallLabel = label("بحجم صغير (مثل شاشة القفل)", 11f, Color.WHITE).apply { gravity = Gravity.CENTER }
        previewSmall = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true }
        previewBox.addView(previewMain, LinearLayout.LayoutParams(-1, -2))
        previewBox.addView(previewSmallLabel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dpi(12f), 0, dpi(6f)) })
        previewBox.addView(previewSmall, LinearLayout.LayoutParams(dpi(170f), dpi(170f)))
        list.addView(previewBox, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dpi(10f), 0, dpi(4f)) })

        val c = card()
        toggleRow(c, "خلفية المعاينة فاتحة", { lightPreview }) { lightPreview = it }
        toggleRow(c, "خلفية زجاجية", { style().background }) { v -> edit { it.copy(background = v) } }
        toggleRow(c, "إطار", { style().border }) { v -> edit { it.copy(border = v) } }
        seekRow(c, "شفافية الخلفية", 0, 90, { style().opacity }) { v -> edit { it.copy(opacity = v) } }
        row(c, "لون الزجاج", { tints[style().tint.coerceIn(0, 2)] }) {
            choose("لون الزجاج", tints, style().tint) { v -> edit { it.copy(tint = v) } }
        }
        seekRow(c, "حجم الخط", 70, 150, { style().scale }) { v -> edit { it.copy(scale = v) } }
        colorRow(c, "لون النص", textColors, { style().textColor }) { v -> edit { it.copy(textColor = v) } }
        colorRow(c, "لون التمييز (اسم الصلاة واليوم)", accentColors, { style().accentColor }) { v -> edit { it.copy(accentColor = v) } }
        toggleRow(c, "ظل النص", { style().shadow }) { v -> edit { it.copy(shadow = v) } }
        val tapRow = toggleRow(c, "العدّ عند لمس ويدجت التسبيح", { prefs.tasbihTap }) { prefs.tasbihTap = it }
        refreshers += { tapRow.visibility = if (current == WidgetType.TASBIH) View.VISIBLE else View.GONE }
        list.addView(label("الخط يكبر حتى يملأ مساحته فقط، ولا يخرج عن حدود الويدجت أبداً.", 11f, muted)
            .apply { setPadding(dpi(4f), dpi(6f), dpi(4f), 0) })
        val c2 = card().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dpi(8f) }
        button(c2, "تطبيق هذا التصميم على كل الويدجت") {
            val s = style()
            WidgetType.values().forEach { prefs.saveStyle(it, s) }
            refresh()
            Toast.makeText(this, "تم التطبيق على كل الويدجت.", Toast.LENGTH_SHORT).show()
        }
        button(c2, "استعادة التصميم الافتراضي لهذه الويدجت") {
            prefs.saveStyle(current, WidgetStyle(true, true, 24, 0, 100, WidgetStyle.DEFAULT_TEXT, WidgetStyle.DEFAULT_ACCENT, true))
            refresh()
        }
    }

    private fun buildUi() {
        list.addView(label("ويدجت إسلامية", 34f, gold, Fonts.ruqaa(this)).apply { setPadding(dpi(4f), 0, dpi(4f), 0) })
        list.addView(label("اضغط مطولاً على الشاشة الرئيسية ← الأدوات (Widgets) ← ويدجت إسلامية", 12f, muted)
            .apply { setPadding(dpi(4f), dpi(2f), dpi(4f), dpi(4f)) })

        buildCustomizer()

        section("أوقات الصلاة")
        val c1 = card()
        row(c1, "الموقع", { prefs.cityName }) { chooseLocation() }
        row(c1, "طريقة الحساب", { methods.firstOrNull { it.first == prefs.method }?.second ?: prefs.method }) {
            choose("طريقة الحساب", methods.map { it.second }, methods.indexOfFirst { it.first == prefs.method }) {
                prefs.method = methods[it].first
            }
        }
        row(c1, "مذهب العصر", { if (prefs.hanafi) "الحنفي" else "الجمهور (الشافعي، المالكي، الحنبلي)" }) {
            choose("مذهب العصر", listOf("الجمهور (الشافعي، المالكي، الحنبلي)", "الحنفي"), if (prefs.hanafi) 1 else 0) {
                prefs.hanafi = it == 1
            }
        }
        row(c1, "تعديل التاريخ الهجري", { adjustText(prefs.hijriAdjust) }) {
            val opts = (-2..2).toList()
            choose("تعديل التاريخ الهجري", opts.map { adjustText(it) }, opts.indexOf(prefs.hijriAdjust)) {
                prefs.hijriAdjust = opts[it]
            }
        }

        section("الخط والأرقام")
        val c2 = card()
        val fonts = listOf("الثلث (خط مستورد)", "الرقعة", "النسخ (أميري)", "الكوفي")
        row(c2, "خط العناوين", {
            if (prefs.headingFont == 0 && Fonts.imported(this) == null) "الرقعة (لم يُستورد خط بعد)"
            else if (prefs.headingFont == 0) "مستورد: ${prefs.importedFontName ?: "خط"}"
            else fonts[prefs.headingFont]
        }) {
            choose("خط العناوين", fonts, prefs.headingFont) {
                prefs.headingFont = it
                if (it == 0 && Fonts.imported(this) == null) importFont()
            }
        }
        row(c2, "استيراد خط (TTF أو ملف ZIP)", { "مثل خط الثلث الذي لديك" }) { importFont() }
        row(c2, "الأرقام", { if (prefs.arabicDigits) "٠١٢٣٤٥٦٧٨٩" else "0123456789" }) {
            choose("الأرقام", listOf("٠١٢٣٤٥٦٧٨٩", "0123456789"), if (prefs.arabicDigits) 0 else 1) {
                prefs.arabicDigits = it == 0
            }
        }
        list.addView(label("أرقام العدّاد التنازلي تتبع لغة الهاتف.", 11f, muted).apply { setPadding(dpi(4f), dpi(6f), dpi(4f), 0) })

        section("إضافة الويدجت")
        val c3 = card()
        button(c3, "إضافة ويدجت أوقات الصلاة") { pin(PrayerWidget::class.java) }
        button(c3, "إضافة ويدجت التاريخ") { pin(DateWidget::class.java) }
        button(c3, "إضافة ويدجت الآية") { pin(AyahWidget::class.java) }
        button(c3, "إضافة ويدجت التسبيح") { pin(TasbihWidget::class.java) }
        list.addView(label("شاشة القفل: في الهواتف التي تسمح بأدوات التطبيقات الأخرى على شاشة القفل (مثل Nothing OS وAndroid 16)، اضغط مطولاً على شاشة القفل ثم اختر الأدوات، وابحث عن «ويدجت إسلامية» ضمن أدوات التطبيقات الأخرى.", 12f, muted)
            .apply { setPadding(dpi(4f), dpi(8f), dpi(4f), 0) })

        list.addView(label("المصادر: نص القرآن بالرسم العثماني (QuranEnc، مُطابَق مع Tanzil). الخطوط: Amiri وAref Ruqaa وNoto Kufi Arabic وReem Kufi برخصة SIL OFL. أوقات الصلاة: مكتبة Adhan.", 11f, muted)
            .apply { setPadding(dpi(4f), dpi(20f), dpi(4f), 0) })
    }

    private fun adjustText(v: Int) = when {
        v == 0 -> "بدون تعديل"
        v > 0 -> "+${Arabic.digits(v.toString(), prefs.arabicDigits)} يوم"
        else -> "−${Arabic.digits((-v).toString(), prefs.arabicDigits)} يوم"
    }

    private fun choose(title: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), selected) { d, which ->
                onPick(which)
                d.dismiss()
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun refreshPreviewsOnly() {
        refreshers.forEach { it() }
        for ((t, chip) in chips) {
            val on = t == current
            chip.background = rounded(if (on) gold else cardBg, 14f, if (on) null else Color.argb(26, 255, 255, 255))
            chip.setTextColor(if (on) bg else textColor)
        }
        previewBox.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (lightPreview) intArrayOf(Color.rgb(246, 240, 228), Color.rgb(214, 200, 176))
            else intArrayOf(Color.rgb(20, 33, 61), Color.rgb(60, 79, 126), Color.rgb(185, 130, 111)),
        ).apply { cornerRadius = dp(20f) }
        val w = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density) - 56f).coerceAtMost(400f)
        val main = when (current) {
            WidgetType.PRAYER -> Render.prayer(this, prefs, w, 190f)
            WidgetType.DATE -> Render.date(this, prefs, w, 150f)
            WidgetType.AYAH -> Render.ayah(this, prefs, w, 96f)
            WidgetType.TASBIH -> Render.tasbih(this, prefs, 170f, 170f)
        }
        previewMain.setImageBitmap(main)
        val showSmall = current != WidgetType.TASBIH
        previewSmall.visibility = if (showSmall) View.VISIBLE else View.GONE
        previewSmallLabel.visibility = previewSmall.visibility
        if (showSmall) {
            val small = when (current) {
                WidgetType.PRAYER -> Render.prayer(this, prefs, 170f, 170f)
                WidgetType.DATE -> Render.date(this, prefs, 170f, 170f)
                else -> Render.ayah(this, prefs, 170f, 170f)
            }
            previewSmall.setImageBitmap(small)
        }
    }

    private fun refresh() {
        refreshPreviewsOnly()
        Widgets.updateAll(this)
        Scheduler.schedule(this)
    }

    // ---------- Location ----------

    private fun chooseLocation() {
        val items = listOf("موقعي الحالي (تحديد تلقائي)") + cities.map { it.first }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("الموقع")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) useMyLocation()
                else {
                    val c = cities[which - 1]
                    prefs.latitude = c.second
                    prefs.longitude = c.third
                    prefs.cityName = c.first
                    prefs.locationChosen = true
                    refresh()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun useMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            return
        }
        val lm = getSystemService(LocationManager::class.java) ?: return
        val last = lm.getProviders(true)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 6 * 3600_000L) {
            applyLocation(last)
            return
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            if (last != null) applyLocation(last)
            else Toast.makeText(this, "شغّل خدمة الموقع في الهاتف ثم حاول مرة أخرى، أو اختر مدينة.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "جارٍ تحديد الموقع…", Toast.LENGTH_SHORT).show()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, mainExecutor) { loc ->
                    if (loc != null) applyLocation(loc) else if (last != null) applyLocation(last)
                    else Toast.makeText(this, "تعذّر تحديد الموقع. اختر مدينة من القائمة.", Toast.LENGTH_LONG).show()
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(loc: Location) = applyLocation(loc)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, mainLooper)
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "لا يوجد إذن للموقع.", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyLocation(loc: Location) {
        prefs.latitude = loc.latitude
        prefs.longitude = loc.longitude
        prefs.cityName = "موقعي"
        prefs.locationChosen = true
        refresh()
        Thread {
            val name = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale("ar")).getFromLocation(loc.latitude, loc.longitude, 1)
                    ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            } catch (e: Exception) {
                null
            }
            if (!name.isNullOrBlank()) runOnUiThread { prefs.cityName = name; refresh() }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) useMyLocation()
            else {
                Toast.makeText(this, "بدون إذن الموقع، اختر مدينتك من قائمة الموقع.", Toast.LENGTH_LONG).show()
                prefs.locationChosen = true
            }
        }
    }

    // ---------- Font import ----------

    private fun importFont() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, 42)
        } catch (e: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق لاختيار الملفات.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val ok = try { copyFont(uri) } catch (e: Exception) { false }
        if (ok) {
            prefs.importedFontName = displayName(uri)
            prefs.headingFont = 0
            Toast.makeText(this, "تم استيراد الخط.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "الملف ليس خطاً صالحاً (TTF أو OTF أو ZIP يحتوي خطاً).", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun displayName(uri: Uri): String? = contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.substringBeforeLast('.') else null
    }

    /** Copies a TTF/OTF (or the first TTF/OTF inside a ZIP) to app storage and checks that it loads. */
    private fun copyFont(uri: Uri): Boolean {
        val tmp = File(filesDir, "font.tmp")
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        if (bytes.size > 20 * 1024 * 1024) return false
        val fontBytes = if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            fromZip(bytes.inputStream()) ?: return false
        } else bytes
        tmp.writeBytes(fontBytes)
        val tf = Typeface.Builder(tmp).build()
        if (tf == null) { tmp.delete(); return false }
        val dest = Fonts.importedFile(this)
        dest.delete()
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        Fonts.clearImported()
        return Fonts.imported(this) != null
    }

    private fun fromZip(input: InputStream): ByteArray? {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: return null
                val n = e.name.lowercase(Locale.ROOT)
                if (!e.isDirectory && (n.endsWith(".ttf") || n.endsWith(".otf"))) return z.readBytes()
            }
        }
    }

    // ---------- Pin widget ----------

    private fun pin(cls: Class<*>) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, cls), null, null)
        } else {
            Toast.makeText(this, "أضف الويدجت يدوياً: اضغط مطولاً على الشاشة الرئيسية ← الأدوات.", Toast.LENGTH_LONG).show()
        }
    }
}
