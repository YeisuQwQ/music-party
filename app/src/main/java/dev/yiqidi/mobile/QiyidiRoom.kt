@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun QRoomsPage(store: MusicStore, ui: MusicUiState, player: PlayerState, wide: Boolean) {
    if (ui.room != null) {
        QActiveRoom(store, ui, player, wide)
    } else {
        QNoRoomPage(store, ui, wide)
    }
}

@Composable
private fun QNoRoomPage(store: MusicStore, ui: MusicUiState, wide: Boolean) {
    val account = ui.account
    val selected = ui.roomChoiceSong
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (wide) 30.dp else QPagePadding,
            end = if (wide) 30.dp else QPagePadding,
            top = 10.dp,
            bottom = if (wide) 30.dp else 130.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QGradientPanel(
                Modifier.fillMaxWidth(),
                colors = listOf(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(if (wide) 30.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QTag("一起听", icon = Icons.Rounded.Headphones)
                        Spacer(Modifier.weight(1f))
                        QTag("官方多人协议", icon = Icons.Rounded.Wifi)
                    }
                    Text(
                        "大家围坐听歌，\n就等你啦。",
                        style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "加入好友房，或选一首歌开一场属于你们的听歌会。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { store.ui("uiRoomInvite") }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("加入好友房", maxLines = 1)
                        }
                        FilledTonalButton(onClick = { store.navigate(Destination.SEARCH) }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("选歌", maxLines = 1)
                        }
                    }
                }
            }
        }
        if (account == null) {
            item { QLoginPrompt { store.navigate(Destination.SETTINGS) } }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QActionTile(Icons.Rounded.Link, "加入好友房", "粘贴邀请链接", onClick = { store.ui("uiRoomInvite") }, modifier = Modifier.weight(1f))
                QActionTile(Icons.Rounded.Refresh, "恢复房间", "回到上次一起听", onClick = { store.ui("uiRestoreRoom") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            QSectionTitle("创建一场一起听", "选好歌，再把链接分享给朋友")
        }
        item {
            QCreateRoomCard(store, ui, selected)
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "好友房与网易云官方多人一起听互通：朋友用官方 App 也能加入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QLoginPrompt(onLogin: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Login, null, Modifier.padding(14.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("登录网易云音乐", style = MaterialTheme.typography.titleMedium)
                Text("同步红心歌单、加入好友房都需要登录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onLogin) { Text("去登录") }
        }
    }
}

@Composable
private fun QCreateRoomCard(store: MusicStore, ui: MusicUiState, song: Song?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (song == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.padding(15.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("先选一首开房歌曲", style = MaterialTheme.typography.titleMedium)
                        Text("建议选择可完整播放的在线歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { store.navigate(Destination.SEARCH) }) { Text("去选歌") }
                }
            } else {
                QTrackRow(
                    song, 0, false, song.id in ui.liked,
                    onClick = { store.navigate(Destination.SEARCH) },
                    showIndex = false,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Wifi, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("允许陌生人匹配", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ui.publicRoom, onCheckedChange = { store.savePublicRoom(it) })
                }
                Button(
                    onClick = { store.createRoom() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ui.roomBusy && ui.account != null,
                ) {
                    if (ui.roomBusy) LinearProgressIndicator(Modifier.width(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("创建好友房")
                    }
                }
            }
        }
    }
}

@Composable
private fun QActiveRoom(store: MusicStore, ui: MusicUiState, player: PlayerState, wide: Boolean) {
    val room = ui.room ?: return
    val members = JSONArrayToObjects(room.optJSONArray("members"))
    val online = room.optInt("online", members.size)
    val kind = room.optString("kind", "好友房")
    val roomId = room.optString("roomId")
    var tab by remember { mutableIntStateOf(0) }

    if (wide) {
        Row(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item { QRoomStage(store, ui, player, kind, online, roomId, wide = true) }
                item { QMemberStrip(members, online) }
                item { QSectionTitle("即将播放", "${ui.roomQueue.size} 首 · 由房间同步，点击可顶歌", trailing = {
                    TextButton(onClick = { store.navigate(Destination.SEARCH) }) { Text("推荐歌曲") }
                }) }
                if (ui.roomQueue.isEmpty()) {
                    item { QRoomQueueEmpty { store.navigate(Destination.SEARCH) } }
                } else {
                    itemsIndexed(ui.roomQueue, key = { _, item -> "${item.bizId}:${item.song.id}" }) { index, item ->
                        QRoomQueueRow(store, ui, player, item, index)
                    }
                }
            }
            QChatPanel(store, ui, Modifier.width(360.dp).fillMaxSize())
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = QPagePadding).padding(top = 8.dp)) {
            QChoicePills(
                options = listOf("queue" to "房间队列", "chat" to "聊天"),
                selected = if (tab == 0) "queue" else "chat",
                onSelect = { tab = if (it == "queue") 0 else 1 },
            )
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (slideInHorizontally(tween(220)) { if (targetState == 1) it else -it } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(180)) { if (targetState == 1) -it else it } + fadeOut(tween(140)))
                },
                label = "roomTab",
            ) { current ->
                if (current == 0) {
                    Column(Modifier.fillMaxSize()) {
                        QSectionTitle("即将播放", "${ui.roomQueue.size} 首 · 点歌或顶歌", trailing = {
                            TextButton(onClick = { store.navigate(Destination.SEARCH) }) { Text("推荐歌曲") }
                        })
                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 10.dp),
                        ) {
                            item { QRoomStage(store, ui, player, kind, online, roomId, wide = false) }
                            item { QMemberStrip(members, online) }
                            if (ui.roomQueue.isEmpty()) {
                                item { QRoomQueueEmpty { store.navigate(Destination.SEARCH) } }
                            } else {
                                itemsIndexed(ui.roomQueue, key = { _, item -> "${item.bizId}:${item.song.id}" }) { index, item ->
                                    QRoomQueueRow(store, ui, player, item, index)
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        QRoomChatList(store, ui, Modifier.weight(1f))
                        QRoomChatInput(store, ui)
                    }
                }
            }
        }
    }
}

@Composable
private fun QRoomStage(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    kind: String,
    online: Int,
    roomId: String,
    wide: Boolean,
) {
    val song = player.song
    val position by store.playerPosition.collectAsState()
    QGradientPanel(
        Modifier.fillMaxWidth(),
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(if (wide) 18.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QTag("房间同步", icon = Icons.Rounded.Wifi)
                Spacer(Modifier.width(6.dp))
                QTag(kind)
                Spacer(Modifier.weight(1f))
                Text(roomId.takeLast(6), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cover(song?.cover.orEmpty(), Modifier.size(if (wide) 92.dp else 80.dp), shape = RoundedCornerShape(20.dp), iconSize = 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(song?.title ?: "等待房间同步歌曲", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(song?.artistLine ?: "房间里的歌会自动出现在这里", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Groups, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("$online 人在线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                FilledTonalIconButton(onClick = store.audio::toggle, enabled = song != null, modifier = Modifier.size(42.dp)) {
                    Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "暂停" else "播放", Modifier.size(20.dp))
                }
            }
            QNowPlayingProgress(position, player.duration)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { store.shareInvite() }, modifier = Modifier.weight(1f), shape = CircleShape, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("邀请", maxLines = 1)
                }
                OutlinedButton(onClick = { store.refreshRoom() }, modifier = Modifier.weight(1f), shape = CircleShape, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("同步", maxLines = 1)
                }
                IconButton(onClick = { store.ui("uiLeaveRequest") }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.Logout, "离开房间", Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun QMemberStrip(members: List<org.json.JSONObject>, online: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("房间成员", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("$online 人在线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(members, key = { _, member -> member.optString("id") }) { _, member ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QAvatar(member.optString("cover"), 42.dp)
                    Text(
                        member.optString("title", "听友"),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 60.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun QRoomQueueRow(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    track: RoomTrack,
    index: Int,
) {
    val current = track.song.id == player.song?.id
    val reactions by store.roomReactions.collectAsState(emptyMap())
    val counts = reactions[track.bizId] ?: reactions[track.song.id] ?: (track.upCount to track.likeCount)
    val upCount = counts.first
    val likeCount = counts.second
    QTrackRow(
        track.song, index, current, track.song.id in ui.liked,
        onClick = null,
        onMore = { store.roomOperation(track.song, track.bizId, if (current) "like" else "up") },
        onLike = null,
        owner = track.owner,
        moreIcon = if (current) Icons.Rounded.ThumbUp else Icons.Rounded.ArrowUpward,
        moreLabel = if (current) "点赞" else "顶歌",
        moreCount = if (current) likeCount else upCount,
    )
}

@Composable
private fun QRoomQueue(store: MusicStore, ui: MusicUiState, player: PlayerState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ui.roomQueue.isEmpty()) {
            item { QRoomQueueEmpty { store.navigate(Destination.SEARCH) } }
        } else {
            itemsIndexed(ui.roomQueue, key = { _, item -> "${item.bizId}:${item.song.id}" }) { index, item ->
                QTrackRow(
                    item.song, index, player.song?.id == item.song.id, item.song.id in ui.liked,
                    onClick = { store.roomOperation(item.song, item.bizId, "next") },
                    onMore = { store.roomOperation(item.song, item.bizId, "next") },
                    onLike = null,
                    owner = item.owner,
                )
            }
        }
    }
}

@Composable
internal fun QRoomQueueEmpty(onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Rounded.MusicNote, null, Modifier.padding(9.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("队列还空着", style = MaterialTheme.typography.titleSmall)
                Text("去搜索一首歌，推荐给房间里的朋友", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSearch) { Text("去点歌") }
        }
    }
}

@Composable
private fun QChatPanel(store: MusicStore, ui: MusicUiState, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("房间聊天", style = MaterialTheme.typography.titleMedium)
                Text("和房间里的人说点什么", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QTag("实时", icon = Icons.Rounded.Wifi)
        }
        QRoomChatList(store, ui, Modifier.weight(1f))
        QRoomChatInput(store, ui)
    }
}

@Composable
private fun QRoomChatBubble(message: RoomMessage, mine: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Text(chatClock(message.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message.imageUrl.isNotBlank()) {
                var preview by remember { mutableStateOf(false) }
                val ratio = if (message.imageWidth > 0 && message.imageHeight > 0)
                    message.imageWidth.toFloat() / message.imageHeight.toFloat()
                else 1f
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { preview = true }
                        .semantics { contentDescription = "表情图片" },
                    contentAlignment = Alignment.Center,
                ) {
                    Cover(
                        message.imageUrl,
                        Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2.5f)),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                if (preview) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { preview = false }) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Cover(
                                    message.imageUrl,
                                    Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 3f)).clip(RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun QRoomChatThread(store: MusicStore, ui: MusicUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.chat.isEmpty()) {
            Text(
                "还没有消息，打个招呼吧。",
                Modifier.fillMaxWidth().padding(vertical = 26.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ui.chat.forEach { message ->
                QRoomChatBubble(message, mine = message.name == ui.account?.name)
            }
        }
    }
}

@Composable
private fun QRoomChatList(store: MusicStore, ui: MusicUiState, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val nim by store.roomNimMessages.collectAsState(emptyList())
    val messages = (ui.chat + nim).distinctBy { it.key }.sortedBy { it.time }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) state.scrollToItem(messages.lastIndex)
    }
    LazyColumn(modifier.fillMaxWidth(), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (messages.isEmpty()) {
            item {
                Text(
                    "还没有消息，打个招呼吧。",
                    Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(messages, key = { _, message -> message.key }) { _, message ->
                QRoomChatBubble(message, mine = message.name == ui.account?.name)
            }
        }
    }
}

@Composable
private fun QRoomChatInput(store: MusicStore, ui: MusicUiState) {
    var showEmoji by remember { mutableStateOf(false) }
    var sendingMedia by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).navigationBarsPadding()) {
        if (showEmoji) {
            QStickerPanel(
                store = store,
                scope = "room",
                onText = { store.chatDraft(ui.chatDraft + it) },
                onSticker = { sticker ->
                    scope.launch {
                        runCatching {
                            val room = ui.room?.optString("roomId").orEmpty()
                            if (room.isBlank()) error("请先进入房间")
                            store.sendRoomSticker(room, sticker)
                        }.onSuccess { showEmoji = false }
                            .onFailure { Toast.makeText(context, it.message ?: "表情发送失败", Toast.LENGTH_SHORT).show() }
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { showEmoji = !showEmoji }) {
                Icon(Icons.Rounded.SentimentSatisfied, "表情")
            }
            QAttachButton(allowVoiceVideo = false, onPicked = { media ->
                val room = ui.room?.optString("roomId").orEmpty()
                if (room.isBlank()) {
                    Toast.makeText(context, "请先进入房间", Toast.LENGTH_SHORT).show()
                } else if (!sendingMedia) {
                    scope.launch {
                        sendingMedia = true
                        runCatching {
                            store.sendMedia(
                                media.kind, media.path, media.mime, media.name,
                                media.width, media.height, media.duration,
                                targetRoomId = room,
                            )
                        }.onSuccess { store.refreshRoom() }
                            .onFailure { Toast.makeText(context, it.message ?: "附件发送失败", Toast.LENGTH_SHORT).show() }
                        sendingMedia = false
                    }
                }
            })
            OutlinedTextField(
                value = ui.chatDraft,
                onValueChange = store::chatDraft,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                singleLine = true,
                placeholder = { Text("说点什么…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { store.sendChat() }),
            )
            FilledTonalIconButton(onClick = { store.sendChat() }, enabled = ui.chatDraft.isNotBlank() && !ui.roomBusy) {
                Icon(Icons.Rounded.Send, "发送")
            }
        }
    }
}
