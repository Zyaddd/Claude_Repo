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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
    private lateinit var prayerPreview: ImageView
    private lateinit var datePreview: ImageView
    private lateinit var ayahPreview: ImageView

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

    private fun preview(heightDp: Float): ImageView {
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true }
        list.addView(iv, LinearLayout.LayoutParams(-1, dpi(heightDp)).apply { setMargins(0, dpi(6f), 0, dpi(6f)) })
        return iv
    }

    private fun buildUi() {
        list.addView(label("ويدجت إسلامية", 34f, gold, Fonts.ruqaa(this)).apply { setPadding(dpi(4f), 0, dpi(4f), 0) })
        list.addView(label("اضغط مطولاً على الشاشة الرئيسية ← الأدوات (Widgets) ← ويدجت إسلامية", 12f, muted)
            .apply { setPadding(dpi(4f), dpi(2f), dpi(4f), dpi(4f)) })

        section("معاينة")
        prayerPreview = preview(190f)
        datePreview = preview(150f)
        ayahPreview = preview(96f)

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

        section("المظهر")
        val c2 = card()
        val opLabel = label("", 15f, textColor).apply { setPadding(0, dpi(12f), 0, 0) }
        c2.addView(opLabel)
        refreshers += { opLabel.text = "شفافية الزجاج: ${Arabic.digits(prefs.glassOpacity.toString(), prefs.arabicDigits)}٪" }
        c2.addView(SeekBar(this).apply {
            max = 55
            progress = prefs.glassOpacity - 5
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minimumHeight = dpi(44f)
            progressTintList = android.content.res.ColorStateList.valueOf(gold)
            thumbTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.glassOpacity = p + 5; refreshPreviewsOnly() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) = refresh()
            })
        })
        val tints = listOf("زجاج داكن", "زجاج فاتح", "لون من الخلفية")
        row(c2, "لون الزجاج", { tints[prefs.glassTint.coerceIn(0, 2)] }) {
            choose("لون الزجاج", tints, prefs.glassTint) { prefs.glassTint = it }
        }
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

        section("إضافة إلى الشاشة الرئيسية")
        val c3 = card()
        button(c3, "إضافة ويدجت أوقات الصلاة") { pin(PrayerWidget::class.java) }
        button(c3, "إضافة ويدجت التاريخ") { pin(DateWidget::class.java) }
        button(c3, "إضافة ويدجت الآية") { pin(AyahWidget::class.java) }
        button(c3, "إضافة ويدجت التسبيح") { pin(TasbihWidget::class.java) }

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
        val w = (resources.displayMetrics.widthPixels / resources.displayMetrics.density) - 32f
        val pw = w.coerceAtMost(420f)
        prayerPreview.setImageBitmap(Render.prayer(this, prefs, pw, 190f))
        datePreview.setImageBitmap(Render.date(this, prefs, pw, 150f))
        ayahPreview.setImageBitmap(Render.ayah(this, prefs, pw, 96f))
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
