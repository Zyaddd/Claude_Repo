package com.arabtv.iptv.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.arabtv.iptv.ArabTvApp
import com.arabtv.iptv.R
import com.arabtv.iptv.ui.MainActivity

/**
 * Keeps the phone serving a video to the TV while its screen is off: a foreground service with
 * a "Stop" button, Wi-Fi and CPU kept awake. Stops by itself when the TV has not asked for data
 * for 10 minutes.
 */
class MediaShareService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val idleCheck = object : Runnable {
        override fun run() {
            val server = (application as ArabTvApp).mediaServer
            if (System.currentTimeMillis() - server.lastRequestAt > IDLE_MS) {
                stopSharing()
            } else {
                handler.postDelayed(this, 60_000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.share_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MediaShareService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tv)
            .setContentTitle(getString(R.string.share_notification))
            .setContentText(getString(R.string.share_notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "arabtv:share").apply { acquire() }
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arabtv:share").apply { acquire(6 * 60 * 60 * 1000L) }
        }
        handler.removeCallbacks(idleCheck)
        handler.postDelayed(idleCheck, 60_000)
        return START_NOT_STICKY
    }

    private fun stopSharing() {
        (application as ArabTvApp).mediaServer.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        runCatching { wifiLock?.release() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "share"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.arabtv.iptv.STOP_SHARING"
        private const val IDLE_MS = 10 * 60 * 1000L

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, MediaShareService::class.java).setAction(ACTION_STOP)) }
        }

        fun start(context: Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, MediaShareService::class.java))
            }
        }
    }
}
