@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject


@Composable
internal fun RustUiScreen(store: MusicStore, ui: MusicUiState, player: PlayerState, tree: JSONObject, dark: Boolean, sharedInvite: String, consumeSharedInvite: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    LaunchedEffect(sharedInvite) {
        if (sharedInvite.isNotBlank()) {
            store.navigate(Destination.ROOMS)
            store.ui("uiRoomInviteDraft", args("value" to sharedInvite))
            consumeSharedInvite()
        }
    }
    BackHandler { store.back() }
    val baseColors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val palette = remember(baseColors, dark) {
        val current = JSONObject()
        listOf(
            "primary" to baseColors.primary, "onPrimary" to baseColors.onPrimary,
            "primaryContainer" to baseColors.primaryContainer, "onPrimaryContainer" to baseColors.onPrimaryContainer,
            "secondary" to baseColors.secondary, "onSecondary" to baseColors.onSecondary,
            "secondaryContainer" to baseColors.secondaryContainer, "onSecondaryContainer" to baseColors.onSecondaryContainer,
            "tertiary" to baseColors.tertiary, "onTertiary" to baseColors.onTertiary,
            "tertiaryContainer" to baseColors.tertiaryContainer, "onTertiaryContainer" to baseColors.onTertiaryContainer,
            "background" to baseColors.background, "onBackground" to baseColors.onBackground,
            "surface" to baseColors.surface, "surfaceVariant" to baseColors.surfaceVariant,
            "surfaceContainer" to baseColors.surfaceContainer, "surfaceContainerLow" to baseColors.surfaceContainerLow,
            "surfaceContainerHigh" to baseColors.surfaceContainerHigh, "surfaceContainerHighest" to baseColors.surfaceContainerHighest,
            "onSurface" to baseColors.onSurface, "onSurfaceVariant" to baseColors.onSurfaceVariant,
            "outline" to baseColors.outline, "outlineVariant" to baseColors.outlineVariant,
        ).forEach { (key, color) -> current.put(key, "#%06X".format(0xFFFFFF and color.toArgb())) }
        JSONObject().put(if (dark) "dark" else "light", current)
    }
    LaunchedEffect(systemDark, palette.toString()) { store.updateUiPalette(systemDark, palette) }
    val themeTokens = tree.optJSONObject("theme") ?: JSONObject()
    val rustColors = baseColors.copy(
        primary = rustColor(themeTokens, "primary", baseColors.primary),
        onPrimary = rustColor(themeTokens, "onPrimary", baseColors.onPrimary),
        primaryContainer = rustColor(themeTokens, "primaryContainer", baseColors.primaryContainer),
        onPrimaryContainer = rustColor(themeTokens, "onPrimaryContainer", baseColors.onPrimaryContainer),
        secondary = rustColor(themeTokens, "secondary", baseColors.secondary),
        onSecondary = rustColor(themeTokens, "onSecondary", baseColors.onSecondary),
        secondaryContainer = rustColor(themeTokens, "secondaryContainer", baseColors.secondaryContainer),
        onSecondaryContainer = rustColor(themeTokens, "onSecondaryContainer", baseColors.onSecondaryContainer),
        tertiary = rustColor(themeTokens, "tertiary", baseColors.tertiary),
        onTertiary = rustColor(themeTokens, "onTertiary", baseColors.onTertiary),
        tertiaryContainer = rustColor(themeTokens, "tertiaryContainer", baseColors.tertiaryContainer),
        onTertiaryContainer = rustColor(themeTokens, "onTertiaryContainer", baseColors.onTertiaryContainer),
        background = rustColor(themeTokens, "background", baseColors.background),
        onBackground = rustColor(themeTokens, "onBackground", baseColors.onBackground),
        surface = rustColor(themeTokens, "surface", baseColors.surface),
        surfaceVariant = rustColor(themeTokens, "surfaceVariant", baseColors.surfaceVariant),
        surfaceContainer = rustColor(themeTokens, "surfaceContainer", baseColors.surfaceContainer),
        surfaceContainerLow = rustColor(themeTokens, "surfaceContainerLow", baseColors.surfaceContainerLow),
        surfaceContainerHigh = rustColor(themeTokens, "surfaceContainerHigh", baseColors.surfaceContainerHigh),
        surfaceContainerHighest = rustColor(themeTokens, "surfaceContainerHighest", baseColors.surfaceContainerHighest),
        onSurface = rustColor(themeTokens, "onSurface", baseColors.onSurface),
        onSurfaceVariant = rustColor(themeTokens, "onSurfaceVariant", baseColors.onSurfaceVariant),
        outline = rustColor(themeTokens, "outline", baseColors.outline),
        outlineVariant = rustColor(themeTokens, "outlineVariant", baseColors.outlineVariant),
    )
    MaterialTheme(colorScheme = rustColors, typography = typography, shapes = shapes) {
        val content = tree.optJSONObject("content") ?: JSONObject()
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(when (ui.destination) {
                        Destination.LIBRARY -> ui.libraryKind.title
                        Destination.SEARCH -> "搜索"
                        Destination.ROOMS -> "一起听"
                        Destination.SETTINGS -> "设置"
                    })
                }, actions = {
                    if (ui.destination == Destination.LIBRARY) IconAction(Icons.Rounded.Refresh, "刷新曲库", onClick = { store.ui("uiRefreshLibrary") })
                    if (ui.destination == Destination.ROOMS && ui.room != null) IconAction(Icons.Rounded.Share, "邀请好友", onClick = { store.shareInvite() })
                })
            },
            bottomBar = {
                Column {
                    if (player.song != null) MiniPlayer(store, ui, player, { store.ui("uiToggleFullPlayer") }, { store.ui("uiToggleQueue") })
                    NavigationBar {
                        listOf(Destination.LIBRARY, Destination.SEARCH, Destination.ROOMS, Destination.SETTINGS).forEach { page ->
                            NavigationBarItem(selected = ui.destination == page, onClick = { store.ui("uiNavigate", args("page" to page.name)) },
                                icon = { Icon(when (page) {
                                    Destination.LIBRARY -> Icons.Rounded.LibraryMusic
                                    Destination.SEARCH -> Icons.Rounded.Search
                                    Destination.ROOMS -> Icons.Rounded.Groups
                                    Destination.SETTINGS -> Icons.Rounded.Settings
                                }, null) }, label = { Text(when (page) {
                                    Destination.LIBRARY -> "曲库"
                                    Destination.SEARCH -> "搜索"
                                    Destination.ROOMS -> "房间"
                                    Destination.SETTINGS -> "设置"
                                }) })
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (tree.length() == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (ui.destination == Destination.ROOMS && ui.room != null) RustRoomContent(store, ui, player, content, Modifier.fillMaxSize())
                else RenderRustContent(store, ui, player, content, Modifier.fillMaxSize())
            }
        }
        val presentation = content.optJSONObject("overlay")
        if (presentation != null) RustOverlay(store, ui, player, presentation)
        val dialog = content.optJSONObject("dialog")
        if (dialog != null) RustDialog(store, ui, dialog)
        LaunchedEffect(ui.error, ui.info) {
            val message = ui.error.ifBlank { ui.info }
            if (message.isNotBlank()) android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun RenderRustContent(store: MusicStore, ui: MusicUiState, player: PlayerState, node: JSONObject, modifier: Modifier = Modifier) {
    val children = node.optJSONArray("children") ?: JSONArray()
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed((0 until children.length()).mapNotNull { children.optJSONObject(it) }, key = { index, item -> "${item.optString("type")}:$index:${item.optString("id")}" }) { _, child ->
            when (child.optString("type")) {
                "row" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RenderRustNode(store, ui, player, child.optJSONArray("children")?.optJSONObject(0) ?: JSONObject(), Modifier.weight(1f))
                    RenderRustNode(store, ui, player, child.optJSONArray("children")?.optJSONObject(1) ?: JSONObject())
                }
                "textField" -> RustField(store, ui, child, Modifier.fillMaxWidth())
                "segments", "libraryTabs" -> RustSegments(store, child)
                "listHeader" -> Text(child.optString("text"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                "lyric" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(child.optString("text"), color = if (child.optBoolean("active")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (child.optBoolean("active")) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                    if (child.optString("translation").isNotBlank()) Text(child.optString("translation"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                "list" -> {
                    if (child.optString("error").isNotBlank()) Text(child.optString("error"), color = MaterialTheme.colorScheme.error)
                    if (child.optBoolean("loading")) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val rows = child.optJSONArray("children") ?: JSONArray()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0 until rows.length()).forEach { index -> rows.optJSONObject(index)?.let { RenderRustNode(store, ui, player, it, Modifier.fillMaxWidth()) } }
                    }
                    if (child.optBoolean("empty")) Text("没有找到内容", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                    child.optJSONObject("footer")?.let { RenderRustNode(store, ui, player, it) }
                }
                "section" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(child.optString("title"), style = MaterialTheme.typography.titleMedium)
                    val rows = child.optJSONArray("children") ?: JSONArray()
                    (0 until rows.length()).forEach { index -> rows.optJSONObject(index)?.let { RenderRustNode(store, ui, player, it, Modifier.fillMaxWidth()) } }
                }
                "text" -> Text(child.optString("text"), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                "loading" -> LinearProgressIndicator(Modifier.fillMaxWidth())
                "account" -> {
                    val account = child.optJSONObject("value")
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(account?.optString("avatar").orEmpty(), Modifier.size(48.dp), shape = CircleShape)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(account?.optString("name") ?: "未登录", style = MaterialTheme.typography.titleMedium)
                                Text(account?.optString("uid") ?: "网易云音乐账号", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val action = child.optJSONArray("actions")?.optJSONObject(0)
                            if (action != null) RenderRustNode(store, ui, player, action)
                        }
                    }
                }
                "slider" -> Column {
                    Text(child.optString("label"), style = MaterialTheme.typography.titleSmall)
                    Slider(child.optDouble("value").toFloat(), { store.ui(child.optString("action"), args("value" to it)) })
                }
                "switch" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(child.optString("label"), Modifier.weight(1f))
                    Switch(child.optBoolean("value"), { store.ui(child.optString("action"), args("value" to it)) })
                }
                "roomSummary" -> RustRoomSummary(store, ui, player, child)
                "memberList" -> {
                    val members = child.optJSONArray("children") ?: JSONArray()
                    Text("房间成员 · ${members.length()}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until members.length()).take(8).forEach { index ->
                            val member = members.optJSONObject(index) ?: return@forEach
                            AssistChip(onClick = {}, label = { Text(member.optString("name", "听友"), maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) })
                        }
                    }
                }
                "chatRow" -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(child.optString("name"), style = MaterialTheme.typography.labelLarge)
                        Text(child.optString("text"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                "action" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenderRustNode(store, ui, player, child, Modifier.weight(1f))
                }
                else -> RenderRustNode(store, ui, player, child, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RustRoomContent(store: MusicStore, ui: MusicUiState, player: PlayerState, node: JSONObject, modifier: Modifier = Modifier) {
    val children = node.optJSONArray("children") ?: JSONArray()
    val nodes = (0 until children.length()).mapNotNull { children.optJSONObject(it) }
    val draft = nodes.firstOrNull { it.optString("type") == "textField" && it.optString("change") == "uiRoomDraft" }
    val send = nodes.firstOrNull { it.optString("event") == "uiRoomSend" }
    val content = nodes.filterNot { it === draft || it === send }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(content, key = { index, item -> "${item.optString("type")}:$index:${item.optString("id")}" }) { _, child ->
                if (child.optString("type") == "section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(child.optString("title"), style = MaterialTheme.typography.titleMedium)
                        val rows = child.optJSONArray("children") ?: JSONArray()
                        (0 until rows.length()).forEach { index -> rows.optJSONObject(index)?.let { RenderRustNode(store, ui, player, it, Modifier.fillMaxWidth()) } }
                    }
                } else RenderRustNode(store, ui, player, child, Modifier.fillMaxWidth())
            }
        }
        if (ui.room != null && draft != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RustField(store, ui, draft, Modifier.weight(1f))
                    IconAction(Icons.Rounded.Send, "发送消息", enabled = send?.optJSONObject("data")?.optBoolean("enabled", true) != false) {
                        store.ui("uiRoomSend", args("value" to ui.chatDraft))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderRustNode(store: MusicStore, ui: MusicUiState, player: PlayerState, node: JSONObject, modifier: Modifier = Modifier) {
    when (node.optString("type")) {
        "roomSummary" -> RustRoomSummary(store, ui, player, node)
        "memberList" -> {
            val members = node.optJSONArray("children") ?: JSONArray()
            Text("房间成员 · ${members.length()}", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until members.length()).take(8).forEach { index ->
                    val member = members.optJSONObject(index) ?: return@forEach
                    AssistChip(onClick = {}, label = { Text(member.optString("name", "听友"), maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) })
                }
            }
        }
        "chatRow" -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(node.optString("name"), style = MaterialTheme.typography.labelLarge)
                Text(node.optString("text"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        "action" -> {
            val event = node.optString("event")
            val label = node.optString("label")
            val data = node.optJSONObject("data") ?: JSONObject()
            val enabled = data.optBoolean("enabled", true)
            val click = { rustAction(store, ui, player, event, data) }
            when (event) {
                "uiPlay", "uiPlayAll", "uiSearch", "uiCreateRoom", "uiRoomJoin", "uiLeaveConfirm" -> Button(onClick = click, enabled = enabled, modifier = modifier) { Text(label) }
                "uiOpenCollection", "uiTrack", "uiSearchMore", "uiMoreLiked", "uiMoreAlbums", "uiMoreCollection", "uiRefreshLibrary" -> OutlinedButton(onClick = click, enabled = enabled, modifier = modifier) { Text(label) }
                else -> TextButton(onClick = click, enabled = enabled, modifier = modifier) { Text(label) }
            }
        }
        "segments", "libraryTabs" -> RustSegments(store, node)
        "account" -> {
            val account = node.optJSONObject("value")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(account?.optString("avatar").orEmpty(), Modifier.size(48.dp), shape = CircleShape)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(account?.optString("name") ?: "未登录", style = MaterialTheme.typography.titleMedium)
                        Text(account?.optString("uid") ?: "网易云音乐账号", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    node.optJSONArray("actions")?.optJSONObject(0)?.let { RenderRustNode(store, ui, player, it) }
                }
            }
        }
        "slider" -> Column {
            Text(node.optString("label"), style = MaterialTheme.typography.titleSmall)
            Slider(node.optDouble("value").toFloat(), { store.ui(node.optString("action"), args("value" to it)) })
        }
        "switch" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(node.optString("label"), Modifier.weight(1f))
            Switch(node.optBoolean("value"), { store.ui(node.optString("action"), args("value" to it)) })
        }
        "textField" -> RustField(store, ui, node, modifier)
        "songRow", "queueRow" -> {
            val song = Song(node.optString("id"), node.optString("title"), node.optString("artist"), node.optString("album"), node.optString("cover"), node.optLong("duration"))
            val action = node.optJSONArray("actions")?.optJSONObject(0)
            val menuAction = node.optJSONArray("actions")?.optJSONObject(1)
            val click: () -> Unit = {
                when {
                    node.optString("type") == "queueRow" -> action?.let { rustAction(store, ui, player, it.optString("event"), it.optJSONObject("data") ?: JSONObject()) }
                    node.optString("list") == "roomQueue" -> store.ui("uiRoomQueuePlay", args("at" to node.optInt("at")))
                    else -> store.ui("uiTrack", args("id" to song.id, "at" to node.optInt("at"), "song" to node.optJSONObject("song")))
                }
                Unit
            }
            Surface(color = if (node.optBoolean("selected")) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large, onClick = click) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(song.cover, Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(node.optString("subtitle", song.artistLine), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        node.optString("type") == "queueRow" -> {
                            IconAction(Icons.Rounded.PlayArrow, "播放此队列项", onClick = click)
                            if (!node.optBoolean("room")) IconAction(Icons.Rounded.Close, "从队列移除", onClick = {
                                val remove = node.optJSONArray("actions")?.optJSONObject(1)
                                if (remove != null) rustAction(store, ui, player, remove.optString("event"), remove.optJSONObject("data") ?: JSONObject())
                            })
                        }
                        node.optString("list") == "roomQueue" -> IconAction(Icons.Rounded.PlaylistAdd, "推荐下一首", onClick = click)
                        else -> IconAction(Icons.Rounded.MoreVert, "更多操作", onClick = {
                            menuAction?.let { rustAction(store, ui, player, it.optString("event"), it.optJSONObject("data") ?: JSONObject()) }
                                ?: store.ui("uiTrack", args("id" to song.id, "at" to node.optInt("at"), "song" to node.optJSONObject("song")))
                        })
                    }
                }
            }
        }
        "collectionRow" -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
            onClick = {
                val data = node.optJSONArray("actions")?.optJSONObject(0)?.optJSONObject("data") ?: JSONObject()
                rustAction(store, ui, player, "uiOpenCollection", data)
            }) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(node.optString("cover"), Modifier.size(54.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(node.optString("title"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(node.optString("detail"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
        else -> Unit
    }
}

@Composable
private fun RustField(store: MusicStore, ui: MusicUiState, node: JSONObject, modifier: Modifier = Modifier) {
    val event = node.optString("change")
    val value = node.optString("value")
    OutlinedTextField(value, { store.ui(event, args("value" to it)) }, modifier,
        placeholder = { Text(node.optString("placeholder")) }, singleLine = node.optString("imeAction") == "search",
        keyboardOptions = KeyboardOptions(imeAction = when (node.optString("imeAction")) { "search" -> ImeAction.Search; "send" -> ImeAction.Send; else -> ImeAction.Done }),
        keyboardActions = KeyboardActions(onSearch = { store.ui("uiSearch", args("kind" to ui.searchKind)) }, onSend = { store.ui("uiRoomSend", args("value" to value)) }))
}

@Composable
private fun RustSegments(store: MusicStore, node: JSONObject) {
    val options = node.optJSONArray("options") ?: JSONArray()
    val current = node.opt("value")?.toString().orEmpty()
    ScrollableTabRow(selectedTabIndex = (0 until options.length()).firstOrNull { options.optJSONObject(it)?.opt("value")?.toString() == current } ?: 0,
        edgePadding = 0.dp, divider = {}) {
        (0 until options.length()).forEach { index ->
            val item = options.optJSONObject(index) ?: return@forEach
            Tab(selected = item.opt("value")?.toString() == current, onClick = {
                val data = when (node.optString("action")) {
                    "uiLibraryKind" -> args("name" to item.optString("value"))
                    "uiTheme" -> args("value" to item.optInt("value"))
                        "uiSearchKind" -> args("kind" to item.optString("value"))
                    else -> args("value" to item.opt("value"))
                }
                store.ui(node.optString("action"), data)
            }, text = { Text(item.optString("label"), maxLines = 1) })
        }
    }
}

@Composable
private fun RustRoomSummary(store: MusicStore, ui: MusicUiState, player: PlayerState, node: JSONObject) {
    val room = node.optJSONObject("value") ?: JSONObject()
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(room.optString("kind", "一起听房间"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("在线 ${room.optString("online", "1")} 人 · ${room.optString("roomId")}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RenderRustNode(store, ui, player, node.optJSONArray("actions")?.optJSONObject(0) ?: JSONObject())
                RenderRustNode(store, ui, player, node.optJSONArray("actions")?.optJSONObject(1) ?: JSONObject())
                RenderRustNode(store, ui, player, node.optJSONArray("actions")?.optJSONObject(2) ?: JSONObject())
            }
        }
    }
}

@Composable
private fun RustOverlay(store: MusicStore, ui: MusicUiState, player: PlayerState, node: JSONObject) {
    if (node.optString("type") == "trackDetails") {
        AlertDialog(onDismissRequest = { store.ui("uiDismiss") },
            title = { Text("歌曲信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cover(node.optString("cover"), Modifier.size(96.dp).align(Alignment.CenterHorizontally))
                    Text(node.optString("title"), style = MaterialTheme.typography.titleMedium)
                    Text(node.optString("subtitle"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val rows = node.optJSONArray("rows") ?: JSONArray()
                    (0 until rows.length()).forEach { index ->
                        val row = rows.optJSONObject(index) ?: return@forEach
                        val raw = row.opt("value")
                        if (raw != null && raw != JSONObject.NULL && raw.toString().isNotBlank()) {
                            val value = if (raw is Number) when (row.optString("label")) {
                                "时长" -> clockLabel(raw.toLong())
                                "采样率" -> "${raw.toInt()} Hz"
                                "码率" -> "${raw.toInt() / 1000} kbps"
                                else -> raw.toString()
                            } else raw.toString()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.optString("label"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value)
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { store.ui("uiDismiss") }) { Text("关闭") } })
        return
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { store.ui("uiDismiss") }, sheetState = sheet) {
        when (node.optString("type")) {
            "trackActions" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(node.optString("title"), style = MaterialTheme.typography.titleLarge)
                Text(node.optString("subtitle"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val actions = node.optJSONArray("actions") ?: JSONArray()
                (0 until actions.length()).forEach { i -> actions.optJSONObject(i)?.let { RenderRustNode(store, ui, player, it, Modifier.fillMaxWidth()) } }
            }
            "queue" -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(node.optString("title"), style = MaterialTheme.typography.titleLarge) }
                val rows = node.optJSONArray("children") ?: JSONArray()
                itemsIndexed((0 until rows.length()).mapNotNull { rows.optJSONObject(it) }) { _, row -> RenderRustNode(store, ui, player, row, Modifier.fillMaxWidth()) }
            }
            "fullPlayer" -> RustPlayer(store, ui, player, node)
        }
    }
}

@Composable
private fun RustPlayer(store: MusicStore, ui: MusicUiState, player: PlayerState, node: JSONObject) {
    val song = player.song
    if (song == null) return
    LaunchedEffect(node.optBoolean("lyrics")) { store.showLyrics(node.optBoolean("lyrics")) }
    Column(Modifier.fillMaxWidth().heightIn(min = 520.dp).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Cover(song.cover, Modifier.size(240.dp), shape = MaterialTheme.shapes.extraLarge)
        Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artistLine, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider((node.optDouble("fraction").toFloat()).coerceIn(0f, 1f), { store.audio.seek((it * player.duration).toLong()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Rounded.SkipPrevious, "上一首", player.previous) { store.audio.previous() }
            FilledIconButton(onClick = store.audio::toggle) { Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放") }
            IconAction(Icons.Rounded.SkipNext, "下一首", player.next) { store.audio.next() }
            IconAction(Icons.Rounded.QueueMusic, "播放队列") { store.ui("uiToggleQueue") }
            IconAction(Icons.Rounded.Lyrics, "歌词") { store.ui("uiToggleLyrics") }
        }
        if (node.optBoolean("lyrics")) {
            val activeLine = store.activeLyric.collectAsStateWithLifecycle().value
            val lyricRows = node.optJSONArray("lyricChildren") ?: JSONArray()
            val lyricState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = activeLine.coerceAtLeast(0))
            LaunchedEffect(activeLine) { if (activeLine >= 0) lyricState.animateScrollToItem(activeLine) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), state = lyricState) {
                itemsIndexed((0 until lyricRows.length()).mapNotNull { lyricRows.optJSONObject(it) }) { _, line ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(line.optString("text"), style = if (line.optBoolean("active")) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            color = if (line.optBoolean("active")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (line.optString("translation").isNotBlank()) Text(line.optString("translation"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("静态歌词", Modifier.weight(1f))
                Switch(ui.lyricMode, { store.setLyricMode(it) })
            }
        }
    }
}

@Composable
private fun RustDialog(store: MusicStore, ui: MusicUiState, node: JSONObject) {
    val invite = node.optString("type") == "inviteDialog"
    val qr = node.optString("type") == "qrDialog"
    var value by remember(node.optString("type")) { mutableStateOf(if (invite) node.optJSONObject("field")?.optString("value").orEmpty() else "") }
    val actions = node.optJSONArray("actions") ?: JSONArray()
    AlertDialog(onDismissRequest = { store.ui(if (qr) "uiCancelLogin" else "uiDismiss") }, title = { Text(node.optString("title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (qr) {
                    QrImage(node.optString("url"), Modifier.size(220.dp).align(Alignment.CenterHorizontally))
                    Text(if (node.optBoolean("scanned")) "已扫码，请在网易云音乐确认" else "二维码有效期有限，请在网易云音乐中确认登录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (invite) {
                    OutlinedTextField(value, { value = it; store.ui("uiRoomInviteDraft", args("value" to it)) }, Modifier.fillMaxWidth(), label = { Text("粘贴网易云邀请链接") }, minLines = 2, maxLines = 4)
                    ui.invitePreview?.let { Text("房间 ${it.roomId} · ${it.inviter} · ${it.song}\n${it.status}") }
                } else Text(node.optString("text"))
            }
        },
        confirmButton = { actions.optJSONObject(0)?.let { RustDialogAction(store, ui, it, value) } },
        dismissButton = { actions.optJSONObject(1)?.let { RustDialogAction(store, ui, it, value) } })
}

@Composable
private fun RustDialogAction(store: MusicStore, ui: MusicUiState, action: JSONObject, value: String) {
    val event = action.optString("event")
    val data = action.optJSONObject("data") ?: JSONObject()
    val enabled = data.optBoolean("enabled", true)
    TextButton(onClick = { rustAction(store, ui, PlayerState(), event, data, value) }, enabled = enabled) { Text(action.optString("label")) }
}

private fun rustColor(tokens: JSONObject, name: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(tokens.optString(name))) }.getOrDefault(fallback)

private fun rustAction(store: MusicStore, ui: MusicUiState, player: PlayerState, event: String, data: JSONObject, value: String? = null) {
    when (event) {
        "uiPlay" -> store.ui(event, data)
        "uiTrack" -> store.ui(event, args("id" to data.optString("id"), "at" to data.optInt("at"), "song" to data.optJSONObject("song")))
        "uiPlayAll" -> store.ui(event, data)
        "uiOpenCollection" -> store.ui(event, data)
        "uiRoomRefresh" -> store.ui("uiRestoreRoom")
        "uiShareRoom" -> store.ui(event, data)
        "uiRoomInvite" -> store.ui("uiRoomInvite")
        "uiDismiss" -> store.ui(event)
        "uiRoomPreview" -> store.ui(event, args("value" to value.orEmpty()))
        "uiRoomJoin" -> store.ui(event)
        "uiLeaveConfirm" -> store.ui(event)
        "uiRoomSend" -> store.ui(event, args("value" to value.orEmpty()))
        "uiLogin", "uiCancelLogin", "uiLogout" -> store.ui(event)
        "uiFavorite", "uiChooseRoom", "uiEnqueue", "uiPlayNext", "uiRoomAdd", "uiShareTrack" -> store.ui(event, data)
        "uiQueuePlay" -> store.ui(event, data)
        "uiRoomQueuePlay" -> store.ui(event, data)
        "uiQueueRemove" -> store.ui(event, data)

        "uiSearch" -> store.ui(event, args("kind" to data.optString("kind", ui.searchKind)))
        "uiClearSearch" -> store.ui(event, data)
        "uiSearchMore" -> store.ui(event, data)
        "uiRefreshLibrary", "uiMoreLiked", "uiMoreAlbums", "uiMoreCollection" -> store.ui(event, data)
        "uiPlaylistFilter", "uiPlaylistQuery", "uiAlbumQuery", "uiTheme", "uiLyricPrefsAlign", "uiLyricPrefsShift", "uiLyricPrefsAwake", "uiLyricsMode", "uiLyricPrefs" -> store.ui(event, data)
        "uiLibraryKind" -> store.ui(event, args("name" to data.optString("value")))
        "uiSearchKind" -> store.ui(event, args("kind" to data.optString("value")))
        "uiCreateRoom" -> store.ui(event)
        "uiRestoreRoom" -> store.ui(event)
        "uiRoomPublic" -> store.ui(event, args("value" to data.optBoolean("value")))
        "uiRoomDraft" -> store.ui(event, args("value" to value.orEmpty()))
        "uiNavigate" -> store.ui(event, data)
        "uiToggleFullPlayer", "uiToggleQueue", "uiToggleLyrics", "uiTrackDetails", "uiLeaveRequest", "uiDismiss" -> store.ui(event)
        "playerPlay" -> store.audio.toggle()
        "playerPause" -> store.audio.toggle()
        "playerPrevious" -> store.audio.previous()
        "playerNext" -> store.audio.next()
        else -> store.ui(event, data)
    }
}


