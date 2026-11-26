@file:OptIn(UnstableApi::class)

package com.arabtv.iptv.ui

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Channel

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()
    private var playerView: PlayerView? = null
    private val pipState = mutableStateOf(false)

    /** Phone: user asked for fullscreen (forces landscape). */
    private val userFullscreen = mutableStateOf(false)

    /** Phone: user left fullscreen while holding the phone sideways; ignore landscape until upright. */
    private val suppressLandscape = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ArabTvTheme {
                AppRoot()
            }
        }
    }

    @Composable
    private fun AppRoot() {
        val screen by vm.screen.collectAsState()
        val inPip by pipState
        val config = LocalConfiguration.current
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        LaunchedEffect(landscape) { if (!landscape) suppressLandscape.value = false }
        val fullscreen = screen == Screen.PLAYER &&
            (vm.isTv || inPip || userFullscreen.value || (landscape && !suppressLandscape.value && !isTablet()))

        LaunchedEffect(fullscreen) { applySystemBars(fullscreen) }
        LaunchedEffect(screen) {
            if (screen != Screen.PLAYER && userFullscreen.value) {
                userFullscreen.value = false
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        val playState by vm.playback.state.collectAsState()
        val castDevice by vm.cast.deviceName.collectAsState()
        LaunchedEffect(screen, playState.channel?.id, playState.error, castDevice) { updatePipParams() }

        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            vm.messages.collect { m ->
                val text = when (m) {
                    is UiMessage.SentTo -> context.getString(R.string.msg_sent, m.tv)
                    is UiMessage.SendFailed -> when (m.kind) {
                        SendError.REFUSED -> context.getString(R.string.msg_send_refused, m.tv)
                        SendError.NO_ANSWER -> context.getString(R.string.msg_send_no_answer, m.tv)
                        SendError.OTHER -> context.getString(R.string.msg_send_failed, m.tv, m.reason)
                    }
                    is UiMessage.FromPhone -> context.getString(R.string.msg_from_phone, m.from)
                    is UiMessage.Casting -> context.getString(R.string.casting_to, m.device)
                    UiMessage.BadCode -> context.getString(R.string.msg_bad_code)
                    UiMessage.BadPlaylist -> context.getString(R.string.msg_bad_playlist)
                    UiMessage.WrongPin -> context.getString(R.string.msg_wrong_pin)
                    UiMessage.NoApp -> context.getString(R.string.msg_no_app)
                    UiMessage.NoChannelNumber -> context.getString(R.string.msg_no_number)
                    UiMessage.NoNewChannels -> context.getString(R.string.msg_no_new_channels)
                    is UiMessage.ChannelsAdded -> context.getString(R.string.msg_channels_added, m.count)
                }
                snackbar.showSnackbar(text)
            }
        }

        var tvSheetFor by remember { mutableStateOf<Channel?>(null) }
        var tvSheetOpen by remember { mutableStateOf(false) }
        var pinRequest by remember { mutableStateOf<Boolean?>(null) } // true = disable kids mode

        BackHandler(enabled = screen != Screen.HOME) {
            when {
                screen == Screen.PLAYER && userFullscreen.value -> toggleFullscreen()
                screen == Screen.PLAYER -> vm.closePlayer()
                else -> vm.goHome()
            }
        }

        Box(
            Modifier.fillMaxSize().background(Bg)
                .then(if (fullscreen) Modifier else Modifier.safeDrawingPadding()),
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    vm,
                    onOpenTvSheet = { ch -> tvSheetFor = ch; tvSheetOpen = true },
                    onRequestPin = { disable -> pinRequest = disable },
                )
                Screen.PLAYER -> PlayerScreen(
                    vm,
                    fullscreen = fullscreen,
                    inPip = inPip,
                    onToggleFullscreen = { toggleFullscreen() },
                    onPlayerView = { playerView = it },
                    onOpenTvSheet = { ch -> tvSheetFor = ch; tvSheetOpen = true },
                )
                Screen.SETTINGS -> SettingsScreen(vm, onRequestPin = { disable -> pinRequest = disable })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }

        if (tvSheetOpen) TvSheet(vm, tvSheetFor) { tvSheetOpen = false; tvSheetFor = null }

        val newChannels by vm.newChannelsDialog.collectAsState()
        newChannels?.let { NewChannelsDialog(vm, it) }

        val update by vm.updateState.collectAsState()
        val updateDismissed by vm.updatePromptDismissed.collectAsState()
        val manualCheck by vm.manualUpdateCheck.collectAsState()
        UpdateDialog(vm, update, updateDismissed, manualCheck)

        pinRequest?.let { disable ->
            TextFieldsDialog(
                title = getString(if (disable) R.string.kids_disable else R.string.kids_unlock),
                message = getString(R.string.kids_pin_enter),
                fields = listOf(FieldSpec(getString(R.string.pin), keyboard = androidx.compose.ui.text.input.KeyboardType.NumberPassword)),
                onDismiss = { pinRequest = null },
                onConfirm = { v -> if (vm.unlockKids(v[0].trim(), disable)) pinRequest = null },
            )
        }
    }

    private fun isTablet() = resources.configuration.smallestScreenWidthDp >= 600

    private fun toggleFullscreen() {
        val landscapeNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val currentlyFull = userFullscreen.value || (landscapeNow && !suppressLandscape.value && !isTablet())
        if (currentlyFull) {
            userFullscreen.value = false
            if (landscapeNow) suppressLandscape.value = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            suppressLandscape.value = false
            userFullscreen.value = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun applySystemBars(fullscreen: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- Lifecycle ----------------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        vm.startReceiver()
        if (vm.screen.value == Screen.PLAYER && vm.cast.deviceName.value == null && vm.target.value == null &&
            vm.playback.state.value.channel != null
        ) {
            vm.playback.player.play()
        }
    }

    override fun onStop() {
        super.onStop()
        // A picture-in-picture window keeps the activity started, so reaching here means nothing is
        // visible any more (app left, or the PiP window closed): stop using data in the background.
        vm.playback.player.pause()
        if (!isChangingConfigurations) vm.stopReceiver()
    }

    private fun canPip(): Boolean =
        !vm.isTv && vm.screen.value == Screen.PLAYER && vm.cast.deviceName.value == null &&
            vm.playback.state.value.channel != null && vm.playback.state.value.error == null &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(canPip())
        return b.build()
    }

    private fun updatePipParams() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && canPip()) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipState.value = isInPictureInPictureMode
        vm.inPip = isInPictureInPictureMode
    }

    // ---- TV remote keys -----------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Number keys pick a channel by number (not while typing in search or settings).
        val digit = when (event.keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> event.keyCode - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> event.keyCode - KeyEvent.KEYCODE_NUMPAD_0
            else -> -1
        }
        val typing = vm.screen.value == Screen.SETTINGS ||
            (vm.screen.value == Screen.HOME && vm.section.value == Section.SEARCH)
        if (digit >= 0 && !typing) {
            if (event.action == KeyEvent.ACTION_DOWN) vm.typeDigit(digit)
            return true
        }
        if (vm.screen.value != Screen.PLAYER) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        val controls = vm.controllerVisible
        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (down) vm.zap(1); return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down) vm.zap(-1); return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (!controls && vm.isTv && !hasError()) {
                if (down) vm.zap(1); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!controls && vm.isTv && !hasError()) {
                if (down) vm.zap(-1); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controls && vm.isTv && !hasError()) {
                if (down) vm.setShowOptions(true); return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE -> {
                if (down) vm.setShowOptions(true); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (!controls && vm.isTv && !hasError()) {
                    if (down) playerView?.showController(); return true
                }
            KeyEvent.KEYCODE_BACK -> if (controls && vm.isTv) {
                if (!down) playerView?.hideController(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (down) vm.playback.togglePause(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (down) vm.playback.player.play(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down) vm.playback.player.pause(); return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (down) vm.closePlayer(); return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (down) vm.zap(1); return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (down) vm.zap(-1); return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hasError() = vm.playback.state.value.error != null
}
