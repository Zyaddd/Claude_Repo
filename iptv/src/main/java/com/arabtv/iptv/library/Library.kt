package com.arabtv.iptv.library

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject

/** What tapping a library tile does. */
sealed class LibraryAction {
    /** Search the official channel on YouTube; [url] (a channel page) is used where the app supports it. */
    data class Youtube(val query: String, val url: String?) : LibraryAction()
    /** Open an official app, or its store page when it is not installed. */
    data class App(val pkg: String?, val name: String) : LibraryAction()
    /** Play a live channel from the catalog, found by its tvg-id. */
    data class LiveChannel(val tvgId: String) : LibraryAction()
}

data class LibraryItem(
    val id: String,
    val title: String,
    val titleAr: String,
    val by: String,
    val byAr: String,
    val colors: List<Long>,
    val action: LibraryAction,
) {
    fun title(arabic: Boolean) = if (arabic) titleAr else title
    fun by(arabic: Boolean) = if (arabic) byAr else by
}

data class LibrarySection(val id: String, val title: String, val titleAr: String, val items: List<LibraryItem>) {
    fun title(arabic: Boolean) = if (arabic) titleAr else title
}

/** A video on the device or a USB stick. */
data class LocalVideo(val id: Long, val title: String, val uri: Uri, val durationMs: Long)

object Library {
    fun load(context: Context): List<LibrarySection> = runCatching {
        val root = JSONObject(context.assets.open("library.json").bufferedReader().use { it.readText() })
        val sections = root.getJSONArray("sections")
        (0 until sections.length()).map { i ->
            val s = sections.getJSONObject(i)
            val items = s.getJSONArray("items")
            LibrarySection(
                id = s.getString("id"),
                title = s.getString("title"),
                titleAr = s.optString("titleAr", s.getString("title")),
                items = (0 until items.length()).mapNotNull { j -> parseItem(items.getJSONObject(j)) },
            )
        }
    }.getOrDefault(emptyList())

    private fun parseItem(o: JSONObject): LibraryItem? {
        val a = o.getJSONObject("action")
        val action = when (a.getString("type")) {
            "youtube" -> LibraryAction.Youtube(a.getString("query"), a.optString("url").ifBlank { null })
            "app" -> LibraryAction.App(a.optString("package").ifBlank { null }, a.getString("name"))
            "channel" -> LibraryAction.LiveChannel(a.getString("tvgId"))
            else -> return null
        }
        val colors = o.optJSONArray("colors")?.let { arr ->
            (0 until arr.length()).mapNotNull { parseColor(arr.getString(it)) }
        }.orEmpty().ifEmpty { listOf(0xFF334155, 0xFF0F172A) }
        return LibraryItem(
            id = o.getString("id"),
            title = o.getString("title"),
            titleAr = o.optString("titleAr", o.getString("title")),
            by = o.optString("by"),
            byAr = o.optString("byAr", o.optString("by")),
            colors = colors,
            action = action,
        )
    }

    private fun parseColor(hex: String): Long? =
        hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    /** Opens [action]; returns false if nothing on the device could handle it. */
    fun open(context: Context, action: LibraryAction, isTv: Boolean): Boolean = when (action) {
        is LibraryAction.Youtube -> openYoutube(context, action, isTv)
        is LibraryAction.App -> openApp(context, action, isTv)
        is LibraryAction.LiveChannel -> false // played by the app itself (AppViewModel.openLibraryItem)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private const val YT_TV = "com.google.android.youtube.tv"
    private const val YT_PHONE = "com.google.android.youtube"

    fun openYoutube(context: Context, a: LibraryAction.Youtube, isTv: Boolean): Boolean {
        val searchUrl = "https://www.youtube.com/results?search_query=" + Uri.encode(a.query)
        val tries = buildList {
            if (isTv) {
                // The TV app understands search best; channel pages are not reliable there.
                add(Intent(Intent.ACTION_SEARCH).setPackage(YT_TV).putExtra(SearchManager.QUERY, a.query))
                add(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).setPackage(YT_TV))
            } else {
                a.url?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(YT_PHONE)) }
                add(Intent(Intent.ACTION_SEARCH).setPackage(YT_PHONE).putExtra(SearchManager.QUERY, a.query))
            }
            add(Intent(Intent.ACTION_VIEW, Uri.parse(a.url ?: searchUrl)))
        }
        return tries.any { start(context, it) }
    }

    /** A live channel that is only published on YouTube. */
    fun openYoutubeUrl(context: Context, url: String, fallbackQuery: String, isTv: Boolean): Boolean {
        val tries = buildList {
            add(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(if (isTv) YT_TV else YT_PHONE))
            if (isTv) add(Intent(Intent.ACTION_SEARCH).setPackage(YT_TV).putExtra(SearchManager.QUERY, fallbackQuery))
            add(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        return tries.any { start(context, it) }
    }

    private fun openApp(context: Context, a: LibraryAction.App, isTv: Boolean): Boolean {
        val pm = context.packageManager
        val launch = a.pkg?.let { pkg ->
            (if (isTv) pm.getLeanbackLaunchIntentForPackage(pkg) else null) ?: pm.getLaunchIntentForPackage(pkg)
        }
        if (launch != null && start(context, launch)) return true
        val store = if (a.pkg != null) "market://details?id=${a.pkg}" else "market://search?q=" + Uri.encode(a.name)
        val web = if (a.pkg != null) "https://play.google.com/store/apps/details?id=${a.pkg}"
        else "https://play.google.com/store/search?q=" + Uri.encode(a.name)
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(store))) ||
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(web)))
    }

    /** Videos on the device and attached USB storage (needs the video-read permission). */
    fun localVideos(context: Context): List<LocalVideo> = runCatching {
        val out = ArrayList<LocalVideo>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
        )
        context.contentResolver.query(
            collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext() && out.size < 500) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol)?.ifBlank { null } ?: c.getString(nameCol) ?: "Video"
                out += LocalVideo(id, title, ContentUris.withAppendedId(collection, id), c.getLong(durCol))
            }
        }
        out
    }.getOrDefault(emptyList())
}
