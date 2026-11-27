@file:OptIn(UnstableApi::class)

package com.arabtv.iptv.ui

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.player.QualityOption
import kotlinx.coroutines.delay

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
)

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    fullscreen: Boolean,
    inPip: Boolean,
    onToggleFullscreen: () -> Unit,
    onPlayerView: (PlayerView?) -> Unit,
    onOpenTvSheet: (Channel?) -> Unit,
) {
    val state by vm.playback.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val health by vm.health.collectAsState()
    val castDevice by vm.cast.deviceName.collectAsState()
    val showOptions by vm.showOptions.collectAsState()
    val channel = state.channel ?: return
    // The player only reports changes, so start from "hidden" (starting from "shown" left the
    // top bar stuck on screen when the controls never appeared).
    var controls by remember { mutableStateOf(false) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    val currentFullscreen by rememberUpdatedState(fullscreen)
    val currentToggle by rememberUpdatedState(onToggleFullscreen)
    val currentInPip by rememberUpdatedState(inPip)
    val currentResize by rememberUpdatedState(resizeModes[resizeIndex])

    val video = remember {
        movableContentOf {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = vm.playback.player
                        keepScreenOn = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setShowFastForwardButton(false)
                        setShowRewindButton(false)
                        controllerShowTimeoutMs = 4000
                        // On TV, controls appear only when OK is pressed, not on every rebuffer.
                        controllerAutoShow = !vm.isTv
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                val visible = visibility == View.VISIBLE
                                controls = visible
                                vm.controllerVisible = visible
                            },
                        )
                        if (!vm.isTv) setFullscreenButtonClickListener { currentToggle() }
                        isFocusable = true
                        onPlayerView(this)
                        if (vm.isTv) post { requestFocus() }
                    }
                },
                update = { v ->
                    v.resizeMode = currentResize
                    v.useController = !currentInPip
                    if (!vm.isTv) v.setFullscreenButtonState(currentFullscreen)
                },
                onRelease = { v ->
                    v.player = null
                    onPlayerView(null)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Composable
    fun VideoBox(modifier: Modifier) {
        Box(modifier.background(Color.Black)) {
            video()
            if (!inPip) {
                PlayerOverlays(
                    vm = vm,
                    channel = channel,
                    controls = controls,
                    fullscreenLayout = fullscreen || vm.isTv,
                    buffering = state.buffering,
                    switching = state.switching,
                    repairing = state.repairing,
                    showOsd = settings.channelOsd,
                    error = state.error,
                    castDevice = castDevice,
                    favorite = channel.id in settings.favorites,
                    onOpenTvSheet = onOpenTvSheet,
                )
            }
        }
    }

    val split by vm.split.collectAsState()
    when {
        split && !inPip -> Row(Modifier.fillMaxSize().background(Color.Black)) {
            VideoBox(Modifier.weight(1f).fillMaxHeight())
            SecondPane(vm, Modifier.weight(1f).fillMaxHeight())
        }
        fullscreen || inPip || vm.isTv -> VideoBox(Modifier.fillMaxSize())
        landscape -> Row(Modifier.fillMaxSize()) {
            VideoBox(Modifier.weight(0.62f).fillMaxHeight())
            QueueList(vm, channel, health, Modifier.weight(0.38f).fillMaxHeight())
        }
        else -> Column(Modifier.fillMaxSize()) {
            VideoBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            NowPlayingInfo(vm, channel, channel.id in settings.favorites, health[channel.id])
            QueueList(vm, channel, health, Modifier.weight(1f).fillMaxWidth())
        }
    }

    if (showOptions) {
        PlayerOptionsDialog(
            vm = vm,
            channel = channel,
            favorite = channel.id in settings.favorites,
            resizeIndex = resizeIndex,
            onResize = { resizeIndex = (resizeIndex + 1) % resizeModes.size },
            onOpenTvSheet = onOpenTvSheet,
            onDismiss = { vm.setShowOptions(false) },
        )
    }
}

/** More than 20 seconds behind the live edge (after a pause or rewind). */
private fun isBehindLive(p: androidx.media3.common.Player): Boolean {
    val offset = p.currentLiveOffset
    return p.isCurrentMediaItemLive && offset != androidx.media3.common.C.TIME_UNSET && offset > 20_000
}

/** The second channel in split screen: no controls; select it to give it the sound. */
@Composable
private fun SecondPane(vm: AppViewModel, modifier: Modifier) {
    val st by vm.second.state.collectAsState()
    val soundHere by vm.secondAudio.collectAsState()
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = vm.second.player
                    isFocusable = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            onClick = { vm.setSecondAudio(!soundHere) },
            interactionSource = source,
            border = focusBorder(source),
            color = Color(0xAA000000),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        ) {
            Text(
                (if (soundHere) "🔊 " else "🔇 ") + (st.channel?.let { vm.title(it) } ?: ""),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        IconButton(onClick = { vm.closeSplit() }, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(androidx.compose.material.icons.Icons.Filled.Close, stringResource(R.string.close_second), tint = Color.White)
        }
        st.error?.let {
            Text(stringResource(R.string.stream_failed), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun PlayerOverlays(
    vm: AppViewModel,
    channel: Channel,
    controls: Boolean,
    fullscreenLayout: Boolean,
    buffering: Boolean,
    switching: Boolean,
    repairing: Boolean,
    showOsd: Boolean,
    error: String?,
    castDevice: String?,
    favorite: Boolean,
    onOpenTvSheet: (Channel?) -> Unit,
) {
    // Flash the channel name for 2 seconds after every change, then get out of the way.
    var osd by remember { mutableStateOf(showOsd) }
    LaunchedEffect(channel.id, showOsd) {
        osd = showOsd
        delay(2000)
        osd = false
    }
    val guide by vm.guide.collectAsState()
    val number by vm.numberEntry.collectAsState()
    val (nowP, nextP) = com.arabtv.iptv.data.GuideRepository.nowAndNext(guide[channel.id])
    Box(Modifier.fillMaxSize()) {
        // TV: the bar is only the 2-second channel banner. Phone: it also carries Back/Cast/Options,
        // so it follows the player controls.
        val showBar = osd || error != null || (!vm.isTv && controls)
        if (showBar) {
            Row(
                Modifier.fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .then(if (fullscreenLayout) Modifier.padding(horizontal = 8.dp) else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!vm.isTv) {
                    IconButton(onClick = { vm.closePlayer() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                } else {
                    Spacer(Modifier.size(16.dp))
                }
                ChannelLogo(channel, vm.title(channel), Modifier.size(width = 52.dp, height = 34.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.title(channel),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val num = vm.numberOf(channel)
                    val height = vm.playback.state.collectAsState().value.videoHeight
                    val sub = listOfNotNull(
                        num.takeIf { it > 0 }?.toString(),
                        height.takeIf { it > 0 }?.let { "${it}p" },
                        nowP?.let { stringResource(R.string.now_playing, it.title) },
                        nextP?.let { stringResource(R.string.next_up, it.title) },
                    ).joinToString("  ·  ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            color = Color(0xFFD0D5DD),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (favorite) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.padding(8.dp))
                if (!vm.isTv && controls) {
                    if (vm.cast.castContext != null) CastButton(Modifier.size(48.dp))
                    IconButton(onClick = { onOpenTvSheet(channel) }) {
                        Icon(painterResource(R.drawable.ic_tv), stringResource(R.string.send_to_tv), tint = Color.White)
                    }
                    IconButton(onClick = { vm.setShowOptions(true) }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.options), tint = Color.White)
                    }
                }
                if (vm.isTv && osd) {
                    Text(
                        stringResource(R.string.tv_hint),
                        color = Color(0xFFB0BAC5),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            }
        }

        // "Go live" after pausing or rewinding a live channel.
        val behind by produceState(false, channel.id) {
            while (true) {
                value = isBehindLive(vm.playback.player)
                kotlinx.coroutines.delay(1000)
            }
        }
        if (behind && error == null && castDevice == null) {
            androidx.compose.material3.Button(
                onClick = { vm.goLive() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LiveRed, contentColor = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 84.dp),
            ) { Text(stringResource(R.string.go_live)) }
        }

        if (number.isNotEmpty()) {
            Text(
                number,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }

        if ((switching || repairing) && error == null && castDevice == null) {
            Text(
                stringResource(if (repairing) R.string.repairing_link else R.string.trying_other_source),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        if (castDevice != null) {
            CastingPanel(vm, castDevice, Modifier.align(Alignment.Center))
        } else if (error != null) {
            ErrorPanel(vm, channel, error, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun CastingPanel(vm: AppViewModel, device: String, modifier: Modifier) {
    Surface(modifier.padding(16.dp), color = Color(0xDD151B23), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.casting_to, device), color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.cast_note),
                color = Color(0xFFB0BAC5),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp).padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.cast.togglePause() }) { Text(stringResource(R.string.play_pause)) }
                Button(onClick = { vm.cast.stopCasting() }) { Text(stringResource(R.string.stop_casting)) }
            }
        }
    }
}

@Composable
private fun ErrorPanel(vm: AppViewModel, channel: Channel, error: String, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(error) { runCatching { focus.requestFocus() } }
    Surface(modifier.padding(16.dp), color = Color(0xEE151B23), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.stream_failed), color = Color.White, fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFFB0BAC5), style = MaterialTheme.typography.bodySmall)
            if (channel.geoBlocked) {
                Text(
                    stringResource(R.string.geo_note),
                    color = Gold,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp).padding(top = 6.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.playback.retry() }, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.retry))
                }
                if (vm.playback.queue.size > 1) {
                    OutlinedButton(onClick = { vm.zap(1) }) { Text(stringResource(R.string.next_channel)) }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingInfo(vm: AppViewModel, channel: Channel, favorite: Boolean, health: Boolean?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(vm.title(channel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            ChannelBadges(channel, health)
        }
        IconButton(onClick = { vm.toggleFavorite(channel.id) }) {
            Icon(
                Icons.Filled.Star,
                stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                tint = if (favorite) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueList(vm: AppViewModel, current: Channel, health: Map<String, Boolean>, modifier: Modifier) {
    val queue = vm.playback.queue
    val listState = rememberLazyListState()
    LaunchedEffect(current.id) {
        val i = queue.indexOfFirst { it.id == current.id }
        if (i >= 0) listState.animateScrollToItem(maxOf(0, i - 1))
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(8.dp)) {
        items(queue, key = { it.id }) { ch ->
            ChannelRow(ch, vm.title(ch), ch.id == current.id, health[ch.id]) { vm.zapTo(ch) }
        }
    }
}

@Composable
private fun PlayerOptionsDialog(
    vm: AppViewModel,
    channel: Channel,
    favorite: Boolean,
    resizeIndex: Int,
    onResize: () -> Unit,
    onOpenTvSheet: (Channel?) -> Unit,
    onDismiss: () -> Unit,
) {
    val health by vm.health.collectAsState()
    val sleepAt by vm.sleepAt.collectAsState()
    var page by remember { mutableStateOf("main") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(page) { runCatching { firstFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Surface1,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(24.dp).widthIn(max = 640.dp).heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(vm.title(channel), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                when (page) {
                    "quality" -> {
                        val options = remember { vm.playback.qualities() }
                        MenuItem(stringResource(R.string.quality_auto)) { vm.playback.setQuality(null); onDismiss() }
                        options.forEach { q: QualityOption ->
                            MenuItem(q.label) { vm.playback.setQuality(q); onDismiss() }
                        }
                        if (options.isEmpty()) {
                            Text(stringResource(R.string.quality_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FocusAnchor(firstFocus)
                    }
                    "source" -> {
                        val links = remember(channel.id) { vm.linksOf(channel) }
                        val st by vm.playback.state.collectAsState()
                        val playing = st.channel?.sources?.getOrNull(st.sourceIndex)?.url
                        Text(
                            stringResource(R.string.source_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                            items(links.size) { i ->
                                val (src, probe) = links[i]
                                val res = probe?.height?.takeIf { it > 0 }?.let { "${it}p" } ?: src.quality
                                val state = when (probe?.alive) {
                                    true -> stringResource(R.string.source_ok)
                                    false -> stringResource(R.string.source_dead)
                                    null -> stringResource(R.string.source_unchecked)
                                }
                                val host = runCatching { java.net.URI(src.url).host }.getOrNull().orEmpty()
                                val label = listOfNotNull(
                                    stringResource(R.string.source_n, i + 1),
                                    res,
                                    state,
                                ).joinToString("  ·  ") + (if (src.url == playing) "  ✓" else "")
                                Column(Modifier.fillMaxWidth()) {
                                    MenuItem(label) { vm.chooseSource(channel, src.url); onDismiss() }
                                    Text(
                                        host,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                        FocusAnchor(firstFocus)
                    }
                    "split" -> {
                        Text(
                            stringResource(R.string.two_channels_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val all by vm.catalog.collectAsState()
                        val choices = remember(all) { all.filter { it.id != channel.id && it.playableSources.isNotEmpty() }.take(200) }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                            items(choices, key = { it.id }) { ch ->
                                ChannelRow(ch, vm.title(ch), false, null) { vm.openSplit(ch) }
                            }
                        }
                        FocusAnchor(firstFocus)
                    }
                    "sleep" -> {
                        MenuItem(stringResource(R.string.sleep_off)) { vm.setSleepTimer(null); onDismiss() }
                        listOf(15, 30, 60, 90, 120).forEach { m ->
                            MenuItem(stringResource(R.string.sleep_minutes, m)) { vm.setSleepTimer(m); onDismiss() }
                        }
                        FocusAnchor(firstFocus)
                    }
                    else -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OptionButton(
                                Icons.Filled.Star,
                                stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                                Modifier.weight(1f).focusRequester(firstFocus),
                            ) { vm.toggleFavorite(channel.id) }
                            OptionButton(R.drawable.ic_quality, stringResource(R.string.quality), Modifier.weight(1f)) {
                                page = "quality"
                            }
                            if (channel.playableSources.size > 1) {
                                OptionButton(Icons.Filled.Refresh, stringResource(R.string.source), Modifier.weight(1f)) {
                                    page = "source"
                                }
                            }
                            OptionButton(
                                R.drawable.ic_aspect,
                                stringResource(
                                    listOf(R.string.aspect_fit, R.string.aspect_zoom, R.string.aspect_fill)[resizeIndex],
                                ),
                                Modifier.weight(1f),
                            ) { onResize() }
                            OptionButton(
                                R.drawable.ic_timer,
                                sleepAt?.let { at ->
                                    stringResource(R.string.sleep_left, ((at - System.currentTimeMillis()) / 60_000L + 1).toInt())
                                } ?: stringResource(R.string.sleep_timer),
                                Modifier.weight(1f),
                            ) { page = "sleep" }
                            OptionButton(Icons.Filled.Refresh, stringResource(R.string.reload), Modifier.weight(1f)) {
                                vm.playback.retry(); onDismiss()
                            }
                            if (!vm.isTv) {
                                OptionButton(R.drawable.ic_tv, stringResource(R.string.send_to_tv), Modifier.weight(1f)) {
                                    onDismiss(); onOpenTvSheet(channel)
                                }
                            }
                        }
                        Spacer(Modifier.size(4.dp))
                        val activity = LocalContext.current as? MainActivity
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OptionButton(R.drawable.ic_play, stringResource(R.string.go_live), Modifier.weight(1f)) {
                                vm.goLive(); onDismiss()
                            }
                            if (activity?.supportsPip() == true) {
                                OptionButton(R.drawable.ic_aspect, stringResource(R.string.pip), Modifier.weight(1f)) {
                                    onDismiss(); activity.enterPipNow()
                                }
                            }
                            OptionButton(R.drawable.ic_tv, stringResource(R.string.mini_player), Modifier.weight(1f)) {
                                vm.minimize()
                            }
                            OptionButton(Icons.Filled.Add, stringResource(R.string.two_channels), Modifier.weight(1f)) {
                                page = "split"
                            }
                            OptionButton(Icons.Filled.Warning, stringResource(R.string.report_problem), Modifier.weight(1f)) {
                                vm.reportChannel(channel); onDismiss()
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.channels), color = Gold, style = MaterialTheme.typography.labelLarge)
                        val queue = vm.playback.queue
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = maxOf(0, queue.indexOfFirst { it.id == channel.id }),
                        )
                        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                            items(queue, key = { it.id }) { ch ->
                                ChannelRow(ch, vm.title(ch), ch.id == channel.id, health[ch.id]) {
                                    vm.zapTo(ch); onDismiss()
                                }
                            }
                        }
                    }
                }
                if (page != "main") {
                    TextButton(onClick = { page = "main" }) { Text(stringResource(R.string.back)) }
                }
            }
        }
    }
}

/** An invisible focus target so D-pad focus lands inside sub-pages. */
@Composable
private fun FocusAnchor(requester: FocusRequester) {
    Box(Modifier.size(1.dp).focusRequester(requester).focusable())
}

@Composable
private fun OptionButton(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    OptionButtonBase({ Icon(painterResource(icon), null) }, label, modifier, onClick)
}

@Composable
private fun OptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OptionButtonBase({ Icon(icon, null) }, label, modifier, onClick)
}

@Composable
private fun OptionButtonBase(icon: @Composable () -> Unit, label: String, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        border = focusBorder(source),
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
