@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun QiyidiScreen(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    tree: JSONObject,
    dark: Boolean,
    sharedInvite: String,
    consumeSharedInvite: () -> Unit,
) {
    val presentation = tree.optJSONObject("presentation") ?: JSONObject()
    val snackbar = remember { SnackbarHostState() }
    val position by store.playerPosition.collectAsStateWithLifecycle()
    val overlay = tree.optJSONObject("content")?.optJSONObject("overlay")
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    val pickedSong by store.playlistPicker.collectAsStateWithLifecycle()
    LaunchedEffect(pickedSong) {
        if (pickedSong != null) {
            playlistSong = pickedSong
            store.clearPlaylistPicker()
        }
    }
    val askSong by store.recommendAsk.collectAsStateWithLifecycle()
    askSong?.let { song ->
        AlertDialog(
            onDismissRequest = { store.clearRecommendAsk() },
            title = { Text("推荐到房间？") },
            text = { Text("「${song.title}」会加入一起听房间的即将播放列表。") },
            confirmButton = {
                TextButton(onClick = { store.confirmRecommend(song) }) { Text("推荐") }
            },
            dismissButton = {
                TextButton(onClick = { store.clearRecommendAsk() }) { Text("取消") }
            },
        )
    }
    var messagesOpen by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sharedInvite) {
        if (sharedInvite.isNotBlank()) {
            store.navigate(Destination.ROOMS)
            store.ui("uiRoomInviteDraft", args("value" to sharedInvite))
            consumeSharedInvite()
        }
    }
    LaunchedEffect(ui.error, ui.info) {
        val message = ui.error.ifBlank { ui.info }
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Short)
            store.clearMessage(message)
        }
    }

    val hasSheet = presentation.optBoolean("invite") || presentation.optBoolean("queue") ||
        presentation.optBoolean("leaveConfirmation") || presentation.optBoolean("fullPlayer")
    val canGoBack = messagesOpen || hasSheet || overlay != null || ui.qrUrl.isNotBlank() || ui.collection != null || ui.destination != Destination.LIBRARY
    BackHandler(enabled = canGoBack) { if (messagesOpen) messagesOpen = false else store.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            TabletShell(
                store, ui, player, tree, presentation,
                expanded = true, snackbar = snackbar,
                onOpenMessages = { messagesOpen = true },
            )
        } else {
            PhoneShell(
                store, ui, player, tree, presentation,
                snackbar = snackbar,
                onOpenMessages = { messagesOpen = true },
            )
        }
        AnimatedVisibility(
            visible = messagesOpen,
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it },
        ) {
            QPrivateMessagesScreen(
                store, ui,
                onClose = { messagesOpen = false },
                onJoinRoom = { link ->
                    messagesOpen = false
                    scope.launch {
                        runCatching { store.joinRoomDirect(link) }
                            .onSuccess {
                                store.navigate(Destination.ROOMS)
                                store.refreshRoom()
                            }
                            .onFailure { joinError = it.message.orEmpty().ifBlank { "房间已过期或暂不可加入" } }
                    }
                },
                onOpenAlbum = { collection ->
                    messagesOpen = false
                    store.openCollection(collection)
                },
            )
        }

        val playerOpen = presentation.optBoolean("fullPlayer") && player.song != null
        AnimatedVisibility(
            visible = playerOpen,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(animationSpec = tween(340, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
        ) {
            QFullPlayer(store, ui, player, wide = expanded, onClose = { store.ui("uiDismiss") })
        }
        QQueueSheet(store, ui, player, visible = presentation.optBoolean("queue"), onDismiss = { store.ui("uiDismiss") })
        if (presentation.optBoolean("invite")) {
            QInviteSheet(store, ui, presentation, onDismiss = { store.ui("uiDismiss") })
        }
        if (presentation.optBoolean("leaveConfirmation")) {
            QLeaveDialog(store)
        }
        val trackOverlay = overlay?.takeIf {
            it.optString("type") == "trackActions" || it.optString("type") == "trackDetails"
        }
        QTrackSheet(
            store, ui, trackOverlay, visible = trackOverlay != null,
            roomSong = player.song,
            onDismiss = { store.ui("uiDismiss") },
            onAddToPlaylist = { song ->
                store.ui("uiDismiss")
                playlistSong = song
            },
        )
        QAddToPlaylistSheet(
            store, playlistSong,
            visible = playlistSong != null,
            onDismiss = { playlistSong = null },
        )
        if (ui.qrUrl.isNotBlank()) {
            QQrDialog(store, ui)
        }
        joinError?.let { message ->
            AlertDialog(
                onDismissRequest = { joinError = null },
                title = { Text("无法加入房间") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { joinError = null }) { Text("知道了") } },
            )
        }
    }
}

@Composable
private fun PhoneShell(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    tree: JSONObject,
    presentation: JSONObject,
    snackbar: SnackbarHostState,
    onOpenMessages: () -> Unit,
) {
    var dockVisible by remember { mutableStateOf(true) }
    val dockScroll = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var lastY = down.position.y
                var lastX = down.position.x
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dy = change.position.y - lastY
                    val dx = change.position.x - lastX
                    lastY = change.position.y
                    lastX = change.position.x
                    if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.15f && kotlin.math.abs(dy) > 2f) {
                        if (dy < 0f) dockVisible = false else dockVisible = true
                    }
                }
            }
        }
    }
    LaunchedEffect(ui.destination, ui.collection?.id) { dockVisible = true }
    LaunchedEffect(ui.room?.optString("roomId").orEmpty()) {
        val roomId = ui.room?.optString("roomId").orEmpty()
        if (ui.account == null) return@LaunchedEffect
        if (roomId.isNotBlank()) store.connectNim(roomId) else store.disconnectNim()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { QPhoneTopBar(store, ui, onOpenMessages) },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding(),
            ) {
                Column {
                    AnimatedVisibility(
                        visible = player.song != null,
                        enter = expandVertically(animationSpec = tween(260), expandFrom = Alignment.Bottom) + fadeIn(tween(220)),
                        exit = shrinkVertically(animationSpec = tween(210), shrinkTowards = Alignment.Bottom) + fadeOut(tween(150)),
                    ) {
                        if (player.song != null) QMiniPlayer(store, ui, player)
                    }
                    AnimatedVisibility(
                        visible = dockVisible,
                        enter = expandVertically(animationSpec = tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom) + fadeIn(tween(150)),
                        exit = shrinkVertically(animationSpec = tween(240, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120)),
                    ) {
                        QPhoneNavigation(store, ui)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).then(dockScroll)) {
            QPageHost(store, ui, player, tree, presentation, wide = false)
        }
    }
}

@Composable
private fun TabletShell(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    tree: JSONObject,
    presentation: JSONObject,
    expanded: Boolean,
    snackbar: SnackbarHostState,
    onOpenMessages: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = maxWidth >= 1020.dp
            Row(Modifier.fillMaxSize()) {
                QNavigationRail(store, ui)
                Column(Modifier.fillMaxSize()) {
                    QTabletTopBar(store, ui, onOpenMessages)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        QPageHost(store, ui, player, tree, presentation, wide = roomy)
                    }
                    AnimatedVisibility(
                        visible = !roomy && player.song != null,
                        enter = expandVertically(animationSpec = tween(260), expandFrom = Alignment.Bottom) + fadeIn(tween(220)),
                        exit = shrinkVertically(animationSpec = tween(210), shrinkTowards = Alignment.Bottom) + fadeOut(tween(150)),
                    ) {
                        if (player.song != null) QMiniPlayer(store, ui, player)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
        }
    }
}

private data class QPageSnapshot(
    val key: String,
    val destination: Destination,
    val collection: Collection?,
)

@Composable
private fun QPageHost(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    tree: JSONObject,
    presentation: JSONObject,
    wide: Boolean,
) {
    val key = when {
        ui.collection != null -> "collection:${ui.collection.id}"
        ui.destination == Destination.LIBRARY -> "LIBRARY:${ui.libraryKind.name}"
        ui.destination == Destination.SEARCH -> "SEARCH:${ui.searchedQuery}:${ui.searchKind}"
        ui.destination == Destination.ROOMS -> "ROOMS:${ui.room?.optString("roomId").orEmpty()}"
        else -> ui.destination.name
    }
    val page = remember(key) { QPageSnapshot(key, ui.destination, ui.collection) }
    AnimatedContent(
        targetState = page,
        contentKey = { it.key },
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
        },
        label = "page",
    ) { snapshot ->
        Row(Modifier.fillMaxSize()) {
            Box(
                if (wide && player.song != null && snapshot.destination != Destination.ROOMS)
                    Modifier.weight(1f).fillMaxHeight()
                else Modifier.fillMaxSize(),
            ) {
                when {
                    snapshot.collection != null -> QCollectionPage(store, ui, player, wide, snapshot.collection)
                    snapshot.destination == Destination.LIBRARY -> QLibraryPage(store, ui, player, wide)
                    snapshot.destination == Destination.SEARCH -> QSearchPage(store, ui, player, wide)
                    snapshot.destination == Destination.ROOMS -> QRoomsPage(store, ui, player, wide)
                    else -> QSettingsPage(store, ui)
                }
            }
            if (wide && player.song != null && snapshot.destination != Destination.ROOMS) {
                QSideNowPlaying(
                    store, ui, player,
                    Modifier.width(330.dp).fillMaxHeight().padding(end = 18.dp, bottom = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun QPhoneTopBar(store: MusicStore, ui: MusicUiState, onOpenMessages: () -> Unit) {
    val account = ui.account
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp).clickable { store.navigate(Destination.LIBRARY) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        null,
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("一起迪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "choose life？！",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpenMessages) { Icon(Icons.Rounded.ChatBubbleOutline, "私信") }
            IconButton(onClick = { store.navigate(Destination.SEARCH) }) {
                Icon(Icons.Rounded.Search, "搜索")
            }
            QAvatar(account?.avatar.orEmpty(), 36.dp, Modifier.clickable { store.navigate(Destination.SETTINGS) })
        }
    }
}

@Composable
private fun QPhoneNavigation(store: MusicStore, ui: MusicUiState) {
    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        listOf(
            Triple(Destination.LIBRARY, Icons.Rounded.Home, "首页"),
            Triple(Destination.SEARCH, Icons.Rounded.Search, "搜索"),
            Triple(Destination.ROOMS, Icons.Rounded.Groups, "一起听"),
            Triple(Destination.SETTINGS, Icons.Rounded.Settings, "设置"),
        ).forEach { (destination, icon, label) ->
            NavigationBarItem(
                selected = ui.destination == destination,
                onClick = { store.navigate(destination) },
                icon = { Icon(icon, label) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun QNavigationRail(store: MusicStore, ui: MusicUiState) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight().width(104.dp).statusBarsPadding(),
        header = {
            Column(Modifier.padding(top = 18.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp).clickable { store.navigate(Destination.LIBRARY) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            null,
                            Modifier.size(27.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    "一起迪",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        Spacer(Modifier.weight(0.4f))
        listOf(
            Triple(Destination.LIBRARY, Icons.Rounded.Home, "首页"),
            Triple(Destination.SEARCH, Icons.Rounded.Search, "搜索"),
            Triple(Destination.ROOMS, Icons.Rounded.Groups, "一起听"),
            Triple(Destination.SETTINGS, Icons.Rounded.Settings, "设置"),
        ).forEach { (destination, icon, label) ->
            NavigationRailItem(
                selected = ui.destination == destination,
                onClick = { store.navigate(destination) },
                icon = { Icon(icon, label) },
                label = { Text(label, maxLines = 1) },
            )
        }
        Spacer(Modifier.weight(1f))
        QAvatar(ui.account?.avatar.orEmpty(), 40.dp, Modifier.clickable { store.navigate(Destination.SETTINGS) })
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun QTabletTopBar(store: MusicStore, ui: MusicUiState, onOpenMessages: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 26.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                when {
                    ui.collection != null -> ui.collection.title
                    ui.destination == Destination.LIBRARY -> "首页"
                    ui.destination == Destination.SEARCH -> "搜索"
                    ui.destination == Destination.ROOMS -> "一起听"
                    else -> "设置"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "choose life？！",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.destination == Destination.LIBRARY) {
            IconButton(onClick = { store.refreshLibrary() }) { Icon(Icons.Rounded.Refresh, "刷新音乐库") }
        }
        if (ui.destination == Destination.ROOMS && ui.room != null) {
            IconButton(onClick = { store.shareInvite() }) { Icon(Icons.Rounded.Share, "邀请好友") }
        }
        IconButton(onClick = onOpenMessages) { Icon(Icons.Rounded.ChatBubbleOutline, "私信") }
        IconButton(onClick = { store.navigate(Destination.SEARCH) }) { Icon(Icons.Rounded.Search, "搜索") }
        QAvatar(ui.account?.avatar.orEmpty(), 40.dp, Modifier.clickable { store.navigate(Destination.SETTINGS) })
    }
}

@Composable
internal fun QMiniPlayer(store: MusicStore, ui: MusicUiState, player: PlayerState) {
    val song = player.song ?: return
    val position by store.playerPosition.collectAsStateWithLifecycle()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().clickable { store.ui("uiToggleFullPlayer") },
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(song.cover, Modifier.size(48.dp), shape = RoundedCornerShape(15.dp))
                Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (player.roomMode) {
                            QTag("一起听", Modifier.padding(start = 6.dp))
                        }
                    }
                    Text(song.artistLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = store.audio::toggle, modifier = Modifier.size(42.dp)) {
                    if (player.buffering) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "暂停" else "播放")
                }
                IconButton(onClick = { store.ui("uiToggleQueue") }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.QueueMusic, "播放队列")
                }
            }
            LinearProgressIndicator(
                progress = { if (player.duration <= 0) 0f else (position.toFloat() / player.duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
internal fun QSideNowPlaying(store: MusicStore, ui: MusicUiState, player: PlayerState, modifier: Modifier = Modifier) {
    val song = player.song ?: return
    val position by store.playerPosition.collectAsStateWithLifecycle()
    Surface(modifier, shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("正在播放", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (player.roomMode) "房间同步" else "本机播放",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { store.ui("uiToggleQueue") }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.QueueMusic, "播放队列", Modifier.size(20.dp))
                }
                IconButton(onClick = { store.ui("uiToggleFullPlayer") }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Headphones, "展开播放器", Modifier.size(20.dp))
                }
            }
            Cover(
                song.cover,
                Modifier.fillMaxWidth(0.68f).aspectRatio(1f).align(Alignment.CenterHorizontally).clickable { store.ui("uiToggleFullPlayer") },
                shape = RoundedCornerShape(22.dp),
                iconSize = 38.dp,
            )
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            QNowPlayingProgress(position, player.duration)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clockLabel(position), style = MaterialTheme.typography.labelSmall)
                Text(clockLabel(player.duration), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = store.audio::previous, enabled = player.previous && !player.roomMode, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(24.dp))
                }
                FilledIconButton(onClick = store.audio::toggle, modifier = Modifier.size(52.dp)) {
                    if (player.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "暂停" else "播放", Modifier.size(26.dp))
                }
                IconButton(onClick = store.audio::next, enabled = player.next && !player.roomMode, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun QLeaveDialog(store: MusicStore) {
    AlertDialog(
        onDismissRequest = { store.ui("uiDismiss") },
        title = { Text("离开一起听房间？") },
        text = { Text("离开后会恢复个人播放队列，房间里的同步也会停止。") },
        confirmButton = { Button(onClick = { store.ui("uiLeaveConfirm") }) { Text("离开房间") } },
        dismissButton = { TextButton(onClick = { store.ui("uiDismiss") }) { Text("继续听") } },
    )
}

@Composable
private fun QQrDialog(store: MusicStore, ui: MusicUiState) {
    AlertDialog(
        onDismissRequest = { store.cancelQr() },
        title = { Text("使用网易云音乐扫码") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrImage(ui.qrUrl, Modifier.size(230.dp))
                Text(
                    if (ui.qrScanned) "已扫码，请在网易云音乐中确认登录" else "打开网易云音乐 · 扫一扫 · 确认登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { store.cancelQr() }) { Text("取消") } },
    )
}

@Composable
private fun QQueueSheet(store: MusicStore, ui: MusicUiState, player: PlayerState, visible: Boolean, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            delay(48)
            runCatching { sheet.show() }
        } else if (mounted) {
            runCatching { sheet.hide() }
            mounted = false
        }
    }
    if (!mounted) return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ui.room != null) "即将播放" else "播放队列", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (ui.room != null) "${ui.roomQueue.size} 首 · 点歌或顶歌" else "接下来播放 · ${player.queue.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ui.room != null) {
                    TextButton(onClick = { onDismiss(); store.navigate(Destination.SEARCH) }) { Text("推荐歌曲") }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.KeyboardArrowDown, "收起") }
            }
            if (ui.room != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ui.roomQueue.isEmpty()) {
                        QRoomQueueEmpty({ onDismiss(); store.navigate(Destination.SEARCH) })
                    }
                    ui.roomQueue.forEachIndexed { index, track ->
                        QRoomQueueRow(store, ui, player, track, index)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    player.queue.forEachIndexed { index, song ->
                        QTrackRow(
                            song, index, player.song?.id == song.id, song.id in ui.liked,
                            onClick = { store.selectPlayback(index) },
                            onMore = { store.audio.remove(index) },
                            onLike = null, showIndex = true,
                            moreIcon = Icons.Rounded.DeleteOutline,
                            moreLabel = "从队列移除",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QInviteSheet(store: MusicStore, ui: MusicUiState, presentation: JSONObject, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var link by remember { mutableStateOf(presentation.optString("inviteText")) }
    val preview = ui.invitePreview
    LaunchedEffect(ui.room?.optString("roomId")) {
        if (ui.room != null) onDismiss()
    }
    LaunchedEffect(presentation.optString("inviteText")) {
        if (link.isBlank()) link = presentation.optString("inviteText")
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("加入好友房", style = MaterialTheme.typography.headlineSmall)
                    Text("粘贴网易云音乐「一起听」邀请链接", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QTag("官方协议", icon = Icons.Rounded.Link)
            }
            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    store.ui("uiRoomInviteDraft", args("value" to it))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                minLines = 2,
                maxLines = 4,
                label = { Text("邀请链接") },
                placeholder = { Text("https://st.music.163.com/listen-together/…") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (link.isNotBlank()) IconButton(onClick = {
                        link = ""
                        store.ui("uiRoomInviteDraft", args("value" to ""))
                    }) { Icon(Icons.Rounded.Close, "清空") }
                },
            )
            if (preview != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QTag(if (preview.joinable) "可以加入" else "暂不可加入", icon = Icons.Rounded.Groups)
                            Spacer(Modifier.weight(1f))
                            Text(preview.roomId.takeLast(8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(preview.song.ifBlank { "房间里的歌" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("邀请人 · ${preview.inviter.ifBlank { "网易云用户" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!preview.joinable) Text(preview.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        store.ui("uiRoomInviteDraft", args("value" to link))
                        store.ui("uiRoomPreview")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = link.isNotBlank() && !ui.roomBusy,
                ) { Text("预览房间") }
                Button(
                    onClick = { store.joinInvite() },
                    modifier = Modifier.weight(1f),
                    enabled = preview?.joinable == true && !ui.roomBusy,
                ) {
                    if (ui.roomBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("加入房间")
                }
            }
            Text(
                "支持网易云官方多人一起听分享链接；不会打开外部网页，也不会读取无关信息。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QTrackSheet(
    store: MusicStore,
    ui: MusicUiState,
    overlay: JSONObject?,
    visible: Boolean,
    roomSong: Song?,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cached by remember { mutableStateOf<JSONObject?>(null) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(overlay) { if (overlay != null) cached = overlay }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            delay(48)
            runCatching { sheet.show() }
        } else if (mounted) {
            runCatching { sheet.hide() }
            mounted = false
        }
    }
    val data = overlay ?: cached ?: return
    if (!mounted) return
    val details = data.optString("type") == "trackDetails"
    val actions = data.optJSONArray("actions") ?: JSONArray()
    val selectedSong = (0 until actions.length()).mapNotNull { index ->
            val action = actions.optJSONObject(index) ?: return@mapNotNull null
            if (action.optString("event") != "uiFavorite") return@mapNotNull null
            action.optJSONObject("data")?.optJSONObject("song")?.let(Song::from)
        }.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cover(data.optString("cover"), Modifier.size(62.dp), shape = RoundedCornerShape(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(data.optString("title", "歌曲"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(data.optString("subtitle"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            AnimatedContent(
                targetState = details to data.toString().hashCode(),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 10 }) togetherWith fadeOut(tween(120))
                },
                label = "track-sheet-body",
            ) { state ->
            if (state.first) {
                val rows = data.optJSONArray("rows") ?: JSONArray()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until rows.length()).forEach { index ->
                        val row = rows.optJSONObject(index) ?: return@forEach
                        val raw = row.opt("value")
                        if (raw != null && raw != JSONObject.NULL && raw.toString().isNotBlank()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.optString("label"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                    when (row.optString("label")) {
                                        "时长" -> clockLabel((raw as? Number)?.toLong() ?: 0L)
                                        "采样率" -> "${(raw as? Number)?.toInt() ?: 0} Hz"
                                        "码率" -> "${((raw as? Number)?.toInt() ?: 0) / 1000} kbps"
                                        else -> raw.toString()
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (index != rows.length() - 1) HorizontalDivider()
                        }
                    }
                }
            } else {
                val roomId = ui.room?.optString("roomId").orEmpty()
                val playback = ui.room?.optJSONObject("playback")
                val roomBizId = playback?.optString("bizId").orEmpty()
                val roomSongId = playback?.optString("songId").orEmpty()
                val currentRoomTrack = ui.roomQueue.firstOrNull { it.song.id == roomSongId }
                val fallbackId = roomSong?.id ?: roomSongId
                val fallbackSong = fallbackId.takeIf { it.isNotBlank() }?.let {
                    Song(
                        id = it,
                        title = data.optString("title"),
                        artist = data.optString("subtitle"),
                        album = "",
                        cover = data.optString("cover"),
                        duration = 0L,
                    )
                }
                val actionSong = selectedSong ?: roomSong ?: currentRoomTrack?.song
                    ?: fallbackSong ?: ui.roomQueue.firstOrNull()?.song
                if (actionSong != null) {
                    FilledTonalButton(
                        onClick = { onAddToPlaylist(actionSong) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("添加到歌单")
                    }
                }
                if (roomId.isNotBlank() && actionSong != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                store.roomOperation(actionSong, roomBizId, "next")
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("切歌")
                        }
                        OutlinedButton(
                            onClick = {
                                store.roomOperation(actionSong, roomBizId, "like")
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.ThumbUp, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("点赞")
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until actions.length()).forEach { index ->
                        val action = actions.optJSONObject(index) ?: return@forEach
                        val event = action.optString("event")
                        if (roomId.isNotBlank() && event in setOf("uiPlay", "playerSelect", "uiPlayNow")) return@forEach
                        val data = action.optJSONObject("data") ?: JSONObject()
                        val favoriteId = data.optJSONObject("song")?.optString("id").orEmpty()
                        val label = if (event == "uiFavorite" && favoriteId.isNotBlank() && favoriteId in ui.liked) {
                            "已收藏"
                        } else {
                            action.optString("label")
                        }
                        if (event == "uiTrackDetails") {
                            OutlinedButton(
                                onClick = { store.ui("uiTrackDetails") },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    store.ui(event, data)
                                    if (event != "uiToggleQueue") onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun QAddToPlaylistSheet(store: MusicStore, songInput: Song?, visible: Boolean, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var cachedSong by remember { mutableStateOf<Song?>(null) }
    var mounted by remember { mutableStateOf(visible) }
    var playlists by remember(songInput?.id) { mutableStateOf<List<Collection>>(emptyList()) }
    var loading by remember(songInput?.id) { mutableStateOf(true) }
    var error by remember(songInput?.id) { mutableStateOf("") }
    var adding by remember(songInput?.id) { mutableStateOf("") }
    var added by remember(songInput?.id) { mutableStateOf<String?>(null) }
    val uid = remember { store.accountUid() }
    LaunchedEffect(songInput) { if (songInput != null) cachedSong = songInput }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            delay(48)
            runCatching { sheet.show() }
        } else if (mounted) {
            runCatching { sheet.hide() }
            mounted = false
        }
    }
    val song = songInput ?: cachedSong ?: return
    if (!mounted) return
    LaunchedEffect(song.id) {
        loading = true
        error = ""
        runCatching { store.playlistChoices() }
            .onSuccess { playlists = it }
            .onFailure { error = it.message.orEmpty().ifBlank { "读取歌单失败" } }
        loading = false
    }
    LaunchedEffect(added) {
        if (added != null) {
            delay(700)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("添加到歌单", style = MaterialTheme.typography.headlineSmall)
                    Text("选择一个网易云歌单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (added != null) QTag("已添加")
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(song.cover, Modifier.size(48.dp), shape = RoundedCornerShape(14.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                error.isNotBlank() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        scope.launch {
                            loading = true
                            runCatching { store.playlistChoices() }
                                .onSuccess { playlists = it; error = "" }
                                .onFailure { error = it.message.orEmpty() }
                            loading = false
                        }
                    }) { Text("重试") }
                }
                playlists.isEmpty() -> Text("还没有可用的歌单", Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val created = playlists.filter { it.owner == uid }
                    val collected = playlists.filterNot { it.owner == uid }
                    val choose: @Composable (Collection) -> Unit = { playlist ->
                        Surface(
                            onClick = {
                                if (adding.isBlank()) {
                                    scope.launch {
                                        adding = playlist.id
                                        error = ""
                                        runCatching { store.addToPlaylist(playlist.id, song.id) }
                                            .onSuccess { added = playlist.title }
                                            .onFailure { error = it.message.orEmpty().ifBlank { "添加失败，请重试" } }
                                        adding = ""
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            enabled = adding.isBlank(),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(playlist.cover, Modifier.size(46.dp), shape = RoundedCornerShape(13.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${playlist.count} 首 · ${playlist.detail.ifBlank { "网易云音乐" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (adding == playlist.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else if (added != null) QTag("已添加")
                                else Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (created.isNotEmpty()) {
                            item { QSectionTitle("我创建的", "${created.size} 个歌单") }
                            items(created, key = { it.id }) { choose(it) }
                        }
                        if (collected.isNotEmpty()) {
                            item { QSectionTitle("我收藏的", "${collected.size} 个歌单") }
                            items(collected, key = { it.id }) { choose(it) }
                        }
                    }
                }
            }
        }
    }
}
