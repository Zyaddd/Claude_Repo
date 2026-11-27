@file:OptIn(UnstableApi::class)

package com.arabtv.iptv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.Profile
import com.arabtv.iptv.prayer.PrayerTime
import com.arabtv.iptv.prayer.Prayers
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val prayerNames = listOf(R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha)

@Composable
fun prayerName(index: Int): String = stringResource(prayerNames[index.coerceIn(0, 4)])

/** Current time, refreshed every [everyMs]. */
@Composable
fun rememberNow(everyMs: Long = 30_000): Instant {
    val t by produceState(Instant.now()) {
        while (true) {
            delay(everyMs)
            value = Instant.now()
        }
    }
    return t
}

private fun clock(i: Instant, zone: String): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(i.atZone(ZoneId.of(zone)))

@Composable
private fun untilText(from: Instant, to: Instant): String {
    val d = Duration.between(from, to).coerceAtLeast(Duration.ZERO)
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return if (h > 0) stringResource(R.string.in_hours_minutes, h.toInt(), m.toInt())
    else stringResource(R.string.in_minutes, m.toInt().coerceAtLeast(1))
}

/** Today's five prayers for the chosen city, the next one highlighted with a countdown. */
@Composable
fun PrayerBar(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    if (!s.showPrayerBar) return
    val now = rememberNow()
    val city = Prayers.city(s.prayerCity)
    val day = Prayers.today(city, now)
    val next = Prayers.next(city, now)
    val arabic = vm.isArabicUi()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Text(
                "🕌 " + city.name(arabic),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        items(day.times, key = { it.index }) { p ->
            val isNext = p.at == next.at
            Surface(
                color = if (isNext) Gold.copy(alpha = 0.18f) else Surface1,
                shape = RoundedCornerShape(50),
                border = if (isNext) androidx.compose.foundation.BorderStroke(1.dp, Gold) else null,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(prayerName(p.index), style = MaterialTheme.typography.labelLarge, color = if (isNext) Gold else TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(clock(p.at, city.zone), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    if (isNext) {
                        Spacer(Modifier.width(6.dp))
                        Text(untilText(now, p.at), style = MaterialTheme.typography.labelMedium, color = Gold)
                    }
                }
            }
        }
        if (next.index == 0 && day.times.none { it.at == next.at }) {
            // After Isha: tomorrow's Fajr.
            item {
                Text(
                    prayerName(0) + " " + clock(next.at, city.zone) + " · " + untilText(now, next.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                )
            }
        }
    }
}

/** In Ramadan: countdown to iftar (Maghrib) or the end of suhoor (Fajr), and Taraweeh from Makkah. */
@Composable
fun RamadanBanner(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    if (!vm.ramadanActive(s)) return
    val now = rememberNow()
    val city = Prayers.city(s.prayerCity)
    val today = Prayers.today(city, now)
    val maghrib = today.times[3].at
    val beforeIftar = now.isBefore(maghrib) && now.isAfter(today.times[0].at)
    val target = if (beforeIftar) maghrib else Prayers.next(city, now).let { n -> if (n.index == 0) n.at else null }
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = { vm.playHaram() },
        interactionSource = source,
        border = focusBorder(source),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1B4B),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ramadan_kareem), style = MaterialTheme.typography.titleMedium, color = Gold)
                if (target != null) {
                    Text(
                        stringResource(if (beforeIftar) R.string.iftar_in else R.string.suhoor_ends_in, untilText(now, target)) +
                            "  (" + clock(target, city.zone) + ")",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(stringResource(R.string.taraweeh_live), color = Gold, fontWeight = FontWeight.Bold)
        }
    }
}

/** "It's time for Asr - watch Al-Haram live?" */
@Composable
fun PrayerAlertDialog(vm: AppViewModel, prayer: PrayerTime) {
    AlertDialog(
        onDismissRequest = { vm.dismissPrayerAlert() },
        title = { Text(stringResource(R.string.adhan_now, prayerName(prayer.index))) },
        text = { Text(stringResource(R.string.adhan_watch_haram)) },
        confirmButton = { TextButton(onClick = { vm.playHaram() }) { Text(stringResource(R.string.watch)) } },
        dismissButton = { TextButton(onClick = { vm.dismissPrayerAlert() }) { Text(stringResource(R.string.not_now)) } },
    )
}

// ---- Programme guide grid ----------------------------------------------------------------------

/** Channels with guide data down the side, the next three hours across. */
@Composable
fun GuideGrid(vm: AppViewModel, tile: TileActions) {
    val guide by vm.guide.collectAsState()
    val nowI = rememberNow(60_000)
    val now = nowI.toEpochMilli()
    val start = now - now % (30 * 60_000L)
    val span = 3 * 60 * 60_000L
    val end = start + span
    val rows = tile.all.filter { ch -> guide[ch.id]?.any { it.stop > start && it.start < end } == true }
    val nameWidth = if (vm.isTv) 190.dp else 110.dp
    if (rows.isEmpty()) {
        Text(
            stringResource(R.string.guide_empty),
            color = TextSecondary,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val timeline = maxWidth - nameWidth - 12.dp
        fun x(t: Long): Dp = timeline * ((t - start).toFloat() / span)
        Column(Modifier.fillMaxSize()) {
            // Time labels every 30 minutes.
            Box(Modifier.fillMaxWidth().height(28.dp)) {
                for (k in 0 until 6) {
                    val t = start + k * 30 * 60_000L
                    Text(
                        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.offset(x = nameWidth + 12.dp + x(t)),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(rows, key = { it.id }) { ch -> GuideRow(vm, ch, guide[ch.id].orEmpty(), tile, nameWidth, timeline, start, end, now) }
            }
        }
    }
}

@Composable
private fun GuideRow(
    vm: AppViewModel,
    ch: Channel,
    programmes: List<com.arabtv.iptv.data.Programme>,
    tile: TileActions,
    nameWidth: Dp,
    timeline: Dp,
    start: Long,
    end: Long,
    now: Long,
) {
    val source = remember { MutableInteractionSource() }
    val span = (end - start).toFloat()
    Surface(
        onClick = { vm.open(ch, tile.all) },
        interactionSource = source,
        color = Color.Transparent,
        border = focusBorder(source),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.height(if (vm.isTv) 64.dp else 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(nameWidth).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ch, vm.title(ch), Modifier.width(52.dp).aspectRatio(16f / 9f))
                Spacer(Modifier.width(6.dp))
                Text(vm.title(ch), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(timeline).fillMaxHeight()) {
                for (p in programmes) {
                    if (p.stop <= start || p.start >= end) continue
                    val a = maxOf(p.start, start)
                    val b = minOf(p.stop, end)
                    val onNow = p.start <= now && p.stop > now
                    Box(
                        Modifier
                            .offset(x = timeline * ((a - start) / span))
                            .width((timeline * ((b - a) / span) - 3.dp).coerceAtLeast(2.dp))
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (onNow) Brand.copy(alpha = 0.55f) else Surface2)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(p.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                // "Now" line.
                Box(
                    Modifier.offset(x = timeline * ((now - start) / span)).width(2.dp).fillMaxHeight().background(LiveRed),
                )
            }
        }
    }
}

// ---- Profiles ------------------------------------------------------------------------------------

private val avatarColors = listOf(
    Color(0xFF0E7C66), Color(0xFFB45309), Color(0xFF7C3AED), Color(0xFFBE123C), Color(0xFF0369A1), Color(0xFF4D7C0F),
)

@Composable
fun profileName(p: Profile): String =
    p.name.ifBlank { stringResource(if (p.id == com.arabtv.iptv.data.Prefs.MAIN_PROFILE) R.string.profile_main else R.string.profile) }

@Composable
fun ProfileAvatar(p: Profile, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColors[p.color.mod(avatarColors.size)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (p.kids) "🧸" else profileName(p).take(1),
            style = if (size > 60.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

/** Netflix-style "Who's watching?" */
@Composable
fun ProfilePicker(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.who_is_watching), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(28.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                items(s.profiles, key = { it.id }) { p ->
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(120.dp),
                    ) {
                        Surface(
                            onClick = { vm.chooseProfile(p.id) },
                            interactionSource = source,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.focusScale(source, 1.12f)
                                .then(if (p == s.profiles.first()) Modifier.focusRequester(first) else Modifier)
                                .then(if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
                        ) { ProfileAvatar(p, 96.dp) }
                        Spacer(Modifier.height(8.dp))
                        Text(profileName(p), textAlign = TextAlign.Center, maxLines = 1, color = if (focused) Color.White else TextSecondary)
                    }
                }
                item(key = "add") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                        val source = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { adding = true },
                            interactionSource = source,
                            shape = CircleShape,
                            color = Surface2,
                            border = focusBorder(source),
                            modifier = Modifier.size(96.dp).focusScale(source, 1.12f),
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, Modifier.size(40.dp)) } }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.add_profile), color = TextSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
    if (adding) AddProfileDialog(vm) { adding = false }
}

@Composable
fun AddProfileDialog(vm: AppViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.add_profile)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.playlist_name)) })
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(checked = kids, onCheckedChange = { kids = it })
                    Text(stringResource(R.string.kids_profile))
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.addProfile(name, kids); onDone() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---- Mini player -------------------------------------------------------------------------------

/** The channel keeps playing in a corner while browsing; select it to return to full screen. */
@Composable
fun MiniPlayer(vm: AppViewModel, modifier: Modifier) {
    val state by vm.playback.state.collectAsState()
    val ch = state.channel ?: return
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val w = if (vm.isTv) 340.dp else 220.dp
    Surface(
        onClick = { vm.expandMini() },
        interactionSource = source,
        shape = RoundedCornerShape(14.dp),
        color = Color.Black,
        shadowElevation = 12.dp,
        modifier = modifier.width(w).aspectRatio(16f / 9f)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
    ) {
        Box {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = vm.playback.player
                        isFocusable = false
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                vm.title(ch),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            IconButton(onClick = { vm.closePlayer() }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.stop), tint = Color.White)
            }
        }
    }
}
