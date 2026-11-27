@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DmConversation(
    val uid: String,
    val nickname: String,
    val avatar: String,
    val preview: String,
    val time: Long,
    val unread: Int,
)

data class DmAlbum(
    val id: String,
    val title: String,
    val cover: String,
    val artist: String,
    val count: Int,
)

data class DmMessage(
    val key: String,
    val mine: Boolean,
    val name: String,
    val avatar: String,
    val text: String,
    val time: Long,
    val inviteLink: String = "",
    val album: DmAlbum? = null,
    val mediaUrl: String = "",
    val mediaKind: String = "",
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val mediaDuration: Long = 0L,
)

@Composable
internal fun QPrivateMessagesScreen(
    store: MusicStore,
    ui: MusicUiState,
    onClose: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onOpenAlbum: (Collection) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<DmConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DmConversation?>(null) }
    var messages by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    var chatLoading by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var newChat by remember { mutableStateOf(false) }
    var newUid by remember { mutableStateOf("") }
    val account = ui.account

    suspend fun reloadConversations() {
        if (account == null) return
        loading = true
        error = ""
        runCatching { store.privateConversations() }
            .onSuccess { raw ->
                Log.d("YiqidiDM", raw.toString().take(6000))
                conversations = parseConversations(raw, account.uid)
                if (conversations.isEmpty()) error = responseHint(raw)
            }
            .onFailure { error = it.message.orEmpty().ifBlank { "读取私信列表失败" } }
        loading = false
    }

    suspend fun loadHistory(silent: Boolean = false) {
        val peer = selected ?: return
        if (!silent) chatLoading = true
        chatError = ""
        runCatching { store.privateHistory(peer.uid) }
            .onSuccess { raw ->
                Log.d("YiqidiDM", raw.toString().take(6000))
                messages = parseMessages(raw, account?.uid.orEmpty(), peer)
                if (messages.isEmpty() && !silent) chatError = responseHint(raw)
            }
            .onFailure { if (!silent) chatError = it.message.orEmpty().ifBlank { "读取私信失败" } }
        if (!silent) chatLoading = false
    }

    LaunchedEffect(account?.uid) {
        if (account != null) reloadConversations()
    }
    LaunchedEffect(selected?.uid) {
        if (selected != null) {
            loadHistory()
            while (true) {
                delay(10_000)
                loadHistory(silent = true)
            }
        } else {
            messages = emptyList()
        }
    }
    val sendMedia: (QMediaFile) -> Unit = { media ->
        val peer = selected
        if (peer != null && !sending) {
            scope.launch {
                sending = true
                chatError = ""
                runCatching {
                    store.sendMedia(
                        media.kind, media.path, media.mime, media.name,
                        media.width, media.height, media.duration,
                        targetUid = peer.uid,
                    )
                }.onSuccess {
                    loadHistory()
                    reloadConversations()
                }.onFailure { chatError = it.message.orEmpty().ifBlank { "附件发送失败" } }
                sending = false
            }
        }
    }
    BackHandler(enabled = selected != null) { selected = null }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { if (selected != null) selected = null else onClose() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("私信", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("网易云消息 · 实时轮询", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { newChat = true }, enabled = account != null) { Icon(Icons.Rounded.Add, "新私信") }
                IconButton(onClick = { scope.launch { reloadConversations(); if (selected != null) loadHistory() } }) {
                    Icon(Icons.Rounded.Refresh, "刷新")
                }
            }
            if (account == null) {
                QEmptyState(Icons.Rounded.ChatBubbleOutline, "请先登录", "登录网易云音乐后才能收发私信。", actionLabel = "去登录", onAction = onClose)
                return@Column
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        QConversationList(
                            conversations, selected, loading, error,
                            onSelect = { selected = it },
                            onRefresh = { scope.launch { reloadConversations() } },
                            modifier = Modifier.width(370.dp).fillMaxHeight(),
                        )
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            QChatPane(
                                store = store,
                                peer = selected,
                                messages = messages,
                                loading = chatLoading,
                                error = chatError,
                                draft = draft,
                                sending = sending,
                                onJoinRoom = onJoinRoom,
                                onOpenAlbum = onOpenAlbum,
                                onMedia = sendMedia,
                                onDraft = { draft = it },
                                onError = { chatError = it },
                                onSend = {
                                    val peer = selected
                                    if (peer != null && draft.isNotBlank() && !sending) {
                                        scope.launch {
                                            sending = true
                                            chatError = ""
                                            val body = draft
                                            runCatching { store.sendPrivate(peer.uid, body) }
                                                .onSuccess {
                                                    draft = ""
                                                    loadHistory()
                                                    reloadConversations()
                                                }
                                                .onFailure { chatError = it.message.orEmpty().ifBlank { "发送失败，请重试" } }
                                            sending = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = selected?.uid,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                        },
                        label = "dm-page",
                    ) { uid ->
                        if (uid == null) {
                            QConversationList(
                                conversations, selected, loading, error,
                                onSelect = { selected = it },
                                onRefresh = { scope.launch { reloadConversations() } },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            QChatPane(
                                store = store,
                                peer = selected,
                                messages = messages,
                                loading = chatLoading,
                                error = chatError,
                                draft = draft,
                                sending = sending,
                                onJoinRoom = onJoinRoom,
                                onOpenAlbum = onOpenAlbum,
                                onMedia = sendMedia,
                                onDraft = { draft = it },
                                onError = { chatError = it },
                                onSend = {
                                    val peer = selected
                                    if (peer != null && draft.isNotBlank() && !sending) {
                                        scope.launch {
                                            sending = true
                                            chatError = ""
                                            val body = draft
                                            runCatching { store.sendPrivate(peer.uid, body) }
                                                .onSuccess {
                                                    draft = ""
                                                    loadHistory()
                                                    reloadConversations()
                                                }
                                                .onFailure { chatError = it.message.orEmpty().ifBlank { "发送失败，请重试" } }
                                            sending = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (newChat) {
        AlertDialog(
            onDismissRequest = { newChat = false },
            title = { Text("发起私信") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUid,
                        onValueChange = { newUid = it.filter(Char::isDigit).take(24) },
                        label = { Text("对方 UID") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                    Text("输入网易云用户 UID，打开与对方的私信。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newUid.isNotBlank(),
                    onClick = {
                        selected = DmConversation(newUid, "UID $newUid", "", "", 0L, 0)
                        newChat = false
                    },
                ) { Text("打开") }
            },
            dismissButton = { TextButton(onClick = { newChat = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun QConversationList(
    conversations: List<DmConversation>,
    selected: DmConversation?,
    loading: Boolean,
    error: String,
    onSelect: (DmConversation) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
            }
        }
        if (error.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("私信接口", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (conversations.isEmpty() && !loading && error.isBlank()) {
            QEmptyState(Icons.Rounded.ChatBubbleOutline, "还没有私信", "点右上角 + 输入对方 UID 开始聊天。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(conversations, key = { it.uid }) { peer ->
                    val active = peer.uid == selected?.uid
                    Surface(
                        onClick = { onSelect(peer) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            QAvatar(peer.avatar, 48.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(peer.nickname, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(peer.preview.ifBlank { "暂无消息" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(chatClock(peer.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (peer.unread > 0) QTag(peer.unread.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QChatPane(
    store: MusicStore,
    peer: DmConversation?,
    messages: List<DmMessage>,
    loading: Boolean,
    error: String,
    draft: String,
    sending: Boolean,
    onJoinRoom: (String) -> Unit,
    onOpenAlbum: (Collection) -> Unit,
    onMedia: (QMediaFile) -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onError: (String) -> Unit,
) {
    if (peer == null) {
        QEmptyState(Icons.Rounded.ChatBubbleOutline, "选择一个会话", "或在右上角输入 UID 发起新的私信。")
        return
    }
    var showEmoji by remember { mutableStateOf(false) }
    val paneScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            QAvatar(peer.avatar, 40.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(peer.nickname, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("UID ${peer.uid}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (error.isNotBlank()) {
            Text(error, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty() && !loading) {
                item {
                    Text(
                        "还没有消息，打个招呼吧。",
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(messages, key = { it.key }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (message.mine) "我" else message.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(6.dp))
                            Text(chatClock(message.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(message.text, style = MaterialTheme.typography.bodyMedium)
                        message.album?.let { album ->
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    onOpenAlbum(
                                        Collection(
                                            id = album.id, title = album.title, cover = album.cover,
                                            count = album.count, owner = album.artist,
                                            detail = album.artist, kind = "album",
                                        ),
                                    )
                                },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Cover(album.cover, Modifier.fillMaxWidth().height(140.dp), shape = RoundedCornerShape(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        QTag("专辑")
                                        Text(
                                            album.title,
                                            Modifier.padding(start = 6.dp).weight(1f),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (album.artist.isNotBlank()) {
                                        Text(album.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (message.mediaUrl.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            if (message.mediaKind == "image" || message.mediaKind.isBlank()) {
                                Cover(
                                    message.mediaUrl,
                                    Modifier.fillMaxWidth().height(190.dp),
                                    shape = RoundedCornerShape(12.dp),
                                )
                            } else {
                                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (message.mediaKind == "voice") Icons.Rounded.Mic else Icons.Rounded.Videocam,
                                            null,
                                            Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            if (message.mediaKind == "voice") "语音 ${message.mediaDuration.coerceAtLeast(1)} 秒"
                                            else "视频 ${message.mediaDuration.coerceAtLeast(1)} 秒",
                                            Modifier.padding(start = 8.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                        if (message.inviteLink.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Icon(Icons.Rounded.Headphones, null, Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                        Text("多人一起听", style = MaterialTheme.typography.titleSmall)
                                        Text("和好友一起听歌", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(onClick = { onJoinRoom(message.inviteLink) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                        Text("加入房间", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showEmoji) {
            QStickerPanel(
                store = store,
                scope = "private",
                onText = { onDraft(draft + it) },
                onSticker = { sticker ->
                    paneScope.launch {
                        runCatching { store.sendSticker(peer.uid, sticker) }
                            .onSuccess { showEmoji = false }
                            .onFailure { onError(it.message.orEmpty()) }
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { showEmoji = !showEmoji }) { Icon(Icons.Rounded.SentimentSatisfied, "表情") }
            QAttachButton(onPicked = onMedia)
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                singleLine = true,
                placeholder = { Text("发送私信…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            IconButton(onClick = onSend, enabled = draft.isNotBlank() && !sending) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Rounded.Send, "发送")
            }
        }
    }
}

private fun parseConversations(root: JSONObject, selfUid: String): List<DmConversation> {
    val data = root.optJSONObject("data")
    val array = sequenceOf(
        data?.optJSONArray("msgs"),
        data?.optJSONArray("records"),
        data?.optJSONArray("list"),
        root.optJSONArray("msgs"),
        root.optJSONArray("records"),
        data as? JSONArray,
    ).firstOrNull { it != null } ?: JSONArray()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val from = item.optJSONObject("fromUser") ?: item.optJSONObject("user")
        val to = item.optJSONObject("toUser")
        val fromId = idOf(from) ?: item.optString("fromUserId").takeIf { it.isNotBlank() }
        val toId = idOf(to) ?: item.optString("toUserId").takeIf { it.isNotBlank() }
        val peer = when {
            fromId != null && fromId != selfUid && toId == selfUid -> from
            toId != null && toId != selfUid && fromId == selfUid -> to
            toId != null && toId != selfUid -> to
            fromId != null && fromId != selfUid -> from
            else -> to ?: from
        }
        val uid = idOf(peer) ?: item.optString("uid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = peer?.optString("nickname").orEmpty().ifBlank { item.optString("nickname") }.ifBlank { "UID $uid" }
        val avatar = peer?.optString("avatarUrl").orEmpty().ifBlank { peer?.optString("avatar").orEmpty() }.ifBlank { item.optString("avatarUrl") }
        val lastRaw = item.optString("lastMsg")
        val last = runCatching { JSONObject(lastRaw) }.getOrNull()
        val preview = last?.optString("msg").orEmpty()
            .ifBlank { item.optString("msg") }
            .ifBlank { item.optString("message") }
            .ifBlank { item.optString("preview") }
            .ifBlank { item.optJSONObject("msgBody")?.optString("msg").orEmpty() }
            .ifBlank { last?.optString("title").orEmpty() }
            .ifBlank {
                when {
                    last?.optJSONObject("picInfo") != null -> "[图片]"
                    last?.has("voiceUrl") == true -> "[语音]"
                    last?.has("videoUrl") == true -> "[视频]"
                    else -> ""
                }
            }
        val time = item.optLong("lastMsgTime").takeIf { it > 0 }
            ?: item.optLong("time").takeIf { it > 0 }
            ?: item.optLong("lastTime")
        DmConversation(
            uid, name, avatar, preview.replace("\n", " ").take(80), time,
            item.optInt("newMsgCount", item.optInt("unreadCount", item.optInt("unread", 0))),
        )
    }.distinctBy { it.uid }.sortedByDescending { it.time }
}

private fun parseMessages(root: JSONObject, selfUid: String, peer: DmConversation): List<DmMessage> {
    val data = root.optJSONObject("data")
    val array = sequenceOf(
        data?.optJSONArray("msgs"),
        data?.optJSONArray("records"),
        data?.optJSONArray("list"),
        root.optJSONArray("msgs"),
        root.optJSONArray("records"),
    ).firstOrNull { it != null } ?: JSONArray()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val from = item.optJSONObject("fromUser")
        val senderId = idOf(from) ?: item.optString("sendUid").takeIf { it.isNotBlank() }.orEmpty()
        val name = from?.optString("nickname").orEmpty().ifBlank { item.optString("nickname") }.ifBlank { peer.nickname }
        val avatar = from?.optString("avatarUrl").orEmpty().ifBlank { item.optString("avatarUrl") }.ifBlank { peer.avatar }
        val body = item.optJSONObject("msgBody") ?: item.optJSONObject("imChatRoomMsgBody")
        val raw = item.optString("msg")
        val parsed = if (raw.trimStart().startsWith("{")) runCatching { JSONObject(raw) }.getOrNull() else null
        val text = parsed?.optString("msg").orEmpty()
            .ifBlank { if (parsed != null) "" else raw }
            .ifBlank { item.optString("message") }
            .ifBlank { body?.optString("msg").orEmpty() }
            .ifBlank { item.optString("body").let { value -> runCatching { JSONObject(value).optString("text") }.getOrDefault("") } }
            .ifBlank { item.optString("text") }
        val media = mediaFrom(item, parsed)
        val displayText = text.ifBlank {
            when (media.kind) { "image" -> "图片"; "voice" -> "语音"; "video" -> "视频"; else -> "" }
        }
        if (displayText.isBlank()) return@mapNotNull null
        val time = item.optLong("time").takeIf { it > 0 } ?: item.optLong("sendTime").takeIf { it > 0 } ?: item.optLong("msgTime")
        val key = "$senderId:$time:${displayText.hashCode()}"
        DmMessage(
            key, senderId == selfUid, name, avatar, displayText, time,
            inviteLinkFrom(raw, parsed, item, senderId), albumFrom(parsed),
            media.url, media.kind, media.width, media.height, media.duration,
        )
    }.distinctBy { it.key }.sortedBy { it.time }
}

private data class DmMedia(val url: String = "", val kind: String = "", val width: Int = 0, val height: Int = 0, val duration: Long = 0L)

private fun mediaFrom(item: JSONObject, parsed: JSONObject?): DmMedia {
    val body = item.optJSONObject("msgBody")?.optString("body")
        ?.takeIf { it.trimStart().startsWith("{") }
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
    val candidates = listOfNotNull(parsed, body)
    var url = ""
    var kind = ""
    var width = 0
    var height = 0
    var duration = 0L
    candidates.forEach { obj ->
        val pic = obj.optJSONObject("picInfo")
        val voice = obj.optString("voiceUrl")
        val video = obj.optString("videoUrl")
        val image = pic?.optString("picUrl").orEmpty().ifBlank { obj.optString("url") }
            .ifBlank { obj.optString("emojiImgUrl") }
        when {
            voice.isNotBlank() -> { url = voice; kind = "voice" }
            video.isNotBlank() -> { url = video; kind = "video" }
            image.isNotBlank() -> { url = image; kind = "image" }
        }
        if (obj.has("emojiId") && obj.optString("emojiId") != "0") kind = "image"
        if (pic != null) {
            if (width == 0) width = pic.optInt("width")
            if (height == 0) height = pic.optInt("height")
        }
        if (obj.has("width") && width == 0) width = obj.optInt("width")
        if (obj.has("height") && height == 0) height = obj.optInt("height")
        if (obj.has("duration") && duration == 0L) duration = obj.optLong("duration")
    }
    if (url.isBlank()) return DmMedia()
    if (url.startsWith("http://")) url = "https://" + url.removePrefix("http://")
    if (kind.isBlank()) kind = if (width > 0) "image" else ""
    return DmMedia(url, kind, width, height, duration)
}

private fun albumFrom(parsed: JSONObject?): DmAlbum? {
    val album = parsed?.optJSONObject("album") ?: parsed?.optJSONObject("generalMsg")?.optJSONObject("album") ?: return null
    val id = album.opt("id")?.toString().orEmpty()
    val title = album.optString("name").ifBlank { album.optString("title") }
    if (id.isBlank() || title.isBlank()) return null
    val cover = (
        album.optString("picUrl").ifBlank { album.optString("blurPicUrl") }.ifBlank { album.optString("cover") }
    ).let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
    val artist = album.optJSONObject("artist")?.optString("name").orEmpty()
        .ifBlank { album.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty() }
    return DmAlbum(id, title, cover, artist, album.optInt("size", 0))
}

private val ROOM_ID_IN_TEXT = Regex("""roomId(?:%3D|=|["':\s])+([A-Za-z0-9_%\-]{4,120})""", RegexOption.IGNORE_CASE)
private val INVITER_IN_TEXT = Regex("""inviter(?:Uid|UId|Id)(?:%3D|=|["':\s])+(\d{2,24})""", RegexOption.IGNORE_CASE)

private fun deepDecode(value: String): String {
    var current = value
    repeat(6) {
        val next = runCatching { java.net.URLDecoder.decode(current, "UTF-8") }.getOrDefault(current)
        if (next == current) return current
        current = next
    }
    return current
}

private fun inviteLinkFrom(raw: String, parsed: JSONObject?, item: JSONObject, senderId: String): String {
    val general = parsed?.optJSONObject("generalMsg")
    val sources = buildList {
        add(raw)
        parsed?.toString()?.let { add(it) }
        listOf("nativeUrl", "url", "shareUrl", "jumpUrl", "webUrl").forEach { key ->
            item.optString(key).takeIf { it.isNotBlank() }?.let { add(it) }
            parsed?.optString(key)?.takeIf { it.isNotBlank() }?.let { add(it) }
            general?.optString(key)?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        item.optJSONObject("msgBody")?.toString()?.let { add(it) }
    }
    var room = ""
    var inviter = ""
    sources.forEach { original ->
        val source = deepDecode(original)
        if (room.isBlank()) ROOM_ID_IN_TEXT.find(source)?.groupValues?.getOrNull(1)?.let { room = it }
        if (inviter.isBlank()) INVITER_IN_TEXT.find(source)?.groupValues?.getOrNull(1)?.let { inviter = it }
    }
    if (room.isBlank()) room = parsed?.optString("roomId").orEmpty().ifBlank { item.optString("roomId") }
    if (inviter.isBlank()) inviter = parsed?.optString("inviterUid").orEmpty().ifBlank { parsed?.optString("inviterId").orEmpty() }.ifBlank { senderId }
    if (room.length < 4 || room.length > 120 || inviter.isBlank()) return ""
    return "https://st.music.163.com/listen-together/multishare/index.html?roomId=$room&inviterUid=$inviter&isFLT=false"
}

private fun idOf(value: JSONObject?): String? {
    if (value == null) return null
    val raw = value.opt("userId") ?: value.opt("uid")
    return when (raw) {
        is Number -> raw.toLong().toString()
        is String -> raw.takeIf { it.isNotBlank() && it != "0" }
        else -> null
    }
}

private fun responseHint(root: JSONObject): String {
    val code = root.optInt("code", 200)
    return when {
        code != 200 -> root.optString("message").ifBlank { root.optString("msg").ifBlank { "接口返回 $code" } }
        else -> ""
    }
}