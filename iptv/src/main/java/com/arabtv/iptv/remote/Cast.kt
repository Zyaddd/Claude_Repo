package com.arabtv.iptv.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arabtv.iptv.data.Channel
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Google Cast: Chromecast and Android TVs with Chromecast built-in, using the default receiver. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

class CastHelper(context: Context) {
    /** Null where Google Play services (and so Cast) is missing, e.g. on some TVs and emulators. */
    val castContext: CastContext? = try {
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance(context.applicationContext)
    } catch (e: Throwable) {
        Log.i(TAG, "Cast unavailable: ${e.message}")
        null
    }

    private val _deviceName = MutableStateFlow<String?>(null)
    /** Name of the connected Cast device, or null when not casting. */
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    /** Called with the newly connected session, so the current channel can be handed over. */
    var onConnected: (() -> Unit)? = null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = connected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connected(session)
        override fun onSessionEnded(session: CastSession, error: Int) { _deviceName.value = null }
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) { _deviceName.value = null }
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) { _deviceName.value = null }
    }

    init {
        castContext?.sessionManager?.let { sm ->
            sm.addSessionManagerListener(listener, CastSession::class.java)
            sm.currentCastSession?.takeIf { it.isConnected }?.let { s ->
                _deviceName.value = s.castDevice?.friendlyName ?: "Chromecast"
            }
        }
    }

    private fun connected(session: CastSession) {
        _deviceName.value = session.castDevice?.friendlyName ?: "Chromecast"
        onConnected?.invoke()
    }

    private fun session(): CastSession? = castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }

    /** Loads [channel] on the Cast device. Returns false if no session is connected. */
    fun play(channel: Channel, title: String, sourceIndex: Int = 0): Boolean {
        val client = session()?.remoteMediaClient ?: return false
        val src = channel.sources.getOrNull(sourceIndex) ?: channel.sources.first()
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            channel.logo?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(src.url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentType(src.url))
            .setMetadata(meta)
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
        return true
    }

    fun togglePause() {
        val client = session()?.remoteMediaClient ?: return
        if (client.isPlaying) client.pause() else client.play()
    }

    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private fun contentType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".ts") -> "video/mp2t"
            else -> "application/x-mpegURL"
        }
    }

    companion object {
        private const val TAG = "CastHelper"
    }
}
