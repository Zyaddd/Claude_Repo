package com.islamicwidgets.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps widgets current: an exact alarm at the next prayer time or midnight (whichever is first),
 * plus an inexact 15-minute refresh (no wake-up) for the progress bar.
 */
object Scheduler {
    private fun pending(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, UpdateReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val exact = pending(ctx, UpdateReceiver.ACTION_REFRESH, 10)
        val periodic = pending(ctx, UpdateReceiver.ACTION_PERIODIC, 11)
        if (!Widgets.hasAny(ctx)) {
            am.cancel(exact)
            am.cancel(periodic)
            return
        }
        val prefs = Prefs(ctx)
        val st = Prayer.state(prefs)
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val at = minOf(st.nextAt, midnight).toEpochMilli() + 1_000L
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, exact)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, exact)
        am.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            periodic,
        )
    }
}

/** Our own alarms and widget taps (not exported). */
class UpdateReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REFRESH = "com.islamicwidgets.app.REFRESH"
        const val ACTION_PERIODIC = "com.islamicwidgets.app.PERIODIC"
        const val ACTION_NEXT_AYAH = "com.islamicwidgets.app.NEXT_AYAH"
        const val ACTION_TASBIH = "com.islamicwidgets.app.TASBIH"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        when (intent.action) {
            ACTION_NEXT_AYAH -> {
                prefs.ayahOffset = prefs.ayahOffset + 1
                Widgets.update(ctx, AyahWidget::class.java)
            }
            ACTION_TASBIH -> {
                prefs.tasbihCount = (prefs.tasbihCount + 1) % 100
                Widgets.update(ctx, TasbihWidget::class.java)
            }
            ACTION_PERIODIC -> Widgets.update(ctx, PrayerWidget::class.java)
            else -> {
                Widgets.updateAll(ctx)
                Scheduler.schedule(ctx)
            }
        }
    }
}

/** System events that change times or dates (exported, but these are protected system broadcasts). */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Widgets.updateAll(ctx)
        Scheduler.schedule(ctx)
    }
}
