@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


@Composable
internal fun SettingsScreen(store: MusicStore, ui: MusicUiState) {
    var loginDialog by rememberSaveable { mutableStateOf(false) }
    var qrDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.qrUrl) { if (ui.qrUrl.isNotBlank()) qrDialog = true }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionLabel("网易云账号")
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(ui.account?.avatar.orEmpty(), Modifier.size(54.dp), shape = CircleShape, glyph = Icons.Rounded.AccountCircle)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(ui.account?.name ?: "未登录", style = MaterialTheme.typography.titleMedium)
                        Text(ui.account?.let { "账号 ${it.uid}" } ?: "登录后可查看网易云曲库和使用官方好友房", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (ui.account == null) FilledTonalButton(onClick = { loginDialog = true }) { Text("登录") }
                    else IconAction(Icons.Rounded.Logout, "退出登录", onClick = { store.logout() })
                }
            }
        }
        item {
            SectionLabel("外观")
            ChoiceRow(listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"), ui.theme, store::saveTheme, Modifier.fillMaxWidth())
        }
        item {
            SectionLabel("歌词")
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                LyricOptions(store, ui, Modifier.padding(12.dp))
            }
        }

        item { Text("一起迪 · 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
    }
    if (loginDialog) AlertDialog(onDismissRequest = { loginDialog = false }, title = { Text("扫码登录网易云音乐") },
        text = { Text("获取一次性登录二维码。会话凭据通过 Android 系统密钥库加密保存在本机。") },
        confirmButton = { Button(onClick = { loginDialog = false; store.createQr() }, enabled = !ui.accountBusy) { Text("获取二维码") } },
        dismissButton = { TextButton(onClick = { loginDialog = false }) { Text("取消") } })
    if (qrDialog && ui.qrUrl.isNotBlank()) AlertDialog(onDismissRequest = { qrDialog = false; store.cancelQr() }, title = { Text("使用网易云音乐扫码") },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QrImage(ui.qrUrl, Modifier.size(220.dp))
            Text(if (ui.qrScanned) "已扫码，请在网易云音乐确认" else "二维码有效期有限，请在网易云音乐中确认登录", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = { qrDialog = false; store.cancelQr() }) { Text("取消登录") } })
}

@Composable
private fun LyricOptions(store: MusicStore, ui: MusicUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("对齐方式", style = MaterialTheme.typography.titleSmall)
        ChoiceRow(listOf(0 to "左对齐", 1 to "居中"), ui.lyricPrefs.align, { store.saveLyricPrefs(ui.lyricPrefs.copy(align = it)) }, Modifier.fillMaxWidth())
        Text("同步微调 · ${ui.lyricPrefs.shift} ms", style = MaterialTheme.typography.titleSmall)
        Slider((ui.lyricPrefs.shift + 5000) / 10000f, { store.saveLyricPrefs(ui.lyricPrefs.copy(shift = (it * 10000 - 5000).toInt())) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("歌词页播放时保持屏幕常亮", Modifier.weight(1f))
            Switch(ui.lyricPrefs.awake, { store.saveLyricPrefs(ui.lyricPrefs.copy(awake = it)) })
        }
    }
}

@Composable
internal fun QrImage(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) {
        runCatching {
            val matrix = com.google.zxing.MultiFormatWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888).also { image ->
                for (y in 0 until 512) for (x in 0 until 512) image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, "登录二维码", modifier)
}

@Composable
internal fun MusicPanel(panel: Panel, choice: TrackChoice?, store: MusicStore, ui: MusicUiState, player: PlayerState, update: (Panel?) -> Unit, dismiss: () -> Unit) {
    val song = choice?.song ?: player.song
    if (song == null && panel != Panel.QUEUE) return
    var share by rememberSaveable(song?.id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().then(if (panel == Panel.QUEUE) Modifier else Modifier.verticalScroll(rememberScrollState())).padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (panel) {
                Panel.TRACK -> {
                    checkNotNull(song)
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(song.cover, Modifier.size(54.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artistLine, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconAction(Icons.Rounded.Close, "关闭", onClick = dismiss)
                    }
                    if (ui.room == null) PanelAction(Icons.Rounded.PlayArrow, "立即播放") { choice?.let { store.playTracks(it.list, it.list.indexOfFirst { row -> row.id == song.id }.coerceAtLeast(0)) }; dismiss() }
                    if (ui.room == null) PanelAction(Icons.Rounded.Groups, "选为开房歌曲") { store.chooseRoomSong(song); dismiss() }
                    if (ui.room != null) PanelAction(Icons.Rounded.GroupAdd, "推荐到房间") { store.roomOperation(song, "0", "add"); dismiss() }
                    PanelAction(if (song.id in ui.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (song.id in ui.liked) "取消收藏" else "收藏", enabled = ui.favoriteBusy.isEmpty()) { store.toggleLiked(song); dismiss() }
                    PanelAction(Icons.Rounded.Share, "分享") { share = true }
                    if (ui.room == null) {
                        PanelAction(Icons.Rounded.PlaylistAdd, "添加到播放队列") { store.audio.enqueue(song, false); dismiss() }
                        PanelAction(Icons.Rounded.SkipNext, "下一首播放") { store.audio.enqueue(song, true); dismiss() }
                    }
                    PanelAction(Icons.Rounded.Info, "歌曲信息") { update(Panel.DETAILS) }
                }
                Panel.DETAILS -> {
                    checkNotNull(song)
                    Text("歌曲信息", style = MaterialTheme.typography.headlineSmall)
                    DetailRow("歌曲", song.title); DetailRow("歌手", song.artistLine); DetailRow("专辑", song.album.ifBlank { "未知专辑" })
                    DetailRow("时长", clockLabel(song.duration)); DetailRow("格式", song.format.ifBlank { "未知" })
                    DetailRow("采样率", song.sampleRate.takeIf { it > 0 }?.let { "$it Hz" } ?: "未知")
                    DetailRow("码率", song.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "未知")
                    DetailRow("来源", "网易云音乐")
                }
                Panel.QUEUE -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ui.room != null) "房间队列" else "播放队列", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        IconAction(Icons.Rounded.Close, "关闭", onClick = dismiss)
                    }
                    val queue = if (ui.room != null) ui.roomQueue.map { it.song } else player.queue
                    if (queue.isEmpty()) EmptyNotice(Icons.Rounded.QueueMusic, "队列为空")
                    else LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(queue, key = { index, item -> "$index:${item.id}" }) { index, item ->
                            TrackRow(item, index, queue.size, player.at == index, { if (ui.room != null) store.roomOperation(item, ui.roomQueue.getOrNull(index)?.bizId.orEmpty(), "next") else store.selectPlayback(index) },
                                trailing = { if (ui.room == null) IconAction(Icons.Rounded.Close, "从队列移除") { store.audio.remove(index) } })
                        }
                    }
                }
                Panel.LYRIC_SETTINGS -> {
                    Text("歌词设置", style = MaterialTheme.typography.headlineSmall)
                    LyricOptions(store, ui)
                }
            }
        }
    }
    if (share && song != null) AlertDialog(onDismissRequest = { share = false }, title = { Text("分享曲目") }, text = { Text("选择一种分享方式。不会替你发送。") },
        confirmButton = { TextButton(onClick = {
            share = false
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${song.title} - ${song.artistLine}")
            context.startActivity(android.content.Intent.createChooser(send, "分享曲目"))
        }) { Text("选择应用分享") } }, dismissButton = { TextButton(onClick = { share = false }) { Text("取消") } })
}

@Composable
private fun PanelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, enabled = enabled, onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun InviteDialog(value: String, onValue: (String) -> Unit, ui: MusicUiState, store: MusicStore, dismiss: () -> Unit) {
    val close = { store.dismissInvite(); dismiss() }
    AlertDialog(onDismissRequest = close, title = { Text("加入好友房") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value, { onValue(it.take(4096)); store.dismissInvite() }, Modifier.fillMaxWidth(), label = { Text("粘贴网易云邀请链接") }, minLines = 2, maxLines = 4)
            ui.invitePreview?.let {
                Text("房间 ${it.roomId}", style = MaterialTheme.typography.titleMedium)
                if (it.inviter.isNotBlank()) Text("邀请人：${it.inviter}")
                if (it.song.isNotBlank()) Text("当前曲目：${it.song}")
                Text(when {
                    it.joinable -> "可加入"
                    it.status == "AVAILABLE" -> "不能加入自己创建的房间"
                    it.status == "EXPIRED" -> "邀请已过期"
                    else -> "房间状态：${it.status}"
                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }, confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { store.previewInvite(value) }, enabled = value.isNotBlank() && !ui.roomBusy) { Text(if (ui.roomBusy) "读取中" else "预览") }
                Button(onClick = { store.joinInvite() }, enabled = ui.invitePreview?.joinable == true && ui.account != null && !ui.roomBusy) { Text("加入房间") }
            }
        }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}
