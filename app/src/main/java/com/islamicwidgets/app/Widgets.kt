package com.islamicwidgets.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.Instant

abstract class BaseWidget : AppWidgetProvider() {
    abstract fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) safeUpdate(ctx, mgr, id)
        Scheduler.schedule(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        safeUpdate(ctx, mgr, id)
    }

    override fun onEnabled(ctx: Context) = Scheduler.schedule(ctx)
    override fun onDisabled(ctx: Context) = Scheduler.schedule(ctx)

    fun safeUpdate(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            mgr.updateAppWidget(id, build(ctx, mgr, id))
        } catch (e: Exception) {
            android.util.Log.e("IslamicWidgets", "update failed", e)
        }
    }
}

class PrayerWidget : BaseWidget() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val prefs = Prefs(ctx)
        val (w, h) = Widgets.sizeDp(ctx, mgr, id, 340f, 200f)
        val now = Instant.now()
        val st = Prayer.state(prefs, now)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)
        rv.setImageViewBitmap(R.id.img, Render.prayer(ctx, prefs, w, h, now))
        rv.setContentDescription(
            R.id.img,
            "الصلاة القادمة ${Arabic.prayerNames[st.nextIndex]} " +
                Arabic.time12(Prayer.localTime(st.nextAt), prefs.arabicDigits),
        )
        val base = SystemClock.elapsedRealtime() + (st.nextAt.toEpochMilli() - now.toEpochMilli())
        rv.setChronometer(R.id.countdown, base, null, true)
        rv.setChronometerCountDown(R.id.countdown, true)
        rv.setOnClickPendingIntent(R.id.root, Widgets.openSettings(ctx))
        return rv
    }
}

class DateWidget : BaseWidget() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val prefs = Prefs(ctx)
        val (w, h) = Widgets.sizeDp(ctx, mgr, id, 340f, 150f)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_image)
        rv.setImageViewBitmap(R.id.img, Render.date(ctx, prefs, w, h))
        rv.setOnClickPendingIntent(R.id.root, Widgets.openSettings(ctx))
        return rv
    }
}

class AyahWidget : BaseWidget() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val prefs = Prefs(ctx)
        val (w, h) = Widgets.sizeDp(ctx, mgr, id, 340f, 96f)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_image)
        rv.setImageViewBitmap(R.id.img, Render.ayah(ctx, prefs, w, h))
        val a = Ayat.list[Render.ayahIndex(prefs)]
        rv.setContentDescription(R.id.img, a.text)
        rv.setOnClickPendingIntent(R.id.root, Widgets.broadcast(ctx, UpdateReceiver.ACTION_NEXT_AYAH, 1))
        return rv
    }
}

class TasbihWidget : BaseWidget() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val prefs = Prefs(ctx)
        val (w, h) = Widgets.sizeDp(ctx, mgr, id, 160f, 160f)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_image)
        rv.setImageViewBitmap(R.id.img, Render.tasbih(ctx, prefs, w, h))
        rv.setContentDescription(R.id.img, Render.tasbihPhrase(prefs))
        rv.setOnClickPendingIntent(R.id.root, Widgets.broadcast(ctx, UpdateReceiver.ACTION_TASBIH, 2))
        return rv
    }
}

object Widgets {
    val providers = listOf(
        PrayerWidget::class.java, DateWidget::class.java, AyahWidget::class.java, TasbihWidget::class.java,
    )

    /** Current widget size in dp: portrait uses min width × max height, landscape the opposite. */
    fun sizeDp(ctx: Context, mgr: AppWidgetManager, id: Int, defW: Float, defH: Float): Pair<Float, Float> {
        val o = mgr.getAppWidgetOptions(id)
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return Pair(if (w > 0) w.toFloat() else defW, if (h > 0) h.toFloat() else defH)
    }

    fun openSettings(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, SettingsActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun broadcast(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, UpdateReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ids(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    fun hasAny(ctx: Context) = providers.any { ids(ctx, it).isNotEmpty() }

    fun update(ctx: Context, cls: Class<out BaseWidget>) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val provider = cls.getDeclaredConstructor().newInstance()
        for (id in ids(ctx, cls)) provider.safeUpdate(ctx, mgr, id)
    }

    fun updateAll(ctx: Context) {
        for (cls in providers) update(ctx, cls)
    }
}
