package com.arabtv.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A built-in channel list: where it is published, its bundled snapshot, and which entries to keep. */
data class CatalogSource(
    val key: String,
    val urls: List<String>,
    val asset: String,
    val minEntries: Int,
    val keep: (M3uEntry) -> Boolean = { true },
)

/**
 * The channel catalog: public free-channel lists (bundled snapshots, refreshed online), merged so
 * each channel appears once with every known link, plus any playlists the user added.
 */
class ChannelRepository(private val context: Context) {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    /** Lists that failed on the last refresh, with the error text. */
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val failures: StateFlow<Map<String, String>> = _failures.asStateFlow()

    /** When the last successful online refresh finished (0 = never in this run). */
    @Volatile var lastRefresh = 0L
        private set

    private val arabicNames: Map<String, String> by lazy {
        runCatching {
            val o = JSONObject(context.assets.open("arabic_names.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun cacheFile(s: CatalogSource) = File(context.filesDir, "catalog_${s.key}.m3u")

    private fun bundled(s: CatalogSource): String =
        context.assets.open(s.asset).bufferedReader().use { it.readText() }

    private fun offlineText(s: CatalogSource): String =
        cacheFile(s).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
            ?.takeIf { looksValid(it, s.minEntries) }
            ?: bundled(s)

    /** Shows whatever is available offline right away (cache, else the bundled snapshot). */
    suspend fun loadOffline(playlists: List<UserPlaylist>) = withContext(Dispatchers.IO) {
        val builtIn = SOURCES.map { it to offlineText(it) }
        val user = playlists.mapNotNull { p ->
            playlistCache(p).takeIf { it.exists() }?.let { f -> runCatching { p to f.readText() }.getOrNull() }
        }
        publish(builtIn, user)
    }

    /** Downloads fresh copies of everything; keeps the old copy of anything that fails. */
    suspend fun refresh(playlists: List<UserPlaylist>) = withContext(Dispatchers.IO) {
        val failures = mutableMapOf<String, String>()
        val builtIn = SOURCES.map { s ->
            var text: String? = null
            for (url in s.urls) {
                try {
                    val t = Http.getText(url)
                    if (looksValid(t, s.minEntries)) {
                        text = t
                        runCatching { cacheFile(s).writeText(t) }
                        break
                    } else {
                        failures[url] = "Unexpected content"
                    }
                } catch (e: Exception) {
                    failures[url] = e.message ?: e.javaClass.simpleName
                }
            }
            if (text != null) failures.keys.removeAll(s.urls.toSet())
            s to (text ?: offlineText(s))
        }
        val user = playlists.mapNotNull { p ->
            try {
                val text = Http.getText(p.url)
                if (!text.contains("#EXTINF", ignoreCase = true)) throw IllegalArgumentException("Not an M3U playlist")
                runCatching { playlistCache(p).writeText(text) }
                p to text
            } catch (e: Exception) {
                failures[p.url] = e.message ?: e.javaClass.simpleName
                playlistCache(p).takeIf { it.exists() }?.let { p to it.readText() }
            }
        }
        publish(builtIn, user)
        _failures.value = failures
        lastRefresh = System.currentTimeMillis()
    }

    fun forget(p: UserPlaylist) {
        playlistCache(p).delete()
    }

    private fun publish(builtIn: List<Pair<CatalogSource, String>>, user: List<Pair<UserPlaylist, String>>) {
        val lists = builtIn.map { (s, text) ->
            Catalog.build(M3uParser.parse(text).filter(s.keep), arabicNames)
        }
        val list = Catalog.merge(lists).toMutableList()
        for ((p, text) in user) list += Catalog.build(M3uParser.parse(text), arabicNames, playlist = p.name)
        // Two playlists with the same name could repeat ids; lists need unique keys.
        _channels.value = list.distinctBy { it.id }
    }

    private fun playlistCache(p: UserPlaylist): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(p.url.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "playlist_$hash.m3u")
    }

    private fun looksValid(text: String, min: Int) = text.contains("#EXTM3U") && text.split("#EXTINF").size > min

    companion object {
        /** Must match FREETV_GROUPS / FREETV_EXCLUDE in iptv/tools/update_catalog.py. */
        val FREETV_GROUPS = setOf(
            "News (AR)", "Saudi Arabia", "United Arab Emirates", "Qatar", "Kuwait", "Bahrain", "Oman", "Yemen",
            "Iraq", "Syria", "Lebanon", "Jordan", "Palestine", "Egypt", "Sudan", "Libya", "Tunisia", "Algeria",
            "Morocco", "Mauritania",
        )
        private val FREETV_EXCLUDE = Regex("(?i)persia|english|zagros|turkmen|kurd")

        fun keepFreeTv(e: M3uEntry): Boolean =
            e.group in FREETV_GROUPS && !FREETV_EXCLUDE.containsMatchIn(e.title + " " + (e.attrs["tvg-id"] ?: ""))

        val SOURCES = listOf(
            // Hand-checked official links, listed first so they are tried first.
            CatalogSource(key = "pinned", urls = emptyList(), asset = "pinned.m3u", minEntries = 0),
            CatalogSource(
                key = "ara",
                urls = listOf(
                    "https://iptv-org.github.io/iptv/languages/ara.m3u",
                    "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/languages/ara.m3u",
                ),
                asset = "ara.m3u",
                minEntries = 50,
            ),
            CatalogSource(
                key = "freetv",
                urls = listOf("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"),
                asset = "freetv_arab.m3u",
                minEntries = 50,
                keep = ::keepFreeTv,
            ),
        )
    }
}
