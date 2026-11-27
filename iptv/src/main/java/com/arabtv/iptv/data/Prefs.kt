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

/** A family member: their own My List, history and link choices. Kids profiles see only cartoons and Quran. */
data class Profile(val id: String, val name: String, val kids: Boolean = false, val color: Int = 0)

/** A channel the user reported as broken, kept so it can be shared and fixed. */
data class Report(val channelId: String, val name: String, val time: Long, val links: List<String>)

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
    val guideUrls: String = Prefs.DEFAULT_GUIDE_URLS,
    val autoUpdate: Boolean = true,
    /** "best" (always the highest quality), "auto" (adapts to the connection) or "saver" (max 480p). */
    val videoQuality: String = "best",
    /** channel id -> the link the user picked in the player's Source menu. */
    val sourceChoice: Map<String, String> = emptyMap(),
    val prayerCity: String = "",
    /** At adhan time: "off", "ask" (offer Al-Haram live) or "auto" (switch to it). */
    val adhanAlert: String = "ask",
    /** "auto" (by the Hijri calendar), "on" or "off". */
    val ramadanMode: String = "auto",
    val showPrayerBar: Boolean = true,
    val profiles: List<Profile> = listOf(Profile(Prefs.MAIN_PROFILE, "")),
    val currentProfile: String = Prefs.MAIN_PROFILE,
    val startOnBoot: Boolean = false,
    val reports: List<Report> = emptyList(),
) {
    val profile: Profile get() = profiles.firstOrNull { it.id == currentProfile } ?: profiles.first()

    /** Kids mode, or a kids profile: only cartoons and Quran without the PIN. */
    val kidsLocked: Boolean get() = kidsMode || profile.kids
}

class Prefs(context: Context, isTv: Boolean) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
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

    /** Keys of per-profile values: the first profile keeps the original key names. */
    private fun pk(base: String, profile: String) = if (profile == MAIN_PROFILE) base else "$base@$profile"

    /** Switches profile: the new profile's My List, history and choices are loaded. */
    fun switchProfile(id: String) = edit { s ->
        if (s.profiles.none { it.id == id }) return@edit s
        s.copy(
            currentProfile = id,
            favorites = strings(pk("favorites", id)),
            recents = strings(pk("recents", id)),
            lastChannel = sp.getString(pk("lastChannel", id), null),
            sourceChoice = sourceChoices(pk("sourceChoice", id)),
        )
    }

    private fun sourceChoices(key: String): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun load(isTv: Boolean): Settings {
        val profiles = objects("profiles").mapNotNull {
            val id = it.optString("id")
            if (id.isBlank()) null else Profile(id, it.optString("name"), it.optBoolean("kids"), it.optInt("color"))
        }.ifEmpty { listOf(Profile(MAIN_PROFILE, "")) }
        val current = sp.getString("currentProfile", null)?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        val s = Settings(
            profiles = profiles,
            currentProfile = current,
            favorites = strings(pk("favorites", current)),
            recents = strings(pk("recents", current)),
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
            lastChannel = sp.getString(pk("lastChannel", current), null),
            kidsMode = sp.getBoolean("kidsMode", false),
            kidsPin = sp.getString("kidsPin", "") ?: "",
            knownIds = if (sp.contains("knownIds")) strings("knownIds").toSet() else null,
            hiddenIds = strings("hiddenIds").toSet(),
            channelOsd = sp.getBoolean("channelOsd", true),
            showGuide = sp.getBoolean("showGuide", true),
            guideUrls = sp.getString("guideUrls", null)?.ifBlank { null } ?: DEFAULT_GUIDE_URLS,
            autoUpdate = sp.getBoolean("autoUpdate", true),
            videoQuality = sp.getString("videoQuality", null)?.takeIf { it in setOf("best", "auto", "saver") } ?: "best",
            sourceChoice = sourceChoices(pk("sourceChoice", current)),
            prayerCity = sp.getString("prayerCity", null)?.ifBlank { null } ?: com.arabtv.iptv.prayer.Prayers.defaultCity().id,
            adhanAlert = sp.getString("adhanAlert", null)?.takeIf { it in setOf("off", "ask", "auto") } ?: "ask",
            ramadanMode = sp.getString("ramadanMode", null)?.takeIf { it in setOf("auto", "on", "off") } ?: "auto",
            showPrayerBar = sp.getBoolean("showPrayerBar", true),
            startOnBoot = sp.getBoolean(START_ON_BOOT, false),
            reports = objects("reports").map { o ->
                val links = o.optJSONArray("links")
                Report(
                    o.optString("id"), o.optString("name"), o.optLong("time"),
                    (0 until (links?.length() ?: 0)).map { links!!.getString(it) },
                )
            },
        )
        save(s)
        return s
    }

    private fun save(s: Settings) {
        sp.edit()
            .putString(pk("favorites", s.currentProfile), JSONArray(s.favorites).toString())
            .putString(pk("recents", s.currentProfile), JSONArray(s.recents).toString())
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
            .putString(pk("lastChannel", s.currentProfile), s.lastChannel)
            .putBoolean("kidsMode", s.kidsMode)
            .putString("kidsPin", s.kidsPin)
            .putString("hiddenIds", JSONArray(s.hiddenIds.toList()).toString())
            .putBoolean("channelOsd", s.channelOsd)
            .putBoolean("showGuide", s.showGuide)
            .putString("guideUrls", s.guideUrls)
            .putBoolean("autoUpdate", s.autoUpdate)
            .putString("videoQuality", s.videoQuality)
            .putString(pk("sourceChoice", s.currentProfile), JSONObject(s.sourceChoice).toString())
            .putString("prayerCity", s.prayerCity)
            .putString("adhanAlert", s.adhanAlert)
            .putString("ramadanMode", s.ramadanMode)
            .putBoolean("showPrayerBar", s.showPrayerBar)
            .putBoolean(START_ON_BOOT, s.startOnBoot)
            .putString("currentProfile", s.currentProfile)
            .putString("profiles", JSONArray(s.profiles.map {
                JSONObject().put("id", it.id).put("name", it.name).put("kids", it.kids).put("color", it.color)
            }).toString())
            .putString("reports", JSONArray(s.reports.map {
                JSONObject().put("id", it.channelId).put("name", it.name).put("time", it.time).put("links", JSONArray(it.links))
            }).toString())
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
        const val MAIN_PROFILE = "main"
        const val START_ON_BOOT = "startOnBoot"
        const val FILE = "arabtv"

        /** Free XMLTV guides covering Arabic channels (Saudi, Egyptian and Al Jazeera feeds). */
        const val DEFAULT_GUIDE_URLS =
            "https://epgshare01.online/epgshare01/epg_ripper_SA1.xml.gz\n" +
                "https://epgshare01.online/epgshare01/epg_ripper_EG1.xml.gz\n" +
                "https://epgshare01.online/epgshare01/epg_ripper_ALJAZEERA1.xml.gz"
        private const val MAX_RECENTS = 40
    }
}
