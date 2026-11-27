@file:OptIn(UnstableApi::class)

package com.arabtv.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.Http
import com.arabtv.iptv.data.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val channel: Channel? = null,
    val sourceIndex: Int = 0,
    val buffering: Boolean = false,
    val playing: Boolean = false,
    /** Set when every source failed; the UI offers Retry. */
    val error: String? = null,
    /** Shown while falling back to another source. */
    val switching: Boolean = false,
    /** All links failed; looking for fresh ones in the online channel lists. */
    val repairing: Boolean = false,
    /** Height of the video being shown (0 until the first frame). */
    val videoHeight: Int = 0,
)

data class QualityOption(val label: String, val height: Int, val group: Int, val track: Int)

/**
 * Wraps ExoPlayer for live IPTV: per-stream HTTP headers, automatic fallback to a channel's other
 * sources, retry rounds, and channel zapping within the list the channel was opened from.
 */
class PlayerController(context: Context, private val scope: CoroutineScope) {
    @Volatile
    private var headers: Map<String, String> = mapOf("User-Agent" to Http.USER_AGENT)

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(null) // the header below decides; a factory UA would override it
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    // DefaultDataSource adds content:// and file:// (the user's own videos) on top of HTTP.
    private val dataSourceFactory = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, httpFactory)) { spec ->
        spec.withAdditionalHeaders(headers)
    }

    // Assume a fast connection at the start, so playback opens on a sharp variant instead of
    // starting blurry and stepping up over the first seconds.
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(12_000_000L)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
        .setBandwidthMeter(bandwidthMeter)
        .build()
        .apply { playWhenReady = true }

    /** "best": always the top variant; "auto": adapt to the connection; "saver": at most 480p. */
    fun setQualityMode(mode: String) {
        val b = player.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(mode == "best")
        if (mode == "saver") b.setMaxVideoSize(854, 480) else b.clearVideoSizeConstraints()
        player.trackSelectionParameters = b.build()
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var zapList: List<Channel> = emptyList()

    /** The list the current channel was opened from (used for zapping and the side list). */
    val queue: List<Channel> get() = zapList
    private var attempts: List<Pair<Int, String?>> = emptyList() // (source index, mime type)
    private var attemptIndex = 0
    private var round = 0
    private var retryJob: Job? = null
    private var repairTried = false
    private var lastError: PlaybackException? = null

    /**
     * Called once per channel when every link failed. Should look for new links and call
     * [replaceSources] or [repairFailed]. Returns false if no repair can be tried.
     */
    var onAllSourcesFailed: ((Channel) -> Boolean)? = null

    /** Puts a channel's links in the order to try them (best measured quality first). */
    var sourceOrder: ((Channel) -> Channel)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) round = 0
                update()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = update()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.value = _state.value.copy(videoHeight = videoSize.height)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                tryNext(error)
            }
        })
    }

    private fun update() {
        _state.value = _state.value.copy(
            buffering = player.playbackState == Player.STATE_BUFFERING,
            playing = player.isPlaying,
            switching = if (player.playbackState == Player.STATE_READY) false else _state.value.switching,
            repairing = if (player.playbackState == Player.STATE_READY) false else _state.value.repairing,
        )
    }

    fun play(channel: Channel, list: List<Channel>) {
        repairTried = false
        start(sourceOrder?.invoke(channel) ?: channel, list)
    }

    private fun start(channel: Channel, list: List<Channel>) {
        zapList = list.ifEmpty { listOf(channel) }
        retryJob?.cancel()
        round = 0
        attempts = channel.sources.indices
            .filterNot { channel.sources[it].isYoutube }
            .flatMap { i -> mimeAttempts(channel.sources[i].url).map { i to it } }
        attemptIndex = 0
        _state.value = PlaybackState(channel = channel)
        if (attempts.isEmpty()) {
            _state.value = _state.value.copy(error = "No playable link")
            return
        }
        startAttempt()
    }

    /** Auto-repair found [fresh] links for the current channel: play with them. */
    fun replaceSources(fresh: Channel) {
        val current = _state.value.channel ?: return
        if (current.id != fresh.id) return
        start(fresh, zapList.map { if (it.id == fresh.id) fresh else it })
    }

    /** Auto-repair found nothing new: show the error. */
    fun repairFailed() {
        val e = lastError
        _state.value = _state.value.copy(
            repairing = false, switching = false, buffering = false,
            error = e?.let { describe(it) } ?: "No working link",
        )
    }

    fun retry() {
        val ch = _state.value.channel ?: return
        play(ch, zapList)
    }

    /** Moves to the next (+1) or previous (-1) channel of the list. Returns the new channel. */
    fun zap(step: Int): Channel? {
        val ch = _state.value.channel ?: return null
        if (zapList.size < 2) return null
        val i = zapList.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)
        val next = zapList[Math.floorMod(i + step, zapList.size)]
        play(next, zapList)
        return next
    }

    /** Pause / resume. Live channels always resume at the live point, never at old video. */
    fun togglePause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
            player.play()
        }
    }

    /** How far behind live the stream normally plays (measured once it starts), to spot drifting. */
    private var normalOffset = C.TIME_UNSET
    private var catchUpJob: Job? = null

    /**
     * Keeps live channels at the live point: if playback drifts more than a minute behind where the
     * stream normally plays (after long rebuffering, for example), it jumps back to live by itself.
     */
    private fun watchLiveDrift() {
        catchUpJob?.cancel()
        normalOffset = C.TIME_UNSET
        catchUpJob = scope.launch {
            while (true) {
                delay(5_000)
                if (!player.isPlaying || !player.isCurrentMediaItemLive) continue
                val offset = player.currentLiveOffset
                if (offset == C.TIME_UNSET) continue
                if (normalOffset == C.TIME_UNSET) {
                    normalOffset = offset
                } else if (offset > normalOffset + 60_000) {
                    player.seekToDefaultPosition()
                }
            }
        }
    }

    fun stop() {
        retryJob?.cancel()
        catchUpJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = PlaybackState()
    }

    fun release() {
        retryJob?.cancel()
        player.release()
    }

    fun qualities(): List<QualityOption> {
        val out = mutableListOf<QualityOption>()
        player.currentTracks.groups.forEachIndexed { gi, group: Tracks.Group ->
            if (group.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
            for (t in 0 until group.length) {
                if (!group.isTrackSupported(t)) continue
                val f = group.getTrackFormat(t)
                if (f.height <= 0) continue
                out += QualityOption("${f.height}p", f.height, gi, t)
            }
        }
        return out.distinctBy { it.height }.sortedByDescending { it.height }
    }

    /** null = automatic. */
    fun setQuality(option: QualityOption?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            val group = player.currentTracks.groups.getOrNull(option.group) ?: return
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.track))
        }
        player.trackSelectionParameters = params.build()
    }

    private fun startAttempt() {
        val ch = _state.value.channel ?: return
        val (index, mime) = attempts.getOrNull(attemptIndex) ?: return
        val src = ch.sources[index]
        headers = headersFor(src)
        val item = MediaItem.Builder()
            .setUri(src.url)
            .setMediaId(ch.id)
            .setMimeType(mime)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
            .build()
        _state.value = _state.value.copy(sourceIndex = index, error = null)
        player.setMediaItem(item)
        player.prepare()
        player.play()
        watchLiveDrift()
    }

    private fun tryNext(error: PlaybackException) {
        if (attemptIndex + 1 < attempts.size) {
            attemptIndex++
            _state.value = _state.value.copy(switching = true)
            startAttempt()
            return
        }
        if (round < MAX_ROUNDS) {
            round++
            attemptIndex = 0
            _state.value = _state.value.copy(switching = true)
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(3_000L * round)
                startAttempt()
            }
            return
        }
        lastError = error
        val ch = _state.value.channel
        if (!repairTried && ch != null && onAllSourcesFailed?.invoke(ch) == true) {
            repairTried = true
            _state.value = _state.value.copy(repairing = true, switching = false)
            return
        }
        _state.value = _state.value.copy(error = describe(error), switching = false, buffering = false, repairing = false)
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "HTTP error (${e.cause?.message ?: e.errorCodeName})"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network error"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Unsupported stream"
        else -> e.errorCodeName
    }

    companion object {
        private const val MAX_ROUNDS = 2

        fun headersFor(src: StreamSource): Map<String, String> = buildMap {
            put("User-Agent", src.userAgent ?: Http.USER_AGENT)
            src.referrer?.let {
                put("Referer", it)
                // Some CDNs check Origin as well as Referer.
                runCatching { java.net.URI(it) }.getOrNull()?.let { u ->
                    if (u.scheme != null && u.host != null) put("Origin", "${u.scheme}://${u.host}")
                }
            }
        }

        /** Extension-less IPTV URLs are usually HLS; try that first, then let ExoPlayer sniff. */
        fun mimeAttempts(url: String): List<String?> {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.startsWith("content:") || path.startsWith("file:") -> listOf(null)
                path.endsWith(".m3u8") || path.endsWith(".m3u") -> listOf(MimeTypes.APPLICATION_M3U8)
                path.endsWith(".mpd") -> listOf(MimeTypes.APPLICATION_MPD)
                listOf(".mp4", ".ts", ".mkv", ".mov", ".webm", ".3gp", ".m4v", ".avi", ".mp3").any { path.endsWith(it) } -> listOf(null)
                else -> listOf(MimeTypes.APPLICATION_M3U8, null)
            }
        }
    }
}
