package com.arabtv.iptv.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabtv.iptv.ArabTvApp
import com.arabtv.iptv.data.Catalog
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
import com.arabtv.iptv.data.GuideRepository
import com.arabtv.iptv.data.ProbeResult
import com.arabtv.iptv.data.Programme
import com.arabtv.iptv.data.StreamQuality
import com.arabtv.iptv.data.StreamSource
import com.arabtv.iptv.library.Library
import com.arabtv.iptv.library.LibraryAction
import com.arabtv.iptv.library.LibraryItem
import com.arabtv.iptv.library.LibrarySection
import com.arabtv.iptv.library.LocalVideo
import com.arabtv.iptv.update.Release
import com.arabtv.iptv.update.UpdateState
import com.arabtv.iptv.update.Updater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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

/** The pages of the home screen's top navigation. */
enum class Section { HOME, NEWS, DOCUMENTARY, KIDS, QURAN, LIBRARY, MY_LIST, ALL, PLAYLISTS, SEARCH }

/** Why a command did not reach a TV, so the message can say what to check. */
enum class SendError {
    /** Something is at that address but nothing listens on the port: the app is closed or receiving is off. */
    REFUSED,
    /** Nothing answered: wrong address, TV asleep or off Wi-Fi, or the router isolates Wi-Fi devices. */
    NO_ANSWER,
    OTHER,
}

fun classifySendError(e: Throwable): SendError {
    val text = generateSequence(e) { it.cause }.take(6)
        .joinToString(" ") { "${it.javaClass.simpleName} ${it.message}" }.lowercase()
    return when {
        "refused" in text -> SendError.REFUSED
        "timeout" in text || "timed out" in text || "unreachable" in text || "no route" in text ->
            SendError.NO_ANSWER
        else -> SendError.OTHER
    }
}

/** Messages for the snackbar; resolved to text in the UI so they follow the app language. */
sealed class UiMessage {
    data class SentTo(val tv: String) : UiMessage()
    data class SendFailed(val tv: String, val reason: String, val kind: SendError) : UiMessage()
    data class FromPhone(val from: String) : UiMessage()
    data class Casting(val device: String) : UiMessage()
    data class ChannelsAdded(val count: Int) : UiMessage()
    data object NoNewChannels : UiMessage()
    data object BadCode : UiMessage()
    data object BadPlaylist : UiMessage()
    data object WrongPin : UiMessage()
    data object NoApp : UiMessage()
    data object NoChannelNumber : UiMessage()
}

/** Category order used for channel numbers and the home rows. */
private fun Channel.rank(): Int = when {
    Category.NEWS in categories -> 0
    Category.DOCUMENTARY in categories -> 1
    Category.KIDS in categories -> 2
    Category.QURAN in categories -> 3
    else -> 4
}

/** What kids mode lets through without the PIN: cartoons and Quran. */
fun Channel.allowedForKids(): Boolean = Category.KIDS in categories || Category.QURAN in categories

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val arabTv = app as ArabTvApp
    val isTv = arabTv.isTv
    private val prefs = arabTv.prefs
    private val repo = arabTv.repository
    private val guideRepo = arabTv.guide
    val receiver = arabTv.receiver

    val settings: StateFlow<Settings> = prefs.state
    val loadFailures = repo.failures
    val guide: StateFlow<Map<String, List<Programme>>> = guideRepo.guide
    val updateState: StateFlow<UpdateState> = Updater.state

    /**
     * Channels the user sees: everything from the built-in lists they have accepted (new ones wait
     * under "Find new channels"), minus hidden ones, plus their own playlists. Sorted News,
     * Documentary, Cartoons, Other; the position is the channel number.
     */
    val catalog: StateFlow<List<Channel>> = combine(repo.channels, prefs.state) { all, s ->
        val known = s.knownIds
        all.filter { ch ->
            ch.id !in s.hiddenIds &&
                (ch.playlist != null || known == null || ch.id in known) &&
                !(s.hideGeoBlocked && ch.geoBlocked)
        }.sortedWith(compareBy<Channel>({ if (it.playlist == null) 0 else 1 }, { it.rank() }))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Built-in channels found online that the user has not added yet. */
    val pendingChannels: StateFlow<List<Channel>> = combine(repo.channels, prefs.state) { all, s ->
        val known = s.knownIds ?: return@combine emptyList()
        all.filter { it.playlist == null && it.id !in known && it.id !in s.hiddenIds }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every channel, hidden ones included (for the "Hidden channels" screen). */
    val allChannels: StateFlow<List<Channel>> = repo.channels

    val playback = PlayerController(app, viewModelScope)
    val cast = CastHelper(app)
    val discovery = LanDiscovery(app)

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _section = MutableStateFlow(Section.HOME)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** channel id -> reachable? Missing = not checked yet. */
    private val _health = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val health: StateFlow<Map<String, Boolean>> = _health.asStateFlow()

    /** link url -> what the last check found (alive, best resolution). */
    private val probes = java.util.concurrent.ConcurrentHashMap<String, ProbeResult>()

    /** channel id -> best resolution measured among its working links (for the quality badge). */
    private val _quality = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quality: StateFlow<Map<String, Int>> = _quality.asStateFlow()

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

    /** Digits typed on the remote, waiting to become a channel number. */
    private val _numberEntry = MutableStateFlow("")
    val numberEntry: StateFlow<String> = _numberEntry.asStateFlow()
    private var numberJob: Job? = null

    /** Shown after "Find new channels": the channels to choose from (null = dialog closed). */
    private val _newChannelsDialog = MutableStateFlow<List<Channel>?>(null)
    val newChannelsDialog: StateFlow<List<Channel>?> = _newChannelsDialog.asStateFlow()

    private val _finding = MutableStateFlow(false)
    val finding: StateFlow<Boolean> = _finding.asStateFlow()

    val librarySections: List<LibrarySection> by lazy { Library.load(app) }
    private val _localVideos = MutableStateFlow<List<LocalVideo>?>(null)
    val localVideos: StateFlow<List<LocalVideo>?> = _localVideos.asStateFlow()

    private val _updatePromptDismissed = MutableStateFlow(false)
    val updatePromptDismissed: StateFlow<Boolean> = _updatePromptDismissed.asStateFlow()

    /** True after "Check for updates" was pressed: then "up to date" and errors are shown too. */
    private val _manualUpdateCheck = MutableStateFlow(false)
    val manualUpdateCheck: StateFlow<Boolean> = _manualUpdateCheck.asStateFlow()

    /** Whether the player's on-screen controls are showing (TV uses it to decide what D-pad keys do). */
    @Volatile var controllerVisible = false
    @Volatile var inPip = false

    private var healthJob: Job? = null
    private var autoplayDone = false

    init {
        viewModelScope.launch {
            repo.loadOffline(settings.value.playlists)
            seedKnownChannels()
            maybeAutoplay()
            refresh()
        }
        viewModelScope.launch {
            receiver.commands.collect { handleCommand(it) }
        }
        // Keep links fresh while the app stays open (TVs often run it for hours).
        viewModelScope.launch {
            while (true) {
                delay(REFRESH_EVERY)
                refresh()
            }
        }
        viewModelScope.launch {
            if (settings.value.autoUpdate) {
                delay(8_000)
                Updater.check()
            }
        }
        cast.onConnected = {
            val ch = playback.state.value.channel
            if (ch != null) {
                cast.play(ch, title(ch), playback.state.value.sourceIndex)
                playback.player.pause()
            }
            cast.deviceName.value?.let { _messages.tryEmit(UiMessage.Casting(it)) }
        }
        playback.onAllSourcesFailed = { ch -> repair(ch); true }
        playback.setQualityMode(settings.value.videoQuality)
        playback.sourceOrder = { withBestSource(it) }
        if (settings.value.kidsMode) _section.value = Section.KIDS
    }

    fun title(ch: Channel): String = ch.displayName(isArabicUi())

    /** The in-app language choice if set, else the device language. */
    fun isArabicUi(): Boolean {
        val app = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val lang = if (!app.isEmpty) app[0]?.language else java.util.Locale.getDefault().language
        return lang == "ar"
    }

    /** 1-based channel number of a built-in channel (0 if it has none). */
    fun numberOf(ch: Channel, list: List<Channel> = catalog.value): Int {
        if (ch.playlist != null) return 0
        return list.indexOfFirst { it.id == ch.id } + 1
    }

    // ---- Catalog -------------------------------------------------------------------------------

    private fun seedKnownChannels() {
        if (settings.value.knownIds != null) return
        val ids = repo.channels.value.filter { it.playlist == null }.map { it.id }
        if (ids.isNotEmpty()) prefs.edit { it.copy(knownIds = ids.toSet()) }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repo.refresh(settings.value.playlists)
                seedKnownChannels()
            } finally {
                _refreshing.value = false
            }
            if (settings.value.checkStreams) checkStreams()
            loadGuide(force = false)
        }
    }

    fun loadGuide(force: Boolean) {
        val s = settings.value
        if (!s.showGuide) return
        viewModelScope.launch {
            val urls = s.guideUrls.lines().map { it.trim() }.filter { StreamSource.isPlayableUrl(it) }
            guideRepo.load(repo.channels.value, urls, force)
        }
    }

    /**
     * Checks every channel (8 at a time): which links answer and the best resolution each offers,
     * so the sharpest working link plays first.
     */
    fun checkStreams() {
        healthJob?.cancel()
        _health.value = emptyMap()
        val list = catalog.value
        healthJob = viewModelScope.launch(Dispatchers.IO) {
            val gate = Semaphore(8)
            val jobs = list.map { ch ->
                launch {
                    gate.withPermit {
                        var alive = false
                        var best = 0
                        for (src in ch.playableSources.take(MAX_PROBED_LINKS)) {
                            val r = Http.probeQuality(src)
                            probes[src.url] = r
                            if (r.alive) {
                                alive = true
                                best = maxOf(best, r.height)
                            }
                        }
                        _health.update { it + (ch.id to (alive || ch.youtubeSource != null)) }
                        if (best > 0) _quality.update { it + (ch.id to best) }
                    }
                }
            }
            jobs.forEach { it.join() }
        }
    }

    /** "1080p" for the best working link measured, else what the playlist claims. */
    fun qualityLabel(ch: Channel, measured: Map<String, Int>): String? =
        measured[ch.id]?.let { "${it}p" } ?: ch.bestQuality

    /** The channel's links with what the last check found, in the order they would be tried. */
    fun linksOf(ch: Channel): List<Pair<StreamSource, ProbeResult?>> =
        withBestSource(ch).playableSources.map { it to probes[it.url] }

    /** The user picked a link in the player's Source menu: play it now and remember it for next time. */
    fun chooseSource(ch: Channel, url: String) {
        prefs.edit { s -> s.copy(sourceChoice = s.sourceChoice + (ch.id to url)) }
        playback.play(withBestSource(ch), playback.queue)
    }

    fun setVideoQuality(mode: String) {
        prefs.edit { it.copy(videoQuality = mode) }
        playback.setQualityMode(mode)
    }

    /** "Find new channels": downloads the lists again and shows what is new. */
    fun findNewChannels() {
        if (_finding.value) return
        viewModelScope.launch {
            _finding.value = true
            try {
                repo.refresh(settings.value.playlists)
                seedKnownChannels()
            } finally {
                _finding.value = false
            }
            val pending = pendingChannels.value.ifEmpty {
                // The combine may not have re-run yet; compute directly.
                val s = settings.value
                val known = s.knownIds.orEmpty()
                repo.channels.value.filter { it.playlist == null && it.id !in known && it.id !in s.hiddenIds }
            }
            if (pending.isEmpty()) _messages.tryEmit(UiMessage.NoNewChannels) else _newChannelsDialog.value = pending
        }
    }

    /** Adds [selected] from the dialog; the rest are hidden (they can be brought back in Settings). */
    fun addNewChannels(selected: Set<String>) {
        val offered = _newChannelsDialog.value.orEmpty().map { it.id }
        _newChannelsDialog.value = null
        prefs.edit { s ->
            s.copy(
                knownIds = s.knownIds.orEmpty() + offered,
                hiddenIds = s.hiddenIds + (offered.toSet() - selected),
            )
        }
        _messages.tryEmit(UiMessage.ChannelsAdded(selected.size))
        if (settings.value.checkStreams) checkStreams()
    }

    fun dismissNewChannels() { _newChannelsDialog.value = null }

    /** Opens the dialog with channels already found in the background. */
    fun showPendingChannels() {
        val p = pendingChannels.value
        if (p.isEmpty()) _messages.tryEmit(UiMessage.NoNewChannels) else _newChannelsDialog.value = p
    }

    fun hideChannel(id: String) = prefs.edit { s -> s.copy(hiddenIds = s.hiddenIds + id, favorites = s.favorites - id) }

    fun unhideChannel(id: String) = prefs.edit { s ->
        s.copy(hiddenIds = s.hiddenIds - id, knownIds = s.knownIds?.plus(id))
    }

    fun setSection(t: Section) { _section.value = t }
    fun setQuery(q: String) { _query.value = q }

    fun listFor(
        section: Section,
        all: List<Channel>,
        s: Settings,
        health: Map<String, Boolean>,
        query: String = "",
    ): List<Channel> {
        var list = when (section) {
            Section.NEWS -> all.filter { Category.NEWS in it.categories && it.playlist == null }
            Section.DOCUMENTARY -> all.filter { Category.DOCUMENTARY in it.categories && it.playlist == null }
            Section.KIDS -> all.filter { Category.KIDS in it.categories && (it.playlist == null || s.kidsMode) }
            Section.QURAN -> all.filter { Category.QURAN in it.categories && it.playlist == null }
            Section.ALL, Section.HOME, Section.LIBRARY -> all.filter { it.playlist == null }
            Section.PLAYLISTS -> all.filter { it.playlist != null }
            Section.MY_LIST -> s.favorites.mapNotNull { id -> all.firstOrNull { it.id == id } }
            Section.SEARCH -> all
        }
        if (s.hideOffline) list = list.filter { health[it.id] != false }
        if (section == Section.SEARCH) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return emptyList()
            val n = Catalog.normalizedName(q)
            list = list.filter {
                it.name.lowercase().contains(q) || (it.nameAr?.contains(q) == true) ||
                    (n.length >= 2 && Catalog.normalizedName(it.name).contains(n)) ||
                    it.country?.lowercase() == q || numberOf(it, all).toString() == q
            }
        }
        if (section != Section.MY_LIST) {
            // Working streams first, then unknown, then dead.
            list = list.sortedBy { when (health[it.id]) { true -> 0; null -> 1; false -> 2 } }
        }
        if (section == Section.QURAN) {
            // Al-Haram (Makkah) and Al-Masjid An-Nabawi (Madinah) always lead.
            list = list.sortedBy { ch -> Catalog.QURAN_PINNED.indexOf(ch.tvgId).let { if (it < 0) Int.MAX_VALUE else it } }
        }
        return list
    }

    fun recents(all: List<Channel>, s: Settings): List<Channel> =
        s.recents.mapNotNull { id -> all.firstOrNull { it.id == id } }

    // ---- Playback ------------------------------------------------------------------------------

    /**
     * The channel's links in the order to try them: the one the user picked (if still listed),
     * then working links by measured resolution, highest first; dead links last.
     */
    private fun withBestSource(ch: Channel): Channel {
        var ordered = StreamQuality.rank(ch.sources, probes)
        settings.value.sourceChoice[ch.id]?.let { url ->
            val picked = ordered.firstOrNull { it.url == url }
            if (picked != null) ordered = listOf(picked) + (ordered - picked)
        }
        return if (ordered == ch.sources) ch else ch.copy(sources = ordered)
    }

    /** Plays [ch] wherever playback currently goes: a connected Cast device, a chosen TV, or here. */
    fun open(ch: Channel, list: List<Channel>) {
        prefs.addRecent(ch.id)
        if (ch.playableSources.isEmpty()) {
            // Published only on YouTube: open it there.
            val yt = ch.youtubeSource ?: return
            val ok = Library.openYoutubeUrl(getApplication<Application>(), yt.url, title(ch) + " live", isTv)
            if (!ok) _messages.tryEmit(UiMessage.NoApp)
            return
        }
        val best = withBestSource(ch)
        val queue = list.map { if (it.id == ch.id) best else it }
        val tv = _target.value
        when {
            cast.deviceName.value != null -> {
                cast.play(best, title(best))
                playback.play(best, queue)
                playback.player.pause()
                _screen.value = Screen.PLAYER
            }
            tv != null -> sendToTv(tv, best)
            else -> {
                playback.play(best, queue)
                _screen.value = Screen.PLAYER
            }
        }
    }

    /**
     * Every link of [ch] failed: download the channel lists again (unless that just happened)
     * and retry with whatever links they now have for it.
     */
    private fun repair(ch: Channel) {
        viewModelScope.launch {
            if (System.currentTimeMillis() - repo.lastRefresh > 2 * 60_000L) {
                runCatching { repo.refresh(settings.value.playlists) }
            }
            val fresh = repo.channels.value.firstOrNull { it.id == ch.id }
                ?: repo.channels.value.firstOrNull {
                    it.playlist == ch.playlist && Catalog.normalizedName(it.name) == Catalog.normalizedName(ch.name)
                }
            val oldUrls = ch.sources.map { it.url }.toSet()
            val newSources = fresh?.playableSources.orEmpty().filter { it.url !in oldUrls }
            if (fresh != null && newSources.isNotEmpty()) {
                // New links first, then the old ones once more.
                playback.replaceSources(ch.copy(sources = newSources + ch.sources))
            } else {
                playback.repairFailed()
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
        playback.play(withBestSource(ch), playback.queue)
        if (cast.deviceName.value != null) {
            cast.play(ch, title(ch))
            playback.player.pause()
        }
    }

    /** A digit pressed on the remote: after a short pause the number is opened as a channel. */
    fun typeDigit(d: Int) {
        if (_numberEntry.value.length >= 4) _numberEntry.value = ""
        _numberEntry.value += d.toString()
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(1500)
            val n = _numberEntry.value.toIntOrNull()
            _numberEntry.value = ""
            val all = catalog.value
            val ch = n?.let { all.getOrNull(it - 1) }?.takeIf { it.playlist == null }
            if (ch == null) {
                _messages.tryEmit(UiMessage.NoChannelNumber)
                return@launch
            }
            val s = settings.value
            if (s.kidsMode && !_kidsUnlocked.value && !ch.allowedForKids()) return@launch
            val list = all.filter { it.playlist == null }
            if (_screen.value == Screen.PLAYER) {
                prefs.addRecent(ch.id)
                playback.play(withBestSource(ch), list)
            } else {
                open(ch, list)
            }
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
        val all = catalog.value.ifEmpty { repo.channels.value }
        val ch = all.firstOrNull { it.id == s.lastChannel } ?: return
        if (ch.playableSources.isEmpty()) return
        playback.play(withBestSource(ch), all.filter { c -> c.categories.any { it in ch.categories } && c.playlist == ch.playlist })
        _screen.value = Screen.PLAYER
    }

    // ---- Library -------------------------------------------------------------------------------

    fun openLibraryItem(item: LibraryItem) {
        val action = item.action
        if (action is LibraryAction.LiveChannel) {
            // A live channel from the catalog (e.g. Al-Haram): play it here.
            val all = repo.channels.value
            val ch = all.firstOrNull { it.tvgId.equals(action.tvgId, ignoreCase = true) }
            if (ch == null) {
                _messages.tryEmit(UiMessage.NoChannelNumber)
                return
            }
            open(ch, listFor(Section.QURAN, catalog.value, settings.value, _health.value).ifEmpty { listOf(ch) })
            return
        }
        if (!Library.open(getApplication<Application>(), action, isTv)) _messages.tryEmit(UiMessage.NoApp)
    }

    fun loadLocalVideos() {
        viewModelScope.launch {
            _localVideos.value = withContext(Dispatchers.IO) { Library.localVideos(getApplication<Application>()) }
        }
    }

    fun playLocal(video: LocalVideo, all: List<LocalVideo>) {
        fun ch(v: LocalVideo) = Channel(
            id = "local:${v.id}",
            name = v.title,
            categories = setOf(Category.OTHER),
            sources = listOf(StreamSource(v.uri.toString())),
        )
        playback.play(ch(video), all.map { ch(it) })
        _screen.value = Screen.PLAYER
    }

    // ---- Updates -------------------------------------------------------------------------------

    fun checkForUpdates() {
        _updatePromptDismissed.value = false
        _manualUpdateCheck.value = true
        viewModelScope.launch { Updater.check() }
    }

    fun installUpdate(release: Release) {
        viewModelScope.launch { Updater.downloadAndInstall(getApplication<Application>(), release) }
    }

    fun dismissUpdate() {
        _updatePromptDismissed.value = true
        _manualUpdateCheck.value = false
        Updater.dismiss()
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
                val kind = if (tv is TvTarget.Lan) classifySendError(e) else SendError.OTHER
                _messages.tryEmit(UiMessage.SendFailed(tv.label, e.message ?: e.javaClass.simpleName, kind))
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
                    if (settings.value.kidsMode && !_kidsUnlocked.value && !ch.allowedForKids()) return@launch
                    // Prefer our own copy of the channel (it has every source); zap within its category.
                    val local = repo.channels.value.firstOrNull { it.id == ch.id } ?: ch
                    val list = repo.channels.value.filter { c ->
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

    fun moveFavorite(id: String, step: Int) = prefs.moveFavorite(id, step)

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
        _section.value = Section.KIDS
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
        _section.value = Section.KIDS
        _screen.value = Screen.HOME
    }

    companion object {
        private const val REFRESH_EVERY = 6 * 60 * 60 * 1000L
        private const val MAX_PROBED_LINKS = 5
    }

    override fun onCleared() {
        discovery.stop()
        playback.release()
        super.onCleared()
    }

}
