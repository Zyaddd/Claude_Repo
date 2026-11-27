package com.arabtv.iptv.tv

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel

/**
 * The app's row on the Android TV home screen ("Arab TV"): favorites first, then a few popular
 * channels. Each tile opens the channel directly through an arabtv://play/<id> link.
 * Google TV devices may show it only if the user enables it under "Customize channels".
 */
object HomeChannels {
    private const val TAG = "HomeChannels"
    private const val PREFS = "home_channels"

    fun playUri(channelId: String): Uri = Uri.parse("arabtv://play/" + Uri.encode(channelId))

    /** Replaces the row's programs with [channels] (at most 20). Safe to call on non-TV devices. */
    fun sync(context: Context, channels: List<Channel>, title: (Channel) -> String) {
        try {
            val helper = PreviewChannelHelper(context)
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var channelId = sp.getLong("channel", -1L)
            if (channelId != -1L && helper.getPreviewChannel(channelId) == null) channelId = -1L
            if (channelId == -1L) {
                val logo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(160, 160) ?: return
                val ch = PreviewChannel.Builder()
                    .setDisplayName(context.getString(R.string.app_name))
                    .setAppLinkIntentUri(Uri.parse("arabtv://home"))
                    .setLogo(logo)
                    .build()
                channelId = helper.publishDefaultChannel(ch)
                sp.edit().putLong("channel", channelId).apply()
            }
            // Remove the previous programs, then publish the current list.
            sp.getString("programs", "")!!.split(',').mapNotNull { it.toLongOrNull() }.forEach {
                runCatching { helper.deletePreviewProgram(it) }
            }
            val ids = channels.take(20).mapIndexedNotNull { i, ch ->
                val builder = PreviewProgram.Builder()
                builder.setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                    .setTitle(title(ch))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setIntentUri(playUri(ch.id))
                    .setInternalProviderId(ch.id)
                    .setLive(true)
                    .setWeight(1000 - i)
                ch.logo?.let { builder.setPosterArtUri(Uri.parse(it)) }
                runCatching { helper.publishPreviewProgram(builder.build()) }.getOrNull()
            }
            sp.edit().putString("programs", ids.joinToString(",")).apply()
        } catch (e: Throwable) {
            // No TV provider (phones, some boxes): nothing to do.
            Log.i(TAG, "home row not available: ${e.message}")
        }
    }
}
