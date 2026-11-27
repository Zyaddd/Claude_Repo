package com.arabtv.iptv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arabtv.iptv.data.Prefs
import com.arabtv.iptv.ui.MainActivity

/**
 * "Start with the TV": opens the app when the device boots. Android 10+ only lets an app open
 * itself from the background when it may "display over other apps", which Settings asks for.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val on = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).getBoolean(Prefs.START_ON_BOOT, false)
        if (!on) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Log.w("BootReceiver", "could not start: ${e.message}")
        }
    }
}
