package com.arabtv.iptv.remote

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

object Net {
    /** This device's IPv4 address on the local network (Wi-Fi or Ethernet), or null. */
    fun localIpv4(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()
}
