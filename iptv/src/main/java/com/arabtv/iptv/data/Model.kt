package com.arabtv.iptv.data

import org.json.JSONArray
import org.json.JSONObject

enum class Category { NEWS, DOCUMENTARY, KIDS, QURAN, OTHER }

/** One playable URL for a channel, with the HTTP headers some broadcasters insist on. */
data class StreamSource(
    val url: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val quality: String? = null,
) {
    /** A YouTube page rather than a media stream: opened in the YouTube app instead of the player. */
    val isYoutube: Boolean
        get() = url.contains("youtube.com/", ignoreCase = true) || url.contains("youtu.be/", ignoreCase = true)

    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        userAgent?.let { put("ua", it) }
        referrer?.let { put("ref", it) }
        quality?.let { put("q", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): StreamSource? {
            val url = o.optString("url")
            if (!isPlayableUrl(url)) return null
            return StreamSource(
                url = url,
                userAgent = o.optString("ua").ifBlank { null },
                referrer = o.optString("ref").ifBlank { null },
                quality = o.optString("q").ifBlank { null },
            )
        }

        /** Only network media URLs are accepted, from playlists and from remote commands alike. */
        fun isPlayableUrl(url: String): Boolean {
            val u = url.trim().lowercase()
            return (u.startsWith("http://") || u.startsWith("https://")) && u.length in 12..4000
        }
    }
}

data class Channel(
    /** Stable key used for favorites and history. */
    val id: String,
    val name: String,
    val nameAr: String? = null,
    val logo: String? = null,
    val categories: Set<Category> = setOf(Category.OTHER),
    /** Upper-case ISO country code, if known. */
    val country: String? = null,
    val sources: List<StreamSource>,
    val geoBlocked: Boolean = false,
    val notAlwaysOn: Boolean = false,
    /** Name of the user playlist this came from; null for the built-in catalog. */
    val playlist: String? = null,
    /** The playlist's tvg-id without its "@feed" suffix, used to match the same channel across sources. */
    val tvgId: String? = null,
    /** From the international (English) documentary list rather than the Arabic lists. */
    val foreign: Boolean = false,
) {
    /** Sources the built-in player can play (YouTube pages open in the YouTube app instead). */
    val playableSources: List<StreamSource> get() = sources.filterNot { it.isYoutube }
    val youtubeSource: StreamSource? get() = sources.firstOrNull { it.isYoutube }

    fun displayName(arabic: Boolean): String = if (arabic && !nameAr.isNullOrBlank()) nameAr else name

    val bestQuality: String?
        get() = sources.mapNotNull { it.quality }
            .maxByOrNull { it.removeSuffix("p").toIntOrNull() ?: 0 }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        nameAr?.let { put("nameAr", it) }
        logo?.let { put("logo", it) }
        put("cats", JSONArray(categories.map { it.name }))
        country?.let { put("country", it) }
        put("sources", JSONArray(sources.map { it.toJson() }))
        if (geoBlocked) put("geo", true)
        if (notAlwaysOn) put("part", true)
    }

    companion object {
        fun fromJson(o: JSONObject): Channel? {
            val arr = o.optJSONArray("sources") ?: return null
            val sources = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { StreamSource.fromJson(it) }
            }.take(8)
            if (sources.isEmpty()) return null
            val name = o.optString("name").take(200).ifBlank { return null }
            val cats = o.optJSONArray("cats")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { Category.valueOf(a.getString(it)) }.getOrNull() }
            }.orEmpty().toSet().ifEmpty { setOf(Category.OTHER) }
            return Channel(
                id = o.optString("id").ifBlank { "remote:$name" }.take(300),
                name = name,
                nameAr = o.optString("nameAr").ifBlank { null }?.take(200),
                logo = o.optString("logo").ifBlank { null }?.takeIf { StreamSource.isPlayableUrl(it) },
                categories = cats,
                country = o.optString("country").ifBlank { null },
                sources = sources,
                geoBlocked = o.optBoolean("geo"),
                notAlwaysOn = o.optBoolean("part"),
            )
        }
    }
}

/** Turns an ISO country code into its flag emoji ("SA" -> 🇸🇦). */
fun flagEmoji(country: String?): String {
    if (country == null || country.length != 2 || !country.all { it in 'A'..'Z' }) return ""
    val first = Character.codePointAt(country, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(country, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
