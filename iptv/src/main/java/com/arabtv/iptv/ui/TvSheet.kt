package com.arabtv.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.remote.TvTarget
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ctx).apply {
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this) }
            }
        },
        modifier = modifier,
    )
}

/**
 * Choose where to watch: Chromecast, a TV running this app on the same Wi-Fi, or a paired TV
 * anywhere over the internet. If [channel] is set, picking a TV sends it straight away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSheet(vm: AppViewModel, channel: Channel?, onDismiss: () -> Unit) {
    val found by vm.discovery.found.collectAsState()
    val settings by vm.settings.collectAsState()
    val target by vm.target.collectAsState()
    var addCode by remember { mutableStateOf(false) }
    var addIp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        vm.discovery.start()
        onDispose { vm.discovery.stop() }
    }

    fun pick(t: TvTarget) {
        if (channel != null) vm.sendToTv(t, channel) else vm.selectTarget(t)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.watch_on_tv), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            channel?.let { Text(vm.title(it), color = Gold) }
            Spacer(Modifier.size(12.dp))

            // 1. Our own app on the TV, same network.
            Section(stringResource(R.string.same_wifi), stringResource(R.string.same_wifi_help))
            if (found.isEmpty() && settings.manualTvs.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.searching_tvs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            found.forEach { tv -> TargetRow(tv.label, "${tv.host}:${tv.port}", target?.key == tv.key, onClick = { pick(tv) }) }
            settings.manualTvs.forEach { m ->
                val t = TvTarget.Lan(m.name, m.host, m.port)
                if (found.none { it.key == t.key }) {
                    TargetRow(m.name, "${m.host}:${m.port}", target?.key == t.key, onClick = { pick(t) }, onDelete = { vm.removeManualTv(m) })
                }
            }
            TextButton(onClick = { addIp = true }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.add_by_ip))
            }

            // 2. Anywhere: relay with a pairing code.
            Section(stringResource(R.string.anywhere), stringResource(R.string.anywhere_help))
            settings.pairedTvs.forEach { p ->
                val t = TvTarget.Remote(p.name, p.code)
                TargetRow(p.name, stringResource(R.string.via_internet), target?.key == t.key, onClick = { pick(t) }, onDelete = { vm.removePairedTv(p) })
            }
            TextButton(onClick = { addCode = true }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.add_by_code))
            }

            // 3. Google Cast.
            if (vm.cast.castContext != null) {
                Section(stringResource(R.string.chromecast), stringResource(R.string.chromecast_help))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CastButton(Modifier.size(48.dp))
                    Text(stringResource(R.string.chromecast_tap), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (target != null) {
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = { vm.selectTarget(null); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.play_on_phone))
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (addCode) {
        TextFieldsDialog(
            title = stringResource(R.string.add_by_code),
            message = stringResource(R.string.add_by_code_help),
            fields = listOf(
                FieldSpec(stringResource(R.string.tv_code), capitalize = true),
                FieldSpec(stringResource(R.string.tv_name_optional)),
            ),
            onDismiss = { addCode = false },
            onConfirm = { values ->
                if (vm.addPairedTv(values[1], values[0])) {
                    addCode = false
                    val t = vm.target.value
                    if (t != null) pick(t)
                }
            },
        )
    }
    if (addIp) {
        TextFieldsDialog(
            title = stringResource(R.string.add_by_ip),
            message = stringResource(R.string.add_by_ip_help),
            fields = listOf(
                FieldSpec(stringResource(R.string.tv_address), keyboard = KeyboardType.Uri),
                FieldSpec(stringResource(R.string.tv_name_optional)),
            ),
            onDismiss = { addIp = false },
            onConfirm = { values ->
                if (vm.addManualTv(values[1], values[0])) {
                    addIp = false
                    val t = vm.target.value
                    if (t != null) pick(t)
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, help: String) {
    Spacer(Modifier.size(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = Gold)
    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TargetRow(name: String, detail: String, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Surface(onClick = onClick, color = if (selected) Brand.copy(alpha = 0.4f) else Surface1, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tv), null, tint = Gold)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = Gold)
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
            }
        }
    }
}

data class FieldSpec(
    val label: String,
    val initial: String = "",
    val keyboard: KeyboardType = KeyboardType.Text,
    val capitalize: Boolean = false,
)

@Composable
fun TextFieldsDialog(
    title: String,
    message: String?,
    fields: List<FieldSpec>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    confirmLabel: String = stringResource(R.string.ok),
) {
    var values by remember { mutableStateOf(fields.map { it.initial }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                fields.forEachIndexed { i, f ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { v -> values = values.toMutableList().also { it[i] = v } },
                        label = { Text(f.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = f.keyboard,
                            capitalization = if (f.capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                        visualTransformation = if (f.keyboard == KeyboardType.NumberPassword) {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(values) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
