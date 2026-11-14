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

/**
 * The channel catalog: iptv-org's Arabic playlist (bundled snapshot, refreshed online) plus any
 * playlists the user added.
 */
class ChannelRepository(private val context: Context) {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    /** Playlists that failed on the last refresh, with the error text. */
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val failures: StateFlow<Map<String, String>> = _failures.asStateFlow()

    private val arabicNames: Map<String, String> by lazy {
        runCatching {
            val o = JSONObject(context.assets.open("arabic_names.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private val catalogCache get() = File(context.filesDir, "catalog_ara.m3u")

    /** Shows whatever is available offline right away (cache, else the bundled snapshot). */
    suspend fun loadOffline(playlists: List<UserPlaylist>) = withContext(Dispatchers.IO) {
        val builtIn = catalogCache.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
            ?.takeIf { looksValid(it) }
            ?: context.assets.open("ara.m3u").bufferedReader().use { it.readText() }
        val user = playlists.mapNotNull { p ->
            playlistCache(p).takeIf { it.exists() }?.let { f -> runCatching { p to f.readText() }.getOrNull() }
        }
        publish(builtIn, user)
    }

    /** Downloads fresh copies of everything; keeps the old copy of anything that fails. */
    suspend fun refresh(playlists: List<UserPlaylist>) = withContext(Dispatchers.IO) {
        val failures = mutableMapOf<String, String>()
        var builtIn: String? = null
        for (url in CATALOG_URLS) {
            try {
                val text = Http.getText(url)
                if (looksValid(text)) {
                    builtIn = text
                    runCatching { catalogCache.writeText(text) }
                    break
                }
            } catch (e: Exception) {
                failures[url] = e.message ?: e.javaClass.simpleName
            }
        }
        if (builtIn != null) failures.keys.removeAll(CATALOG_URLS.toSet())
        val builtInText = builtIn
            ?: catalogCache.takeIf { it.exists() }?.readText()?.takeIf { looksValid(it) }
            ?: context.assets.open("ara.m3u").bufferedReader().use { it.readText() }

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
        publish(builtInText, user)
        _failures.value = failures
    }

    fun forget(p: UserPlaylist) {
        playlistCache(p).delete()
    }

    private fun publish(builtIn: String, user: List<Pair<UserPlaylist, String>>) {
        val list = Catalog.build(M3uParser.parse(builtIn), arabicNames).toMutableList()
        for ((p, text) in user) list += Catalog.build(M3uParser.parse(text), arabicNames, playlist = p.name)
        // Two playlists with the same name could repeat ids; lists need unique keys.
        _channels.value = list.distinctBy { it.id }
    }

    private fun playlistCache(p: UserPlaylist): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(p.url.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "playlist_$hash.m3u")
    }

    private fun looksValid(text: String) = text.contains("#EXTM3U") && text.split("#EXTINF").size > 50

    companion object {
        val CATALOG_URLS = listOf(
            "https://iptv-org.github.io/iptv/languages/ara.m3u",
            "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/languages/ara.m3u",
        )
    }
}
