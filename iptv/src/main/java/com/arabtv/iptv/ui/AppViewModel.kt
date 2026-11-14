package com.arabtv.iptv.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabtv.iptv.ArabTvApp
import com.arabtv.iptv.data.Category
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.Http
import com.arabtv.iptv.data.ManualTv
import com.arabtv.iptv.data.PairedTv
import com.arabtv.iptv.data.Pairing
import com.arabtv.iptv.data.Settings
import com.arabtv.iptv.data.UserPlaylist
import com.arabtv.iptv.player.PlayerController
import com.arabtv.iptv.remote.Action
import com.arabtv.iptv.remote.CastHelper
import com.arabtv.iptv.remote.Command
import com.arabtv.iptv.remote.LanDiscovery
import com.arabtv.iptv.remote.Sender
import com.arabtv.iptv.remote.TvTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Screen { HOME, PLAYER, SETTINGS }

enum class Tab { NEWS, DOCUMENTARY, KIDS, ALL, FAVORITES, RECENT, PLAYLISTS }

/** Messages for the snackbar; resolved to text in the UI so they follow the app language. */
sealed class UiMessage {
    data class SentTo(val tv: String) : UiMessage()
    data class SendFailed(val tv: String, val reason: String) : UiMessage()
    data class FromPhone(val from: String) : UiMessage()
    data class Casting(val device: String) : UiMessage()
    data object BadCode : UiMessage()
    data object BadPlaylist : UiMessage()
    data object WrongPin : UiMessage()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val arabTv = app as ArabTvApp
    val isTv = arabTv.isTv
    private val prefs = arabTv.prefs
    private val repo = arabTv.repository
    val receiver = arabTv.receiver

    val settings: StateFlow<Settings> = prefs.state
    val channels: StateFlow<List<Channel>> = repo.channels
    val loadFailures = repo.failures

    val playback = PlayerController(app, viewModelScope)
    val cast = CastHelper(app)
    val discovery = LanDiscovery(app)

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _tab = MutableStateFlow(Tab.NEWS)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** channel id -> reachable? Missing = not checked yet. */
    private val _health = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val health: StateFlow<Map<String, Boolean>> = _health.asStateFlow()

    /** The TV this phone is currently controlling (null = play here). */
    private val _target = MutableStateFlow<TvTarget?>(null)
    val target: StateFlow<TvTarget?> = _target.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepJob: Job? = null

    /** Kids mode is unlocked for this session after the PIN is entered. */
    private val _kidsUnlocked = MutableStateFlow(false)
    val kidsUnlocked: StateFlow<Boolean> = _kidsUnlocked.asStateFlow()

    /** Whether the player's on-screen controls are showing (TV uses it to decide what D-pad keys do). */
    @Volatile var controllerVisible = false
    @Volatile var inPip = false

    private var healthJob: Job? = null
    private var autoplayDone = false

    init {
        viewModelScope.launch {
            repo.loadOffline(settings.value.playlists)
            maybeAutoplay()
            refresh()
        }
        viewModelScope.launch {
            receiver.commands.collect { handleCommand(it) }
        }
        cast.onConnected = {
            val ch = playback.state.value.channel
            if (ch != null) {
                cast.play(ch, title(ch), playback.state.value.sourceIndex)
                playback.player.pause()
            }
            cast.deviceName.value?.let { _messages.tryEmit(UiMessage.Casting(it)) }
        }
        if (settings.value.kidsMode) _tab.value = Tab.KIDS
    }

    fun title(ch: Channel): String = ch.displayName(isArabicUi())

    /** The in-app language choice if set, else the device language. */
    private fun isArabicUi(): Boolean {
        val app = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val lang = if (!app.isEmpty) app[0]?.language else java.util.Locale.getDefault().language
        return lang == "ar"
    }

    // ---- Catalog -------------------------------------------------------------------------------

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repo.refresh(settings.value.playlists)
            } finally {
                _refreshing.value = false
            }
            if (settings.value.checkStreams) checkStreams()
        }
    }

    /** Probes every channel (8 at a time) and records which ones answer. */
    fun checkStreams() {
        healthJob?.cancel()
        _health.value = emptyMap()
        val list = channels.value
        healthJob = viewModelScope.launch(Dispatchers.IO) {
            val gate = Semaphore(8)
            val jobs = list.map { ch ->
                launch {
                    gate.withPermit {
                        val ok = ch.sources.take(2).any { Http.probe(it) }
                        _health.update { it + (ch.id to ok) }
                    }
                }
            }
            jobs.forEach { it.join() }
        }
    }

    fun setTab(t: Tab) { _tab.value = t }
    fun setQuery(q: String) { _query.value = q }

    fun visibleChannels(
        all: List<Channel>,
        tab: Tab,
        query: String,
        s: Settings,
        health: Map<String, Boolean>,
        kidsUnlocked: Boolean,
    ): List<Channel> {
        val effectiveTab = if (s.kidsMode && !kidsUnlocked) Tab.KIDS else tab
        var list = when (effectiveTab) {
            Tab.NEWS -> all.filter { Category.NEWS in it.categories && it.playlist == null }
            Tab.DOCUMENTARY -> all.filter { Category.DOCUMENTARY in it.categories && it.playlist == null }
            Tab.KIDS -> all.filter { Category.KIDS in it.categories && (it.playlist == null || s.kidsMode) }
            Tab.ALL -> all.filter { it.playlist == null }
            Tab.PLAYLISTS -> all.filter { it.playlist != null }
            Tab.FAVORITES -> s.favorites.mapNotNull { id -> all.firstOrNull { it.id == id } }
            Tab.RECENT -> s.recents.mapNotNull { id -> all.firstOrNull { it.id == id } }
        }
        if (s.hideGeoBlocked) list = list.filterNot { it.geoBlocked }
        if (s.hideOffline) list = list.filter { health[it.id] != false }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || (it.nameAr?.contains(q) == true) || (it.country?.lowercase() == q)
            }
        }
        if (effectiveTab != Tab.FAVORITES && effectiveTab != Tab.RECENT) {
            // Working streams first, then unknown, then dead; favorites float to the top.
            val favs = s.favorites.toSet()
            list = list.sortedWith(
                compareBy<Channel>(
                    { if (it.id in favs) 0 else 1 },
                    { when (health[it.id]) { true -> 0; null -> 1; false -> 2 } },
                ),
            )
        }
        return list
    }

    // ---- Playback ------------------------------------------------------------------------------

    /** Plays [ch] wherever playback currently goes: a connected Cast device, a chosen TV, or here. */
    fun open(ch: Channel, list: List<Channel>) {
        prefs.addRecent(ch.id)
        val tv = _target.value
        when {
            cast.deviceName.value != null -> {
                cast.play(ch, title(ch))
                playback.play(ch, list)
                playback.player.pause()
                _screen.value = Screen.PLAYER
            }
            tv != null -> sendToTv(tv, ch)
            else -> {
                playback.play(ch, list)
                _screen.value = Screen.PLAYER
            }
        }
    }

    fun zap(step: Int) {
        val next = playback.zap(step) ?: return
        prefs.addRecent(next.id)
        if (cast.deviceName.value != null) {
            cast.play(next, title(next))
            playback.player.pause()
        }
    }

    private val _showOptions = MutableStateFlow(false)
    val showOptions: StateFlow<Boolean> = _showOptions.asStateFlow()
    fun setShowOptions(show: Boolean) { _showOptions.value = show }

    /** Jumps to [ch] within the current list (player side list / options panel). */
    fun zapTo(ch: Channel) {
        prefs.addRecent(ch.id)
        playback.play(ch, playback.queue)
        if (cast.deviceName.value != null) {
            cast.play(ch, title(ch))
            playback.player.pause()
        }
    }

    fun closePlayer() {
        _showOptions.value = false
        playback.stop()
        _screen.value = Screen.HOME
    }

    fun openSettings() { _screen.value = Screen.SETTINGS }
    fun goHome() { _screen.value = Screen.HOME }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepAt.value = null
            return
        }
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            _sleepAt.value = null
            closePlayer()
        }
    }

    private fun maybeAutoplay() {
        if (autoplayDone) return
        autoplayDone = true
        val s = settings.value
        if (!s.autoplayLast || s.kidsMode) return
        val ch = channels.value.firstOrNull { it.id == s.lastChannel } ?: return
        playback.play(ch, channels.value.filter { c -> c.categories.any { it in ch.categories } && c.playlist == ch.playlist })
        _screen.value = Screen.PLAYER
    }

    // ---- Phone -> TV ----------------------------------------------------------------------------

    fun selectTarget(t: TvTarget?) { _target.value = t }

    fun sendToTv(tv: TvTarget, ch: Channel) {
        _target.value = tv
        if (_screen.value == Screen.PLAYER) playback.player.pause()
        send(tv, Command(Action.PLAY, channel = ch, from = deviceLabel()))
    }

    fun remote(action: Action) {
        val tv = _target.value ?: return
        send(tv, Command(action, from = deviceLabel()))
    }

    private fun send(tv: TvTarget, cmd: Command) {
        viewModelScope.launch {
            try {
                Sender.send(tv, cmd, settings.value.relayServer)
                if (cmd.action == Action.PLAY) _messages.tryEmit(UiMessage.SentTo(tv.label))
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage.SendFailed(tv.label, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun deviceLabel() = (android.os.Build.MODEL ?: "Phone").take(40)

    fun addPairedTv(name: String, rawCode: String): Boolean {
        val code = Pairing.normalize(rawCode)
        if (code.length != Pairing.CODE_LENGTH) {
            _messages.tryEmit(UiMessage.BadCode)
            return false
        }
        val label = name.trim().ifBlank { "TV " + code.take(4) }
        prefs.edit { s -> s.copy(pairedTvs = s.pairedTvs.filterNot { it.code == code } + PairedTv(label, code)) }
        _target.value = TvTarget.Remote(label, code)
        return true
    }

    fun removePairedTv(tv: PairedTv) = prefs.edit { s -> s.copy(pairedTvs = s.pairedTvs - tv) }

    fun addManualTv(name: String, address: String): Boolean {
        val a = address.trim().removePrefix("http://").trimEnd('/')
        val hasPort = a.count { it == ':' } == 1
        val host = if (hasPort) a.substringBefore(':') else a
        val port = if (hasPort) a.substringAfter(':').toIntOrNull() ?: -1 else com.arabtv.iptv.data.Prefs.DEFAULT_PORT
        if (host.isBlank() || port !in 1..65535) return false
        val tv = ManualTv(name.trim().ifBlank { host }, host, port)
        prefs.edit { s -> s.copy(manualTvs = s.manualTvs.filterNot { it.host == host && it.port == port } + tv) }
        _target.value = TvTarget.Lan(tv.name, host, port)
        return true
    }

    fun removeManualTv(tv: ManualTv) = prefs.edit { s -> s.copy(manualTvs = s.manualTvs - tv) }

    // ---- TV receiver --------------------------------------------------------------------------

    fun startReceiver() {
        val s = settings.value
        if (s.receiverEnabled) receiver.start(s.relayServer, s.pairingCode) else receiver.stop()
    }

    fun stopReceiver() = receiver.stop()

    private fun handleCommand(cmd: Command) {
        viewModelScope.launch(Dispatchers.Main) {
            when (cmd.action) {
                Action.PLAY -> {
                    val ch = cmd.channel ?: return@launch
                    if (settings.value.kidsMode && !_kidsUnlocked.value && Category.KIDS !in ch.categories) return@launch
                    // Prefer our own copy of the channel (it has every source); zap within its category.
                    val local = channels.value.firstOrNull { it.id == ch.id } ?: ch
                    val list = channels.value.filter { c ->
                        c.playlist == local.playlist && c.categories.any { it in local.categories }
                    }.ifEmpty { listOf(local) }
                    prefs.addRecent(local.id)
                    playback.play(local, if (list.any { it.id == local.id }) list else listOf(local))
                    _screen.value = Screen.PLAYER
                    if (cmd.from.isNotBlank()) _messages.tryEmit(UiMessage.FromPhone(cmd.from))
                }
                Action.TOGGLE -> playback.togglePause()
                Action.STOP -> closePlayer()
                Action.NEXT -> zap(1)
                Action.PREV -> zap(-1)
                Action.VOL_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
                Action.VOL_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
                Action.MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            }
        }
    }

    private fun adjustVolume(direction: Int) {
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    // ---- Settings -----------------------------------------------------------------------------

    fun updateSettings(change: (Settings) -> Settings) {
        val before = settings.value
        prefs.edit(change)
        val after = settings.value
        if (before.receiverEnabled != after.receiverEnabled || before.relayServer != after.relayServer ||
            before.pairingCode != after.pairingCode
        ) startReceiver()
    }

    fun toggleFavorite(id: String) = prefs.toggleFavorite(id)

    fun newPairingCode() {
        prefs.newPairingCode()
        startReceiver()
    }

    fun addPlaylist(name: String, url: String): Boolean {
        val u = url.trim()
        if (!com.arabtv.iptv.data.StreamSource.isPlayableUrl(u)) {
            _messages.tryEmit(UiMessage.BadPlaylist)
            return false
        }
        val p = UserPlaylist(name.trim().ifBlank { u.substringAfter("://").substringBefore('/') }, u)
        prefs.edit { s -> s.copy(playlists = s.playlists.filterNot { it.url == u } + p) }
        refresh()
        return true
    }

    fun removePlaylist(p: UserPlaylist) {
        prefs.edit { s -> s.copy(playlists = s.playlists - p) }
        repo.forget(p)
        refresh()
    }

    fun clearHistory() = prefs.edit { it.copy(recents = emptyList()) }

    /** Turning kids mode on sets the PIN; turning it off (or leaving it) needs the PIN. */
    fun enableKidsMode(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        prefs.edit { it.copy(kidsMode = true, kidsPin = pin) }
        _kidsUnlocked.value = false
        _tab.value = Tab.KIDS
        return true
    }

    fun unlockKids(pin: String, disable: Boolean): Boolean {
        if (pin != settings.value.kidsPin) {
            _messages.tryEmit(UiMessage.WrongPin)
            return false
        }
        if (disable) prefs.edit { it.copy(kidsMode = false) } else _kidsUnlocked.value = true
        return true
    }

    fun lockKids() {
        _kidsUnlocked.value = false
        _tab.value = Tab.KIDS
        _screen.value = Screen.HOME
    }

    override fun onCleared() {
        discovery.stop()
        playback.release()
        super.onCleared()
    }

}
