@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RoomScreen(store: MusicStore, ui: MusicUiState, player: PlayerState, onSettings: () -> Unit, onSearch: () -> Unit, onInvite: () -> Unit, onShare: () -> Unit, onQueue: () -> Unit) {
    val room = ui.room
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    if (room != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "room-summary") {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(room.optString("kind", "一起听房间"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                IconAction(Icons.Rounded.Refresh, "同步房间状态", onClick = store::refreshRoom)
                            }
                            Text("房间 ${room.optString("roomId")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("在线 ${room.optString("online", "1")} 人", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onShare, enabled = room.optString("invite").isNotBlank()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("邀请好友") }
                                OutlinedButton(onClick = onQueue) { Icon(Icons.Rounded.QueueMusic, null); Spacer(Modifier.width(6.dp)); Text("房间队列") }
                            }
                        }
                    }
                }
                item(key = "room-members") {
                    val members = room.optJSONArray("members").objects { Song(it.optString("id"), it.optString("title"), "", "", it.optString("cover"), 0) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("房间成员 · ${members.size}")
                        if (members.isEmpty()) Text("正在同步成员…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            members.take(6).forEach { member ->
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Cover(member.cover, Modifier.size(30.dp), shape = CircleShape)
                                        Text(member.title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                player.song?.let { song ->
                    item(key = "room-playback") {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, onClick = onQueue) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(song.cover, Modifier.size(44.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text("房间同步播放", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(song.artistLine, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = store.audio::toggle) { Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "本机暂停或继续") }
                            }
                        }
                    }
                }
                item(key = "room-chat") { SectionLabel("房间聊天") }
                itemsIndexed(ui.chat, key = { _, item -> item.key }) { _, item ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.name.ifBlank { "听友" }, style = MaterialTheme.typography.labelLarge)
                                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (ui.chat.isEmpty()) item { EmptyNotice(Icons.Rounded.ChatBubbleOutline, "房间里还没有消息") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = ui.chatDraft, onValueChange = store::chatDraft, modifier = Modifier.weight(1f), placeholder = { Text("说点什么") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), shape = MaterialTheme.shapes.extraLarge)
                FilledIconButton(onClick = store::sendChat, enabled = ui.chatDraft.isNotBlank() && !ui.roomBusy) { Icon(Icons.Rounded.Send, "发送") }
            }
            OutlinedButton(onClick = { confirmLeave = true }, enabled = !ui.roomBusy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text(if (ui.roomBusy) "请稍候" else "离开房间") }
        }
    } else {

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.account == null) {
                EmptyNotice(Icons.Rounded.AccountCircle, "登录后使用好友房")
                FilledTonalButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Login, null); Spacer(Modifier.width(8.dp)); Text("前往账号设置登录") }
            } else {
                FilledTonalButton(onClick = onInvite, enabled = !ui.roomBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(8.dp)); Text("粘贴好友邀请链接")
                }
                OutlinedButton(onClick = store::refreshRoom, enabled = !ui.roomBusy, modifier = Modifier.fillMaxWidth()) { Text("恢复当前房间") }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SectionLabel("开房歌曲")
                val selected = ui.roomChoiceSong
                if (selected != null) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(selected.cover, Modifier.size(54.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(selected.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(selected.artistLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconAction(Icons.Rounded.Edit, "重新选择歌曲", onClick = onSearch)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ui.publicRoom) "公开好友房" else "仅邀请好友", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(ui.publicRoom, store::savePublicRoom)
                    }
                    Button(onClick = store::createRoom, enabled = !ui.roomBusy, modifier = Modifier.fillMaxWidth()) {
                        if (ui.roomBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.PlayCircle, null)
                        Spacer(Modifier.width(8.dp)); Text(if (ui.roomBusy) "正在创建" else "创建好友房")
                    }

                } else {
                    EmptyNotice(Icons.Rounded.MusicNote, "先搜索一首在线歌曲", detail = "房间歌曲需由网易云音乐提供完整音源")
                    FilledTonalButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text("搜索网易云音乐") }
                }

            }
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("离开一起听房间？") },
        text = { Text("离开后将恢复个人播放队列。") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; store.leaveRoom() }) { Text("离开") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("继续听") } })
}
