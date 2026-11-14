package com.arabtv.iptv.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.arabtv.iptv.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicReference

data class ReceiverStatus(
    val running: Boolean = false,
    val lanPort: Int = -1,
    val lanAddress: String? = null,
    val relayConnected: Boolean = false,
)

/**
 * TV side: listens for commands on the local network (HTTP + DNS-SD advertisement) and over the
 * internet relay, and hands accepted commands to the UI.
 */
class ReceiverHub(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()
    private val _status = MutableStateFlow(ReceiverStatus())
    val status: StateFlow<ReceiverStatus> = _status.asStateFlow()

    private val guard = ReplayGuard()
    private var server: LanServer? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var relayJob: Job? = null
    private val relayConn = AtomicReference<HttpURLConnection?>(null)
    private var config: Pair<String, String>? = null

    val deviceName: String =
        (Build.MODEL ?: "Android TV").trim().ifBlank { "Android TV" }

    @Synchronized
    fun start(relayServer: String, code: String) {
        if (server != null && config == relayServer to code) {
            _status.value = _status.value.copy(lanAddress = localIpv4())
            return
        }
        stop()
        config = relayServer to code
        val s = LanServer(deviceName) { cmd -> deliver(cmd) }
        val port = s.start(Prefs.DEFAULT_PORT..Prefs.DEFAULT_PORT + 8)
        server = s
        _status.value = ReceiverStatus(running = true, lanPort = port, lanAddress = localIpv4())
        if (port > 0) register(port)

        relayJob = scope.launch {
            Relay.subscribe(
                server = relayServer,
                code = code,
                current = relayConn,
                onState = { up -> _status.value = _status.value.copy(relayConnected = up) },
                onEnvelope = { env -> Protocol.open(env, code)?.let { deliver(it) } },
            )
        }
    }

    @Synchronized
    fun stop() {
        relayJob?.cancel()
        relayJob = null
        relayConn.getAndSet(null)?.let { c -> scope.launch { runCatching { c.disconnect() } } }
        server?.stop()
        server = null
        nsdListener?.let { l -> runCatching { nsd().unregisterService(l) } }
        nsdListener = null
        config = null
        _status.value = ReceiverStatus()
    }

    private fun deliver(cmd: Command): Boolean {
        if (!guard.accept(cmd)) return false
        return _commands.tryEmit(cmd)
    }

    private fun nsd() = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private fun register(lanPort: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Arab TV · $deviceName"
            serviceType = Protocol.NSD_TYPE
            port = lanPort
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        try {
            nsd().registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: Exception) {
            Log.w(TAG, "NSD unavailable", e)
        }
    }

    /** The address to type on a phone when automatic discovery does not work. */
    private fun localIpv4(): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    companion object {
        private const val TAG = "ReceiverHub"
    }
}
