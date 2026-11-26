package com.arabtv.iptv.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arabtv.iptv.R
import com.arabtv.iptv.data.Category
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.GuideRepository
import com.arabtv.iptv.data.Programme
import com.arabtv.iptv.data.Settings
import com.arabtv.iptv.library.LibraryItem
import com.arabtv.iptv.library.LocalVideo
import com.arabtv.iptv.remote.Action
import kotlinx.coroutines.delay

@Composable
fun sectionLabel(s: Section): String = stringResource(
    when (s) {
        Section.HOME -> R.string.nav_home
        Section.NEWS -> R.string.tab_news
        Section.DOCUMENTARY -> R.string.tab_documentary
        Section.KIDS -> R.string.tab_kids
        Section.LIBRARY -> R.string.nav_library
        Section.MY_LIST -> R.string.nav_my_list
        Section.ALL -> R.string.tab_all
        Section.PLAYLISTS -> R.string.tab_playlists
        Section.SEARCH -> R.string.search
    },
)

/** Refreshes once a minute so "what's on now" moves on by itself. */
@Composable
private fun rememberMinute(): Long {
    val t by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    return t
}

private fun nowTitle(guide: Map<String, List<Programme>>, ch: Channel, now: Long): String? =
    GuideRepository.nowAndNext(guide[ch.id], now).first?.title

@Composable
fun HomeScreen(vm: AppViewModel, onOpenTvSheet: (Channel?) -> Unit, onRequestPin: (disable: Boolean) -> Unit) {
    val all by vm.catalog.collectAsState()
    val section by vm.section.collectAsState()
    val settings by vm.settings.collectAsState()
    val health by vm.health.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val target by vm.target.collectAsState()
    val kidsUnlocked by vm.kidsUnlocked.collectAsState()
    val guide by vm.guide.collectAsState()
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    val now = rememberMinute()

    val locked = settings.kidsMode && !kidsUnlocked
    val sections = if (locked) {
        listOf(Section.KIDS, Section.LIBRARY)
    } else {
        buildList {
            addAll(listOf(Section.HOME, Section.NEWS, Section.DOCUMENTARY, Section.KIDS, Section.LIBRARY, Section.MY_LIST, Section.ALL))
            if (settings.playlists.isNotEmpty()) add(Section.PLAYLISTS)
        }
    }
    val current = if (section in sections || section == Section.SEARCH && !locked) section else sections.first()

    val tile = TileActions(
        vm = vm,
        all = all,
        settings = settings,
        health = health,
        guide = guide,
        now = now,
        onMenu = { menuFor = it },
    )

    Column(Modifier.fillMaxSize()) {
        TopBar(vm, sections, current, locked, onOpenTvSheet, onRequestPin)
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Gold, trackColor = Bg)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                all.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Gold)
                current == Section.HOME -> HomeFeed(vm, tile)
                current == Section.LIBRARY -> LibraryPage(vm)
                current == Section.SEARCH -> SearchPage(vm, tile)
                else -> ChannelGrid(
                    vm = vm,
                    title = sectionLabel(current),
                    list = vm.listFor(current, all, settings, health),
                    tile = tile,
                    empty = stringResource(if (current == Section.MY_LIST) R.string.empty_favorites else R.string.empty_list),
                )
            }
        }

        target?.let { tv -> RemoteBar(tv.label, vm) }
    }

    menuFor?.let { ch ->
        val fav = ch.id in settings.favorites
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(vm.title(ch)) },
            text = {
                Column {
                    MenuItem(stringResource(R.string.play_here)) {
                        menuFor = null
                        vm.selectTarget(null)
                        vm.open(ch, vm.listFor(Section.ALL, all, settings, health))
                    }
                    if (!vm.isTv) MenuItem(stringResource(R.string.send_to_tv)) { menuFor = null; onOpenTvSheet(ch) }
                    MenuItem(stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite)) {
                        vm.toggleFavorite(ch.id); menuFor = null
                    }
                    if (fav) {
                        MenuItem(stringResource(R.string.move_earlier)) { vm.moveFavorite(ch.id, -1); menuFor = null }
                        MenuItem(stringResource(R.string.move_later)) { vm.moveFavorite(ch.id, 1); menuFor = null }
                    }
                    if (ch.playlist == null) {
                        MenuItem(stringResource(R.string.hide_channel)) { vm.hideChannel(ch.id); menuFor = null }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Everything a tile needs, bundled so pages stay short. */
class TileActions(
    val vm: AppViewModel,
    val all: List<Channel>,
    val settings: Settings,
    val health: Map<String, Boolean>,
    val guide: Map<String, List<Programme>>,
    val now: Long,
    val onMenu: (Channel) -> Unit,
) {
    @Composable
    fun Tile(ch: Channel, list: List<Channel>, width: Dp?, onFocused: (() -> Unit)? = null) {
        ChannelTile(
            channel = ch,
            title = vm.title(ch),
            number = vm.numberOf(ch, all),
            now = nowTitle(guide, ch, now),
            favorite = ch.id in settings.favorites,
            health = health[ch.id],
            onClick = { vm.open(ch, list) },
            onLongClick = { onMenu(ch) },
            modifier = if (width != null) Modifier.width(width) else Modifier,
            onFocused = onFocused,
        )
    }
}

@Composable
private fun TopBar(
    vm: AppViewModel,
    sections: List<Section>,
    current: Section,
    locked: Boolean,
    onOpenTvSheet: (Channel?) -> Unit,
    onRequestPin: (disable: Boolean) -> Unit,
) {
    val castDevice by vm.cast.deviceName.collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Gold,
                )
                if (vm.isTv) ReceiverSubtitle(vm)
                else castDevice?.let {
                    Text(stringResource(R.string.casting_to, it), color = Gold, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!locked) {
                IconButton(onClick = { vm.setSection(Section.SEARCH) }) {
                    Icon(Icons.Filled.Search, stringResource(R.string.search))
                }
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
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(sections) { s -> NavPill(sectionLabel(s), s == current) { vm.setSection(s) } }
        }
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(50),
        color = when {
            focused -> Color.White
            selected -> Surface3
            else -> Color.Transparent
        },
        contentColor = when {
            focused -> Color.Black
            selected -> Color.White
            else -> TextSecondary
        },
        modifier = Modifier.focusScale(source, 1.06f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ReceiverSubtitle(vm: AppViewModel) {
    val status by vm.receiver.status.collectAsState()
    val settings by vm.settings.collectAsState()
    if (!settings.receiverEnabled) return
    val text = buildString {
        append(stringResource(R.string.receiver_code_short, com.arabtv.iptv.data.Pairing.format(settings.pairingCode)))
        if (status.lanAddress != null && status.lanPort > 0) append("  ·  ${status.lanAddress}:${status.lanPort}")
        append("  ·  ")
        append(stringResource(if (status.relayConnected) R.string.relay_online else R.string.relay_connecting))
    }
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// ---- Home feed: hero + rows --------------------------------------------------------------------

@Composable
private fun HomeFeed(vm: AppViewModel, tile: TileActions) {
    val all = tile.all
    val s = tile.settings
    val pending by vm.pendingChannels.collectAsState()
    val news = vm.listFor(Section.NEWS, all, s, tile.health)
    val docs = vm.listFor(Section.DOCUMENTARY, all, s, tile.health)
    val kids = vm.listFor(Section.KIDS, all, s, tile.health)
    val others = vm.listFor(Section.ALL, all, s, tile.health).filter { Category.OTHER in it.categories }
    val recents = vm.recents(all, s)
    val favorites = vm.listFor(Section.MY_LIST, all, s, tile.health)
    val featured = remember(all.size, s.favorites, s.recents) {
        (recents.take(3) + favorites.take(3) + news.take(4) + docs.take(2) + kids.take(2)).distinctBy { it.id }
    }
    var heroId by rememberSaveable { mutableStateOf<String?>(null) }
    var userMoved by remember { mutableStateOf(false) }
    // Rotate the hero through featured channels until the user starts browsing.
    LaunchedEffect(featured, userMoved) {
        if (userMoved || featured.isEmpty()) return@LaunchedEffect
        var i = 0
        while (true) {
            heroId = featured[i % featured.size].id
            delay(8_000)
            i++
        }
    }
    val hero = all.firstOrNull { it.id == heroId } ?: featured.firstOrNull()
    val tileWidth = if (vm.isTv) 210.dp else 156.dp
    val focusHero: (Channel) -> (() -> Unit) = { ch -> { userMoved = true; heroId = ch.id } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (hero != null) item(key = "hero") { Hero(vm, hero, tile) }
        if (pending.isNotEmpty()) {
            item(key = "pending") { NewChannelsBanner(pending.size) { vm.showPendingChannels() } }
        }
        fun row(key: String, title: @Composable () -> String, list: List<Channel>) {
            if (list.isEmpty()) return
            item(key = key) {
                ShelfRow(title(), list.take(40), tileWidth, tile, focusHero)
            }
        }
        row("recent", { stringResource(R.string.row_continue) }, recents)
        row("mylist", { stringResource(R.string.nav_my_list) }, favorites)
        row("news", { stringResource(R.string.tab_news) }, news)
        row("docs", { stringResource(R.string.tab_documentary) }, docs)
        row("kids", { stringResource(R.string.tab_kids) }, kids)
        val lib = vm.librarySections.firstOrNull()
        if (lib != null && !s.kidsMode) {
            item(key = "library") {
                LibraryRow(lib.title(vm.isArabicUi()), lib.items, vm) { vm.openLibraryItem(it) }
            }
        }
        row("more", { stringResource(R.string.row_more) }, others)
    }
}

@Composable
private fun ShelfRow(
    title: String,
    list: List<Channel>,
    tileWidth: Dp,
    tile: TileActions,
    onFocus: (Channel) -> (() -> Unit),
) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list, key = { it.id }) { ch -> tile.Tile(ch, list, tileWidth, onFocus(ch)) }
        }
    }
}

@Composable
private fun Hero(vm: AppViewModel, ch: Channel, tile: TileActions) {
    val height = if (vm.isTv) 290.dp else 230.dp
    val (nowP, nextP) = GuideRepository.nowAndNext(tile.guide[ch.id], tile.now)
    val fav = ch.id in tile.settings.favorites
    val category = when {
        Category.NEWS in ch.categories -> stringResource(R.string.tab_news)
        Category.DOCUMENTARY in ch.categories -> stringResource(R.string.tab_documentary)
        Category.KIDS in ch.categories -> stringResource(R.string.tab_kids)
        else -> stringResource(R.string.live_tv)
    }
    Box(Modifier.fillMaxWidth().height(height)) {
        // Backdrop: the logo, huge and blurred (plain fade on Android versions without blur).
        ChannelLogo(
            ch, vm.title(ch),
            Modifier.fillMaxSize().padding(start = 160.dp)
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp).alpha(0.55f) else Modifier.alpha(0.18f)),
            plain = true,
        )
        // Dark behind the text (start side), clear behind the logo; mirrored for Arabic.
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val shade = arrayOf(0f to Bg, 0.45f to Bg.copy(alpha = 0.85f), 1f to Color.Transparent)
        Box(
            Modifier.fillMaxSize().background(
                if (rtl) Brush.horizontalGradient(*shade.map { (1f - it.first) to it.second }.reversed().toTypedArray())
                else Brush.horizontalGradient(*shade),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Bg)))
        ChannelLogo(
            ch, vm.title(ch),
            Modifier.align(Alignment.CenterEnd).padding(end = 40.dp).width(if (vm.isTv) 260.dp else 130.dp)
                .aspectRatio(16f / 9f),
            plain = true,
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 18.dp, end = 24.dp)
                .fillMaxWidth(if (vm.isTv) 0.55f else 0.62f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(LiveRed))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.live) + "  ·  " + category,
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                )
            }
            Text(
                vm.title(ch),
                style = if (vm.isTv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowP != null) {
                Text(
                    stringResource(R.string.now_playing, nowP.title),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nextP?.let {
                    Text(
                        stringResource(R.string.next_up, it.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.open(ch, vm.listFor(Section.ALL, tile.all, tile.settings, tile.health)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.watch), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { vm.toggleFavorite(ch.id) }) {
                    Icon(if (fav) Icons.Filled.Check else Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nav_my_list))
                }
            }
        }
    }
}

@Composable
private fun NewChannelsBanner(count: Int, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        color = Brand.copy(alpha = 0.25f),
        border = focusBorder(source),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = Gold)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.new_channels_banner, count), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.review), color = Gold, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- Grid pages --------------------------------------------------------------------------------

@Composable
private fun ChannelGrid(vm: AppViewModel, title: String, list: List<Channel>, tile: TileActions, empty: String) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (vm.isTv) 210.dp else 150.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.channel_count, list.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Text(empty, color = TextSecondary, modifier = Modifier.padding(vertical = 40.dp))
            }
        }
        items(list, key = { it.id }) { ch -> tile.Tile(ch, list, null) }
    }
}

@Composable
private fun SearchPage(vm: AppViewModel, tile: TileActions) {
    val query by vm.query.collectAsState()
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.setQuery(it) }
    }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (vm.isArabicUi()) "ar" else "en")
    }
    val canVoice = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    LaunchedEffect(Unit) { if (!vm.isTv) runCatching { focus.requestFocus() } }
    val results = vm.listFor(Section.SEARCH, tile.all, tile.settings, tile.health, query)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, null) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (canVoice) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { runCatching { voice.launch(voiceIntent) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Surface3, contentColor = Color.White),
                ) {
                    Icon(painterResource(R.drawable.ic_mic), null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.voice_search))
                }
            }
        }
        if (query.isBlank()) {
            Text(
                stringResource(R.string.search_tip),
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            ChannelGrid(vm, stringResource(R.string.search_results), results, tile, stringResource(R.string.no_results))
        }
    }
}

// ---- Library -----------------------------------------------------------------------------------

@Composable
private fun LibraryPage(vm: AppViewModel) {
    val context = LocalContext.current
    val videos by vm.localVideos.collectAsState()
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.loadLocalVideos()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            vm.loadLocalVideos()
        }
    }
    val arabic = vm.isArabicUi()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "intro") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.nav_library), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.library_intro), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(vm.librarySections, key = { it.id }) { section ->
            LibraryRow(section.title(arabic), section.items, vm) { vm.openLibraryItem(it) }
        }
        item(key = "mine") {
            Column(Modifier.padding(top = 18.dp)) {
                Text(
                    stringResource(R.string.my_videos),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                val list = videos
                when {
                    list == null -> OutlinedButton(
                        onClick = { launcher.launch(perm) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) { Text(stringResource(R.string.my_videos_allow)) }
                    list.isEmpty() -> Text(
                        stringResource(R.string.my_videos_empty),
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(list, key = { it.id }) { v -> VideoTile(v, if (vm.isTv) 230.dp else 170.dp) { vm.playLocal(v, list) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(title: String, items: List<LibraryItem>, vm: AppViewModel, onOpen: (LibraryItem) -> Unit) {
    val arabic = vm.isArabicUi()
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.id }) { item ->
                PosterTile(
                    title = item.title(arabic),
                    subtitle = item.by(arabic),
                    colors = item.colors.map { Color(it) },
                    width = if (vm.isTv) 190.dp else 140.dp,
                ) { onOpen(item) }
            }
        }
    }
}

/** A tall poster card with a colour gradient and the title set large (no artwork is copied). */
@Composable
private fun PosterTile(title: String, subtitle: String, colors: List<Color>, width: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        Modifier.width(width).aspectRatio(2f / 3f).focusScale(source)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(colors.ifEmpty { listOf(Surface3, Surface1) }))
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
    ) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xAA000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 3)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xDDFFFFFF), maxLines = 2)
        }
    }
}

@Composable
private fun VideoTile(v: LocalVideo, width: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(Modifier.width(width).focusScale(source)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Surface3, Surface1)))
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(40.dp), tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (v.durationMs > 0) {
            val m = v.durationMs / 60_000
            Text("${m / 60}:${"%02d".format(m % 60)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// ---- New channels dialog -----------------------------------------------------------------------

@Composable
fun NewChannelsDialog(vm: AppViewModel, list: List<Channel>) {
    var selected by remember(list) { mutableStateOf(list.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = { vm.dismissNewChannels() },
        title = { Text(stringResource(R.string.new_channels_title, list.size)) },
        text = {
            Column {
                Text(stringResource(R.string.new_channels_help), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(list, key = { it.id }) { ch ->
                        val on = ch.id in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { selected = if (on) selected - ch.id else selected + ch.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            ChannelLogo(ch, vm.title(ch), Modifier.width(56.dp).aspectRatio(16f / 9f))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(vm.title(ch), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                ChannelBadges(ch, null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.addNewChannels(selected) }) {
                Text(stringResource(R.string.add_selected, selected.size))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { selected = if (selected.size == list.size) emptySet() else list.map { it.id }.toSet() }) {
                    Text(stringResource(if (selected.size == list.size) R.string.select_none else R.string.select_all))
                }
                TextButton(onClick = { vm.dismissNewChannels() }) { Text(stringResource(R.string.later)) }
            }
        },
    )
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
fun MenuItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}

