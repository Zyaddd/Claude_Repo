package com.arabtv.iptv.data

/** Turns playlist entries into channels: merges duplicates, cleans titles and assigns categories. */
object Catalog {
    private val qualityRegex = Regex("""\((\d{3,4}p)\)""")
    private val tagRegex = Regex("""\[[^\]]*]""")
    private val parenRegex = Regex("""\(\s*\d{3,4}[pi]\s*\)""")

    private val newsWords = Regex(
        """(?i)\bnews\b|\bnewsroom\b|business|akhbar|ikhbaria|ekhbariya|ikhbariya|إخبار|اخبار|أخبار|الإخبارية|الاخبارية""",
    )
    private val docWords = Regex(
        """(?i)documentar|\bdocu|\bdoc\b|discovery|geographic|\bnat ?geo|wathaeq|science|education|\bedu\b|history|nature|وثائقي|الوثائقية|تعليم|علوم""",
    )
    private val kidsWords = Regex(
        """(?i)\bkids?\b|kid's|children|cartoon|\btoon|animation|anime\b|spacetoon|atfal|baraem|\bjeem\b|majid kids|mbc ?3|semsem|toyor|karameesh|koogi|babyfirst|أطفال|اطفال|كرتون|براعم|طيور|سبيستون|سبيس تون""",
    )

    /**
     * @param arabicNames channel id (without the "@feed" suffix) -> Arabic name.
     * @param playlist label for a user playlist, or null for the built-in catalog.
     */
    fun build(entries: List<M3uEntry>, arabicNames: Map<String, String>, playlist: String? = null): List<Channel> {
        data class Acc(val first: M3uEntry, val id: String, val sources: MutableList<StreamSource>)

        val byKey = LinkedHashMap<String, Acc>()
        for (e in entries) {
            val tvgId = e.attrs["tvg-id"]?.trim().orEmpty()
            val clean = cleanTitle(e.title)
            val key = if (tvgId.isNotEmpty()) "id:$tvgId" else "name:" + clean.lowercase()
            val src = StreamSource(
                url = e.url,
                userAgent = e.userAgent,
                referrer = e.referrer,
                quality = qualityRegex.find(e.title)?.groupValues?.get(1),
            )
            val acc = byKey.getOrPut(key) {
                val id = (if (playlist != null) "pl:$playlist|" else "") + key
                Acc(e, id, ArrayList())
            }
            if (acc.sources.none { it.url == src.url }) acc.sources += src
        }

        return byKey.values.map { acc ->
            val e = acc.first
            val tvgId = e.attrs["tvg-id"]?.trim().orEmpty()
            val baseId = tvgId.substringBefore('@')
            val country = baseId.substringAfterLast('.', "").uppercase().takeIf { it.length == 2 }
            val name = cleanTitle(e.title).ifBlank { e.attrs["tvg-name"] ?: e.url }
            Channel(
                id = acc.id,
                name = name,
                nameAr = arabicNames[baseId] ?: name.takeIf { isArabic(it) },
                logo = e.attrs["tvg-logo"]?.trim()?.takeIf { StreamSource.isPlayableUrl(it) },
                categories = classify(e.group, name, e.attrs["tvg-name"]),
                country = country,
                // Highest resolution first; unknown resolutions keep their playlist order after.
                sources = acc.sources.sortedByDescending { it.quality?.removeSuffix("p")?.toIntOrNull() ?: 0 },
                geoBlocked = e.title.contains("[Geo-blocked]", ignoreCase = true),
                notAlwaysOn = e.title.contains("[Not 24/7]", ignoreCase = true),
                playlist = playlist,
            )
        }
    }

    fun cleanTitle(title: String): String =
        title.replace(tagRegex, "").replace(parenRegex, "").replace(Regex("\\s+"), " ").trim()

    fun classify(group: String?, name: String, altName: String? = null): Set<Category> {
        val result = mutableSetOf<Category>()
        val groups = group.orEmpty().split(';', '|', '/').map { it.trim().lowercase() }
        for (g in groups) {
            when (g) {
                "news", "business" -> result += Category.NEWS
                "documentary", "education", "science" -> result += Category.DOCUMENTARY
                "kids", "animation" -> result += Category.KIDS
            }
        }
        // Names (and free-form groups from user playlists) catch the rest, e.g. "Ch4Teen Documentary".
        val text = listOfNotNull(name, altName, group).joinToString(" ")
        if (Category.NEWS !in result && newsWords.containsMatchIn(text)) result += Category.NEWS
        if (Category.DOCUMENTARY !in result && docWords.containsMatchIn(text)) result += Category.DOCUMENTARY
        if (Category.KIDS !in result && kidsWords.containsMatchIn(text)) result += Category.KIDS
        return result.ifEmpty { mutableSetOf(Category.OTHER) }
    }

    private fun isArabic(s: String) = s.any { it in '؀'..'ۿ' }
}
