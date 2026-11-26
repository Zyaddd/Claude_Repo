package com.arabtv.iptv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class UserPlaylist(val name: String, val url: String)

/** A TV reached through the internet relay, identified by the pairing code shown on the TV. */
data class PairedTv(val name: String, val code: String)

/** A TV on the local network typed in by address (for networks where discovery is blocked). */
data class ManualTv(val name: String, val host: String, val port: Int)

data class Settings(
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val pairedTvs: List<PairedTv> = emptyList(),
    val manualTvs: List<ManualTv> = emptyList(),
    val receiverEnabled: Boolean = false,
    val pairingCode: String = "",
    val relayServer: String = Prefs.DEFAULT_RELAY,
    val hideGeoBlocked: Boolean = false,
    val hideOffline: Boolean = false,
    val checkStreams: Boolean = true,
    val autoplayLast: Boolean = false,
    val lastChannel: String? = null,
    val kidsMode: Boolean = false,
    val kidsPin: String = "",
    /** Built-in channels the user has seen; anything else waits under "Find new channels". Null = not seeded yet. */
    val knownIds: Set<String>? = null,
    val hiddenIds: Set<String> = emptySet(),
    /** Show the channel name for 2 seconds after switching. */
    val channelOsd: Boolean = true,
    val showGuide: Boolean = true,
    val guideUrls: String = DEFAULT_GUIDE_URLS,
    val autoUpdate: Boolean = true,
)

class Prefs(context: Context, isTv: Boolean) {
    private val sp = context.getSharedPreferences("arabtv", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load(isTv))
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun edit(change: (Settings) -> Settings) {
        _state.update(change)
        save(_state.value)
    }

    fun toggleFavorite(id: String) = edit { s ->
        s.copy(favorites = if (id in s.favorites) s.favorites - id else s.favorites + id)
    }

    fun addRecent(id: String) = edit { s ->
        s.copy(recents = (listOf(id) + (s.recents - id)).take(MAX_RECENTS), lastChannel = id)
    }

    /** Moves [id] one place earlier (-1) or later (+1) in the favorites order. */
    fun moveFavorite(id: String, step: Int) = edit { s ->
        val list = s.favorites.toMutableList()
        val i = list.indexOf(id)
        val j = i + step
        if (i < 0 || j !in list.indices) return@edit s
        list.removeAt(i)
        list.add(j, id)
        s.copy(favorites = list)
    }

    fun newPairingCode() = edit { it.copy(pairingCode = generateCode()) }

    private fun load(isTv: Boolean): Settings {
        val s = Settings(
            favorites = strings("favorites"),
            recents = strings("recents"),
            playlists = objects("playlists").mapNotNull {
                val url = it.optString("url")
                if (url.isBlank()) null else UserPlaylist(it.optString("name").ifBlank { url }, url)
            },
            pairedTvs = objects("pairedTvs").mapNotNull {
                val code = Pairing.normalize(it.optString("code"))
                if (code.length != Pairing.CODE_LENGTH) null else PairedTv(it.optString("name"), code)
            },
            manualTvs = objects("manualTvs").mapNotNull {
                val host = it.optString("host")
                if (host.isBlank()) null else ManualTv(it.optString("name").ifBlank { host }, host, it.optInt("port", DEFAULT_PORT))
            },
            receiverEnabled = sp.getBoolean("receiverEnabled", isTv),
            pairingCode = sp.getString("pairingCode", null)?.takeIf { it.length == Pairing.CODE_LENGTH } ?: generateCode(),
            relayServer = sp.getString("relayServer", null)?.ifBlank { null } ?: DEFAULT_RELAY,
            hideGeoBlocked = sp.getBoolean("hideGeoBlocked", false),
            hideOffline = sp.getBoolean("hideOffline", false),
            checkStreams = sp.getBoolean("checkStreams", true),
            autoplayLast = sp.getBoolean("autoplayLast", isTv),
            lastChannel = sp.getString("lastChannel", null),
            kidsMode = sp.getBoolean("kidsMode", false),
            kidsPin = sp.getString("kidsPin", "") ?: "",
            knownIds = if (sp.contains("knownIds")) strings("knownIds").toSet() else null,
            hiddenIds = strings("hiddenIds").toSet(),
            channelOsd = sp.getBoolean("channelOsd", true),
            showGuide = sp.getBoolean("showGuide", true),
            guideUrls = sp.getString("guideUrls", null)?.ifBlank { null } ?: DEFAULT_GUIDE_URLS,
            autoUpdate = sp.getBoolean("autoUpdate", true),
        )
        save(s)
        return s
    }

    private fun save(s: Settings) {
        sp.edit()
            .putString("favorites", JSONArray(s.favorites).toString())
            .putString("recents", JSONArray(s.recents).toString())
            .putString("playlists", JSONArray(s.playlists.map { JSONObject().put("name", it.name).put("url", it.url) }).toString())
            .putString("pairedTvs", JSONArray(s.pairedTvs.map { JSONObject().put("name", it.name).put("code", it.code) }).toString())
            .putString("manualTvs", JSONArray(s.manualTvs.map {
                JSONObject().put("name", it.name).put("host", it.host).put("port", it.port)
            }).toString())
            .putBoolean("receiverEnabled", s.receiverEnabled)
            .putString("pairingCode", s.pairingCode)
            .putString("relayServer", s.relayServer)
            .putBoolean("hideGeoBlocked", s.hideGeoBlocked)
            .putBoolean("hideOffline", s.hideOffline)
            .putBoolean("checkStreams", s.checkStreams)
            .putBoolean("autoplayLast", s.autoplayLast)
            .putString("lastChannel", s.lastChannel)
            .putBoolean("kidsMode", s.kidsMode)
            .putString("kidsPin", s.kidsPin)
            .putString("hiddenIds", JSONArray(s.hiddenIds.toList()).toString())
            .putBoolean("channelOsd", s.channelOsd)
            .putBoolean("showGuide", s.showGuide)
            .putString("guideUrls", s.guideUrls)
            .putBoolean("autoUpdate", s.autoUpdate)
            .also { e ->
                val known = s.knownIds
                if (known != null) e.putString("knownIds", JSONArray(known.toList()).toString()) else e.remove("knownIds")
            }
            .apply()
    }

    private fun strings(key: String): List<String> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun objects(key: String): List<JSONObject> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun generateCode(): String {
        val rnd = SecureRandom()
        return String(CharArray(Pairing.CODE_LENGTH) { Pairing.ALPHABET[rnd.nextInt(Pairing.ALPHABET.length)] })
    }

    companion object {
        const val DEFAULT_RELAY = "https://ntfy.sh"
        const val DEFAULT_PORT = 8642

        /** Free XMLTV guides covering Arabic channels (Saudi, Egyptian and Al Jazeera feeds). */
        const val DEFAULT_GUIDE_URLS =
            "https://epgshare01.online/epgshare01/epg_ripper_SA1.xml.gz\n" +
                "https://epgshare01.online/epgshare01/epg_ripper_EG1.xml.gz\n" +
                "https://epgshare01.online/epgshare01/epg_ripper_ALJAZEERA1.xml.gz"
        private const val MAX_RECENTS = 40
    }
}
