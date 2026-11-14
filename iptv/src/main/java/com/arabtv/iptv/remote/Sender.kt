package com.arabtv.iptv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.ArrayDeque

/** Where the phone sends commands. */
sealed class TvTarget {
    abstract val label: String
    abstract val key: String

    data class Lan(override val label: String, val host: String, val port: Int) : TvTarget() {
        override val key get() = "lan:$host:$port"
    }

    data class Remote(override val label: String, val code: String) : TvTarget() {
        override val key get() = "relay:$code"
    }
}

object Sender {
    /** Sends [cmd]; throws IOException with a readable message when the TV can't be reached. */
    suspend fun send(target: TvTarget, cmd: Command, relayServer: String) = withContext(Dispatchers.IO) {
        when (target) {
            is TvTarget.Lan -> {
                val host = if (target.host.contains(':')) "[${target.host}]" else target.host
                val c = Http.open("http://$host:${target.port}/cmd", "POST", 5_000)
                try {
                    val body = cmd.toJson().toByteArray(Charsets.UTF_8)
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.setFixedLengthStreamingMode(body.size)
                    c.outputStream.use { it.write(body) }
                    val code = c.responseCode
                    if (code !in 200..299) throw IOException("TV answered HTTP $code")
                } finally {
                    c.disconnect()
                }
            }
            is TvTarget.Remote -> Relay.publish(relayServer, target.code, cmd.slimForRelay())
        }
    }

    /** Relay messages are capped at 4 KB, so send at most three sources. */
    private fun Command.slimForRelay(): Command =
        copy(channel = channel?.let { ch: Channel -> ch.copy(sources = ch.sources.take(3)) })
}

/** Finds TVs running this app on the local network (DNS-SD / Bonjour). */
class LanDiscovery(private val context: Context) {
    private val _found = MutableStateFlow<List<TvTarget.Lan>>(emptyList())
    val found: StateFlow<List<TvTarget.Lan>> = _found.asStateFlow()

    private val nsd get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized
    fun start() {
        if (listener != null) return
        _found.value = emptyList()
        multicastLock = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("arabtv-discovery").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = enqueue(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.update { list -> list.filterNot { it.label == info.serviceName.cleanName() } }
            }
        }
        try {
            nsd.discoverServices(Protocol.NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
            listener = l
        } catch (e: Exception) {
            Log.w(TAG, "NSD unavailable", e)
        }
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        synchronized(resolveQueue) { resolveQueue.clear(); resolving = false }
    }

    // Older Android versions allow only one resolve at a time, so resolve one by one.
    private fun enqueue(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.add(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            val n = resolveQueue.poll()
            if (n == null) resolving = false
            n
        } ?: return
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = resolveNext()
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    if (host != null && info.port > 0) {
                        val tv = TvTarget.Lan(info.serviceName.cleanName(), host, info.port)
                        _found.update { list -> list.filterNot { it.label == tv.label || it.key == tv.key } + tv }
                    }
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            resolveNext()
        }
    }

    private fun String.cleanName() = removePrefix("Arab TV · ").ifBlank { this }

    companion object {
        private const val TAG = "LanDiscovery"
    }
}
