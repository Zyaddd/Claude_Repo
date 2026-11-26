package com.arabtv.iptv.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.arabtv.iptv.BuildConfigInfo
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Pairing
import com.arabtv.iptv.data.Prefs

@Composable
fun SettingsScreen(vm: AppViewModel, onRequestPin: (disable: Boolean) -> Unit) {
    val s by vm.settings.collectAsState()
    val status by vm.receiver.status.collectAsState()
    val failures by vm.loadFailures.collectAsState()
    val channels by vm.catalog.collectAsState()
    val everything by vm.allChannels.collectAsState()
    val finding by vm.finding.collectAsState()
    val pending by vm.pendingChannels.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goHome() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            // ---- Receiver ----
            item { Header(stringResource(R.string.sec_receiver)) }
            item {
                SwitchRow(
                    stringResource(R.string.receiver_enable),
                    stringResource(R.string.receiver_enable_help),
                    s.receiverEnabled,
                ) { on -> vm.updateSettings { it.copy(receiverEnabled = on) } }
            }
            if (s.receiverEnabled) {
                item {
                    Surface(color = Surface1, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.pairing_code), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer {
                                Text(
                                    Pairing.format(s.pairingCode),
                                    fontSize = 34.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Gold,
                                )
                            }
                            Text(
                                stringResource(if (status.relayConnected) R.string.relay_online else R.string.relay_connecting),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.lan_address), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (status.lanAddress != null && status.lanPort > 0) "${status.lanAddress}:${status.lanPort}"
                                else stringResource(R.string.lan_unavailable),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.receiver_steps), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { ActionRow(stringResource(R.string.new_code), stringResource(R.string.new_code_help)) { vm.newPairingCode() } }
                item {
                    ActionRow(stringResource(R.string.relay_server), s.relayServer) { dialog = "relay" }
                }
            }

            // ---- Channels ----
            item { Header(stringResource(R.string.sec_channels)) }
            item {
                ActionRow(
                    stringResource(if (finding) R.string.finding_channels else R.string.find_new_channels),
                    if (pending.isNotEmpty()) stringResource(R.string.new_channels_banner, pending.size)
                    else stringResource(R.string.find_new_channels_help),
                ) { vm.findNewChannels() }
            }
            item {
                ActionRow(
                    stringResource(R.string.hidden_channels),
                    stringResource(R.string.hidden_channels_count, s.hiddenIds.size),
                ) { dialog = "hidden" }
            }
            item {
                SwitchRow(stringResource(R.string.channel_osd), stringResource(R.string.channel_osd_help), s.channelOsd) { on ->
                    vm.updateSettings { it.copy(channelOsd = on) }
                }
            }
            item {
                SwitchRow(stringResource(R.string.show_guide), stringResource(R.string.show_guide_help), s.showGuide) { on ->
                    vm.updateSettings { it.copy(showGuide = on) }
                    if (on) vm.loadGuide(force = true)
                }
            }
            if (s.showGuide) {
                item { ActionRow(stringResource(R.string.guide_sources), s.guideUrls.lines().size.toString() + " ×  XMLTV") { dialog = "guide" } }
            }

            // ---- Updates ----
            item { Header(stringResource(R.string.sec_updates)) }
            item {
                ActionRow(
                    stringResource(R.string.check_updates),
                    stringResource(R.string.current_version, BuildConfigInfo.versionName(vm.getApplication<android.app.Application>())),
                ) { vm.checkForUpdates() }
            }
            item {
                SwitchRow(stringResource(R.string.auto_update), stringResource(R.string.auto_update_help), s.autoUpdate) { on ->
                    vm.updateSettings { it.copy(autoUpdate = on) }
                }
            }

            // ---- Playback ----
            item { Header(stringResource(R.string.sec_playback)) }
            item {
                SwitchRow(stringResource(R.string.autoplay_last), stringResource(R.string.autoplay_last_help), s.autoplayLast) { on ->
                    vm.updateSettings { it.copy(autoplayLast = on) }
                }
            }
            item {
                SwitchRow(stringResource(R.string.check_streams), stringResource(R.string.check_streams_help), s.checkStreams) { on ->
                    vm.updateSettings { it.copy(checkStreams = on) }
                    if (on) vm.checkStreams()
                }
            }
            item {
                SwitchRow(stringResource(R.string.hide_offline), null, s.hideOffline) { on -> vm.updateSettings { it.copy(hideOffline = on) } }
            }
            item {
                SwitchRow(stringResource(R.string.hide_geo), stringResource(R.string.hide_geo_help), s.hideGeoBlocked) { on ->
                    vm.updateSettings { it.copy(hideGeoBlocked = on) }
                }
            }
            item { ActionRow(stringResource(R.string.check_now), null) { vm.checkStreams() } }

            // ---- Playlists ----
            item { Header(stringResource(R.string.sec_playlists)) }
            item {
                Text(
                    stringResource(R.string.catalog_info, channels.count { it.playlist == null }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            s.playlists.forEach { p ->
                item(key = "pl:" + p.url) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(p.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            failures[p.url]?.let {
                                Text(stringResource(R.string.playlist_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { vm.removePlaylist(p) }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
                    }
                }
            }
            item { ActionRow(stringResource(R.string.add_playlist), stringResource(R.string.add_playlist_help)) { dialog = "playlist" } }

            // ---- Kids ----
            item { Header(stringResource(R.string.sec_kids)) }
            item {
                if (s.kidsMode) {
                    ActionRow(stringResource(R.string.kids_disable), null) { onRequestPin(true) }
                } else {
                    ActionRow(stringResource(R.string.kids_enable), stringResource(R.string.kids_enable_help)) { dialog = "kids" }
                }
            }
            if (s.kidsMode) {
                item { ActionRow(stringResource(R.string.kids_lock_now), null) { vm.lockKids() } }
            }

            // ---- General ----
            item { Header(stringResource(R.string.sec_general)) }
            item { ActionRow(stringResource(R.string.language), currentLanguageLabel()) { dialog = "language" } }
            item { ActionRow(stringResource(R.string.clear_history), null) { vm.clearHistory() } }
            item {
                Text(
                    stringResource(R.string.about_text, BuildConfigInfo.versionName(vm.getApplication<android.app.Application>())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }

    when (dialog) {
        "relay" -> TextFieldsDialog(
            title = stringResource(R.string.relay_server),
            message = stringResource(R.string.relay_server_help),
            fields = listOf(FieldSpec("URL", s.relayServer, KeyboardType.Uri)),
            onDismiss = { dialog = null },
            onConfirm = { v ->
                val url = v[0].trim().trimEnd('/').ifBlank { Prefs.DEFAULT_RELAY }
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    vm.updateSettings { it.copy(relayServer = url) }
                    dialog = null
                }
            },
        )
        "playlist" -> TextFieldsDialog(
            title = stringResource(R.string.add_playlist),
            message = stringResource(R.string.add_playlist_help),
            fields = listOf(
                FieldSpec(stringResource(R.string.playlist_url), keyboard = KeyboardType.Uri),
                FieldSpec(stringResource(R.string.playlist_name)),
            ),
            onDismiss = { dialog = null },
            onConfirm = { v -> if (vm.addPlaylist(v[1], v[0])) dialog = null },
        )
        "kids" -> TextFieldsDialog(
            title = stringResource(R.string.kids_enable),
            message = stringResource(R.string.kids_pin_set),
            fields = listOf(FieldSpec(stringResource(R.string.pin), keyboard = KeyboardType.NumberPassword)),
            onDismiss = { dialog = null },
            onConfirm = { v -> if (vm.enableKidsMode(v[0].trim())) dialog = null },
        )
        "hidden" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.hidden_channels)) },
            text = {
                val hidden = everything.filter { it.id in s.hiddenIds }
                if (hidden.isEmpty()) {
                    Text(stringResource(R.string.hidden_none))
                } else {
                    LazyColumn {
                        items(hidden.size) { i ->
                            val ch = hidden[i]
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(vm.title(ch), modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = { vm.unhideChannel(ch.id) }) {
                                    Text(stringResource(R.string.show_again))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ok)) }
            },
        )
        "guide" -> TextFieldsDialog(
            title = stringResource(R.string.guide_sources),
            message = stringResource(R.string.guide_sources_help),
            fields = listOf(FieldSpec("URL", s.guideUrls.lines().joinToString(" "), KeyboardType.Uri)),
            onDismiss = { dialog = null },
            onConfirm = { v ->
                val urls = v[0].split(' ', '\n', ',').map { it.trim() }.filter { it.startsWith("http") }
                vm.updateSettings { it.copy(guideUrls = urls.joinToString("\n").ifBlank { Prefs.DEFAULT_GUIDE_URLS }) }
                vm.loadGuide(force = true)
                dialog = null
            },
        )
        "language" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    MenuItem(stringResource(R.string.lang_system)) { setLanguage(null); dialog = null }
                    MenuItem("العربية") { setLanguage("ar"); dialog = null }
                    MenuItem("English") { setLanguage("en"); dialog = null }
                }
            },
            confirmButton = {},
        )
    }
}

private fun setLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
    )
}

@Composable
private fun currentLanguageLabel(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    return when {
        locales.isEmpty -> stringResource(R.string.lang_system)
        locales[0]?.language == "ar" -> "العربية"
        else -> "English"
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        color = Gold,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(title: String, help: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = { onChange(!checked) },
        interactionSource = source,
        border = focusBorder(source),
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // Not focusable itself: the whole row is the control, so D-pad focus lands once per row.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun ActionRow(title: String, detail: String?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        border = focusBorder(source),
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        }
    }
}
