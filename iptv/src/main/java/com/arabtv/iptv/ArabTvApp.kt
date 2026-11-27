package com.arabtv.iptv

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.arabtv.iptv.data.ChannelRepository
import com.arabtv.iptv.data.GuideRepository
import com.arabtv.iptv.data.Prefs
import com.arabtv.iptv.remote.ReceiverHub

class ArabTvApp : Application() {
    val isTv: Boolean by lazy { detectTv(this) }
    val prefs: Prefs by lazy { Prefs(this, isTv) }
    val repository: ChannelRepository by lazy { ChannelRepository(this) }
    val receiver: ReceiverHub by lazy { ReceiverHub(this) }
    val guide: GuideRepository by lazy { GuideRepository(this) }
    /** Serves videos from this phone to the TV on the local network. */
    val mediaServer: com.arabtv.iptv.remote.MediaServer by lazy { com.arabtv.iptv.remote.MediaServer(this) }

    companion object {
        fun detectTv(context: Context): Boolean {
            val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    }
}
