package com.arabtv.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.Pairing
import com.arabtv.iptv.remote.Action

@Composable
fun tabLabel(t: Tab): String = stringResource(
    when (t) {
        Tab.NEWS -> R.string.tab_news
        Tab.DOCUMENTARY -> R.string.tab_documentary
        Tab.KIDS -> R.string.tab_kids
        Tab.ALL -> R.string.tab_all
        Tab.FAVORITES -> R.string.tab_favorites
        Tab.RECENT -> R.string.tab_recent
        Tab.PLAYLISTS -> R.string.tab_playlists
    },
)

@Composable
fun HomeScreen(vm: AppViewModel, onOpenTvSheet: (Channel?) -> Unit, onRequestPin: (disable: Boolean) -> Unit) {
    val all by vm.channels.collectAsState()
    val tab by vm.tab.collectAsState()
    val query by vm.query.collectAsState()
    val settings by vm.settings.collectAsState()
    val health by vm.health.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val target by vm.target.collectAsState()
    val kidsUnlocked by vm.kidsUnlocked.collectAsState()
    val castDevice by vm.cast.deviceName.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Channel?>(null) }

    val locked = settings.kidsMode && !kidsUnlocked
    val tabs = when {
        locked -> listOf(Tab.KIDS)
        else -> buildList {
            addAll(listOf(Tab.NEWS, Tab.DOCUMENTARY, Tab.KIDS, Tab.ALL, Tab.FAVORITES, Tab.RECENT))
            if (settings.playlists.isNotEmpty()) add(Tab.PLAYLISTS)
        }
    }
    val currentTab = if (tab in tabs) tab else tabs.first()
    val list = remember(all, currentTab, query, settings, health, kidsUnlocked) {
        vm.visibleChannels(all, currentTab, query, settings, health, kidsUnlocked)
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Top bar ----
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (vm.isTv) ReceiverSubtitle(vm)
                else castDevice?.let {
                    Text(stringResource(R.string.casting_to, it), color = Gold, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = { searching = !searching; if (!searching) vm.setQuery("") }) {
                Icon(Icons.Filled.Search, stringResource(R.string.search))
            }
            if (!locked) {
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
            }
            if (!vm.isTv && !locked) {
                IconButton(onClick = { onOpenTvSheet(null) }) {
                    Icon(painterResource(R.drawable.ic_tv), stringResource(R.string.watch_on_tv))
                }
                if (vm.cast.castContext != null) CastButton(Modifier.size(48.dp))
            }
            if (locked) {
                IconButton(onClick = { onRequestPin(false) }) { Icon(Icons.Filled.Lock, stringResource(R.string.kids_unlock)) }
            } else {
                IconButton(onClick = { vm.openSettings() }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
            }
        }

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                trailingIcon = {
                    IconButton(onClick = { vm.setQuery(""); searching = false }) { Icon(Icons.Filled.Close, null) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(currentTab),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                tabs.forEach { t ->
                    Tab(selected = t == currentTab, onClick = { vm.setTab(t) }, text = { Text(tabLabel(t)) })
                }
            }
        } else {
            Text(
                tabLabel(currentTab),
                style = MaterialTheme.typography.titleMedium,
                color = Gold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                all.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    stringResource(
                        when (currentTab) {
                            Tab.FAVORITES -> R.string.empty_favorites
                            Tab.RECENT -> R.string.empty_recent
                            else -> R.string.empty_list
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (vm.isTv) 190.dp else 148.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { ch ->
                        ChannelCard(
                            channel = ch,
                            title = vm.title(ch),
                            favorite = ch.id in settings.favorites,
                            health = health[ch.id],
                            playing = false,
                            onClick = { vm.open(ch, list) },
                            onLongClick = { menuFor = ch },
                        )
                    }
                }
            }
        }

        target?.let { tv -> RemoteBar(tv.label, vm) }
    }

    menuFor?.let { ch ->
        ChannelMenu(
            title = vm.title(ch),
            favorite = ch.id in settings.favorites,
            showTv = !vm.isTv,
            onPlayHere = {
                menuFor = null
                vm.selectTarget(null)
                vm.open(ch, list)
            },
            onSendToTv = { menuFor = null; onOpenTvSheet(ch) },
            onFavorite = { vm.toggleFavorite(ch.id); menuFor = null },
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun ReceiverSubtitle(vm: AppViewModel) {
    val status by vm.receiver.status.collectAsState()
    val settings by vm.settings.collectAsState()
    if (!settings.receiverEnabled) return
    val text = buildString {
        append(stringResource(R.string.receiver_code_short, Pairing.format(settings.pairingCode)))
        if (status.lanAddress != null && status.lanPort > 0) append("  ·  ${status.lanAddress}:${status.lanPort}")
        append("  ·  ")
        append(stringResource(if (status.relayConnected) R.string.relay_online else R.string.relay_connecting))
    }
    Text(text, color = Gold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Phone controls for the TV it is driving. */
@Composable
fun RemoteBar(label: String, vm: AppViewModel) {
    Surface(color = Surface2, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_tv), null, tint = Gold, modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    stringResource(R.string.controlling, label),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { vm.selectTarget(null) }) { Text(stringResource(R.string.play_on_phone)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteKey(R.drawable.ic_skip_previous, R.string.prev_channel) { vm.remote(Action.PREV) }
                RemoteKey(R.drawable.ic_play, R.string.play_pause) { vm.remote(Action.TOGGLE) }
                RemoteKey(R.drawable.ic_skip_next, R.string.next_channel) { vm.remote(Action.NEXT) }
                RemoteKey(R.drawable.ic_volume_down, R.string.volume_down) { vm.remote(Action.VOL_DOWN) }
                RemoteKey(R.drawable.ic_volume_off, R.string.mute) { vm.remote(Action.MUTE) }
                RemoteKey(R.drawable.ic_volume_up, R.string.volume_up) { vm.remote(Action.VOL_UP) }
                RemoteKey(R.drawable.ic_stop, R.string.stop) { vm.remote(Action.STOP) }
            }
        }
    }
}

@Composable
private fun RemoteKey(icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(icon), stringResource(label)) }
}

@Composable
private fun ChannelMenu(
    title: String,
    favorite: Boolean,
    showTv: Boolean,
    onPlayHere: () -> Unit,
    onSendToTv: () -> Unit,
    onFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MenuItem(stringResource(R.string.play_here), onPlayHere)
                if (showTv) MenuItem(stringResource(R.string.send_to_tv), onSendToTv)
                MenuItem(stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite), onFavorite)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}

