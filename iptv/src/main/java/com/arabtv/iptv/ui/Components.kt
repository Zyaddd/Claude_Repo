package com.arabtv.iptv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.flagEmoji

@Composable
fun ChannelLogo(channel: Channel, title: String, modifier: Modifier = Modifier) {
    val fallback = @Composable {
        Box(
            Modifier.fillMaxSize().background(Brand.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.trim().take(2),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF242E3A)), contentAlignment = Alignment.Center) {
        if (channel.logo == null) fallback()
        else SubcomposeAsyncImage(
            model = channel.logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

@Composable
fun Badge(text: String, color: Color = Surface2, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(
            text,
            fontSize = 10.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            maxLines = 1,
        )
    }
}

/** Grid tile. Long-press (or long OK on a remote) opens the channel's menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    channel: Channel,
    title: String,
    favorite: Boolean,
    health: Boolean?,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .combinedClickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (playing) Brand.copy(alpha = 0.45f) else Surface1),
        border = focusBorder(source),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                ChannelLogo(channel, title, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
                if (favorite) {
                    Icon(
                        Icons.Filled.Star, null, tint = Gold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                    )
                }
                if (health != null) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(6.dp).size(9.dp).clip(CircleShape)
                            .background(if (health) Color(0xFF3FD07A) else Color(0xFFE5534B)),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(4.dp))
            ChannelBadges(channel, health)
        }
    }
}

@Composable
fun ChannelBadges(channel: Channel, health: Boolean?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val flag = flagEmoji(channel.country)
        if (flag.isNotEmpty()) Text(flag, fontSize = 12.sp)
        channel.bestQuality?.let { Badge(it) }
        if (health == false) Badge(stringResource(R.string.badge_offline), Color(0x55E5534B), Color(0xFFFFB4AB))
        if (channel.geoBlocked) Badge(stringResource(R.string.badge_geo))
        if (channel.notAlwaysOn) Badge(stringResource(R.string.badge_part_time))
        if (channel.sources.size > 1) Badge("×${channel.sources.size}")
    }
}

/** Compact row used in the player's side/bottom list. */
@Composable
fun ChannelRow(
    channel: Channel,
    title: String,
    playing: Boolean,
    health: Boolean?,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        color = if (playing) Brand.copy(alpha = 0.45f) else Color.Transparent,
        border = focusBorder(source),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel, title, Modifier.width(64.dp).aspectRatio(16f / 10f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                ChannelBadges(channel, health)
            }
        }
    }
}
