package com.arabtv.iptv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabtv.iptv.R
import com.arabtv.iptv.update.UpdateState
import com.arabtv.iptv.update.Updater

/**
 * Update prompt: offered when a newer build is published, with download progress. Results of a
 * manual check (up to date, not reachable, failed) are shown here too.
 */
@Composable
fun UpdateDialog(vm: AppViewModel, state: UpdateState, dismissed: Boolean, manual: Boolean) {
    when (state) {
        is UpdateState.Available -> if (!dismissed) {
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text(stringResource(R.string.update_available, state.release.versionName)) },
                text = { Text(stringResource(R.string.update_available_body, "1.0.${Updater.currentVersionCode}")) },
                confirmButton = {
                    TextButton(onClick = { vm.installUpdate(state.release) }) { Text(stringResource(R.string.update_now)) }
                },
                dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text(stringResource(R.string.later)) } },
            )
        }
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, state.release.versionName)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = Gold)
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )
        is UpdateState.NeedsPermission -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text(stringResource(R.string.update_permission_title)) },
                text = { Text(stringResource(R.string.update_permission_body)) },
                confirmButton = {
                    TextButton(onClick = { Updater.openInstallSettings(context) }) { Text(stringResource(R.string.open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.installUpdate(state.release) }) { Text(stringResource(R.string.try_again)) }
                },
            )
        }
        UpdateState.Installing -> AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text(stringResource(R.string.update_installing)) },
            text = { Text(stringResource(R.string.update_installing_body)) },
            confirmButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text(stringResource(R.string.ok)) } },
        )
        UpdateState.SignatureMismatch -> InfoDialog(vm, R.string.update_failed, R.string.update_signature)
        // Background checks stay silent unless there is something to install.
        UpdateState.UpToDate -> if (manual) InfoDialog(vm, R.string.update_latest, R.string.update_latest_body)
        UpdateState.NotReachable -> if (manual) InfoDialog(vm, R.string.update_failed, R.string.update_not_reachable)
        is UpdateState.Failed -> if (manual) {
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text(stringResource(R.string.update_failed)) },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text(stringResource(R.string.ok)) } },
            )
        }
        else -> Unit
    }
}

@Composable
private fun InfoDialog(vm: AppViewModel, title: Int, body: Int) {
    AlertDialog(
        onDismissRequest = { vm.dismissUpdate() },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text(stringResource(R.string.ok)) } },
    )
}
