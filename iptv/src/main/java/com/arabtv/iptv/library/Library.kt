package com.arabtv.iptv.library

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

/** A video on this device (phone storage or a USB stick). */
data class LocalVideo(val id: Long, val title: String, val uri: Uri, val durationMs: Long)

object Library {
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

    /** A live channel that a broadcaster publishes only on YouTube. */
    fun openYoutubeUrl(context: Context, url: String, fallbackQuery: String, isTv: Boolean): Boolean {
        val tries = buildList {
            add(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(if (isTv) YT_TV else YT_PHONE))
            if (isTv) add(Intent(Intent.ACTION_SEARCH).setPackage(YT_TV).putExtra(SearchManager.QUERY, fallbackQuery))
            add(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        return tries.any { start(context, it) }
    }

    /** Videos on this device, newest first (needs the video-read permission). */
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
            while (c.moveToNext() && out.size < 1000) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol)?.ifBlank { null } ?: c.getString(nameCol) ?: "Video"
                out += LocalVideo(id, title, ContentUris.withAppendedId(collection, id), c.getLong(durCol))
            }
        }
        out
    }.getOrDefault(emptyList())
}
