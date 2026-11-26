package com.arabtv.iptv.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class Programme(val title: String, val start: Long, val stop: Long)

/**
 * Streaming XMLTV reader. Guides are large (every channel of a country for a week), so only
 * programmes of channels we know, inside the time window, are kept.
 */
object XmlTv {
    /**
     * @param match given a guide channel's id and display names, returns our channel id or null.
     * @return our channel id -> programmes (sorted by start) overlapping [from, to).
     */
    fun parse(
        input: InputStream,
        from: Long,
        to: Long,
        match: (guideId: String, names: List<String>) -> String?,
    ): Map<String, List<Programme>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        val idMap = HashMap<String, String>() // guide channel id -> our id
        val out = HashMap<String, MutableList<Programme>>()
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmtNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

        fun time(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            val t = s.trim()
            return runCatching { fmt.parse(t)?.time }.getOrNull()
                ?: runCatching { fmtNoZone.parse(t.take(14))?.time }.getOrNull()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val guideId = parser.getAttributeValue(null, "id").orEmpty()
                        val names = ArrayList<String>()
                        val depth = parser.depth
                        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
                            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "display-name") {
                                names += parser.nextText().trim()
                            }
                        }
                        if (guideId.isNotEmpty()) match(guideId, names)?.let { idMap[guideId] = it }
                    }
                    "programme" -> {
                        val ours = idMap[parser.getAttributeValue(null, "channel").orEmpty()]
                        val start = time(parser.getAttributeValue(null, "start"))
                        val stop = time(parser.getAttributeValue(null, "stop"))
                        var title: String? = null
                        val depth = parser.depth
                        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
                            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                            if (ours != null && title == null &&
                                parser.eventType == XmlPullParser.START_TAG && parser.name == "title"
                            ) {
                                title = parser.nextText().trim()
                            }
                        }
                        if (ours != null && title != null && start != null && stop != null && stop > from && start < to) {
                            out.getOrPut(ours) { ArrayList() } += Programme(title, start, stop)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return out.mapValues { (_, v) -> v.distinctBy { it.start }.sortedBy { it.start } }
    }

    /** Index of our channels by every key a guide might use for them. */
    class Matcher(channels: List<Channel>) {
        private val byKey = HashMap<String, String>()

        init {
            for (ch in channels) {
                if (ch.playlist != null) continue
                ch.tvgId?.let { id ->
                    byKey.putIfAbsent("id:" + idKey(id), ch.id)
                }
                byKey.putIfAbsent("n:" + Catalog.normalizedName(ch.name), ch.id)
                ch.nameAr?.let { byKey.putIfAbsent("n:" + Catalog.normalizedName(it), ch.id) }
            }
        }

        fun match(guideId: String, names: List<String>): String? =
            byKey["id:" + idKey(guideId)]
                ?: names.firstNotNullOfOrNull { n -> Catalog.normalizedName(n).takeIf { it.length >= 3 }?.let { byKey["n:$it"] } }
                ?: byKey["n:" + Catalog.normalizedName(guideId.substringBeforeLast('.'))]

        /** "Al.Jazeera.qa", "AlJazeera.qa" and "aljazeera.qa" are the same guide id. */
        private fun idKey(id: String): String {
            val base = id.substringBefore('@')
            val country = base.substringAfterLast('.', "")
            val name = if (country.length == 2) base.dropLast(3) else base
            return (name.filter { it.isLetterOrDigit() } + "." + country).lowercase()
        }
    }
}
