package dev.yiqidi.mobile

import android.app.Application
import android.content.ComponentName
import android.content.Context

import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class YiqidiApplication : Application() {
    internal val music by lazy { MusicStore(this) }

    override fun onCreate() {
        super.onCreate()
        NimChatroom.init(this)
    }
}

internal class MusicStore(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = YiqidiPrefs(app)
    private val ui = MutableStateFlow(MusicUiState())
    private val player = MutableStateFlow(PlayerState())
    private val layout = MutableStateFlow(JSONObject())
    private val position = MutableStateFlow(0L)
    private val line = MutableStateFlow(-1)
    val state = ui.asStateFlow()
    val uiTree = layout.asStateFlow()
    val audioState = player.asStateFlow()
    val playerPosition = position.asStateFlow()
    val activeLyric = line.asStateFlow()
    private val _share = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val share = _share.asSharedFlow()
    private val nimStatusFlow = MutableStateFlow("")
    val nimStatus = nimStatusFlow.asStateFlow()
    private val reactions = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val roomReactions = reactions.asStateFlow()
    private val nimMessages = MutableStateFlow<List<RoomMessage>>(emptyList())
    val roomNimMessages = nimMessages.asStateFlow()
    private val pickFlow = MutableStateFlow<Song?>(null)
    val playlistPicker = pickFlow.asStateFlow()
    private val recommendFlow = MutableStateFlow<Song?>(null)
    val recommendAsk = recommendFlow.asStateFlow()
    val audio = AudioControls(this)
    private val platform = PlatformEffects(prefs, scope, ::send, _share::tryEmit)
    private val events = ArrayDeque<Pair<String, JSONObject>>()
    private var loading = false
    private var booted = false
    private var draining = false
    private var controller: ListenableFuture<MediaController>? = null
    var output: AndroidAudio? = null
        private set
    var mediaStateChanged: (() -> Unit)? = null

    fun boot() {
        if (loading || booted) return
        loading = true
        scope.launch {
            val saved = platform.restore()
            withContext(Dispatchers.IO) {
                runCatching { NativeBridge.request("deviceId", args("value" to prefs.deviceId())) }
            }
            events.addFirst("boot" to saved)
            booted = true
            loading = false
            drain()
        }
    }

    fun connectPlayback(context: Context = app) {
        if (controller != null) return
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(context, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@MusicStore.controller?.let { it.isDone && runCatching { it.get() === controller }.getOrDefault(false) } == true) {
                        this@MusicStore.controller = null
                    }
                }
            }).buildAsync()
        controller = future
        future.addListener({
            if (controller === future && runCatching { future.get() }.isFailure) {
                controller = null
                send("platformError", args("message" to "播放服务暂未就绪，请重试"))
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun attach(adapter: AndroidAudio) {
        output = adapter
        send("audioAttached", args("attached" to true))
    }

    fun detach(adapter: AndroidAudio) {
        if (output !== adapter) return
        output = null
        send("audioAttached", args("attached" to false))
    }

    internal fun send(event: String, data: JSONObject = JSONObject()) {
        check(Looper.myLooper() == Looper.getMainLooper())
        events.addLast(event to data)
        if (booted) drain() else boot()
    }

    private fun drain() {
        if (draining) return
        draining = true
        try {
            while (events.isNotEmpty()) {
                val (name, payload) = events.removeFirst()
                if (name == "uiLayout") {
                    layout.value = NativeBridge.evaluate("appEvent", args(
                        "event" to "uiSnapshot",
                        "data" to args("dark" to ((app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)),
                    ))["uiTree"] as? JSONObject ?: JSONObject()
                    continue
                }
                val reply = try {
                    NativeBridge.evaluate("appEvent", args("event" to name, "data" to payload))
                } catch (error: MusicError) {
                    ui.value = ui.value.copy(error = error.message.orEmpty(), info = "")
                    continue
                }
                reply.optJSONObject("uiTree")?.let { layout.value = it }
                val previousLiked = ui.value.liked
                reply.optJSONObject("ui")?.let { ui.value = MusicUiState.from(it) }
                reply.optJSONObject("input")?.let {
                    ui.value = ui.value.copy(query = it.optString("query", ui.value.query), error = it.optString("error", ui.value.error), info = it.optString("info", ui.value.info))
                }
                val previous = player.value
                reply.optJSONObject("player")?.let { player.value = PlayerState.from(it) }
                reply.optJSONObject("progress")?.let {
                    position.value = it.optLong("position")
                    line.value = it.optInt("lyricLine", -1)
                }

                reply.optJSONArray("effects").objects { it }.forEach { effect ->
                    if (effect.optString("kind") == "audio") {
                        if (output == null && effect.optString("op") == "load") connectPlayback()
                        output?.apply(effect)
                    } else if (effect.optString("kind") == "playlistPicker") {
                        effect.optJSONObject("song")?.let { pickFlow.value = Song.from(it) }
                    } else platform.apply(effect)
                }
                if (previous != player.value || previousLiked != ui.value.liked) mediaStateChanged?.invoke()
                if (name == "boot" || name == "snapshot" || name == "navigate" || name == "selectKind" || name.startsWith("ui") ||
                    name.startsWith("library") || name.startsWith("search") || name.startsWith("room") || name.startsWith("player") || name.startsWith("lyrics")) {
                    layout.value = NativeBridge.evaluate("appEvent", args("event" to "uiSnapshot", "data" to args(
                        "dark" to ((app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES),
                    )))["uiTree"] as? JSONObject ?: JSONObject()
                }
            }
        } finally { draining = false }
    }

    fun navigate(target: Destination) = send("navigate", args("page" to target.name))
    fun ui(event: String, data: JSONObject = JSONObject()) = send(event, data)
    fun back() = send("uiBack", args("dark" to ((app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)))
    fun selectKind(kind: LibraryKind) = send("selectKind", args("name" to kind.name))
    fun query(value: String) = send("query", args("value" to value))
    fun search(query: String = state.value.query, kind: String = state.value.searchKind, append: Boolean = false) =
        send("search", args("query" to query, "kind" to kind, "append" to append))
    fun closeCollection() = send("closeCollection")
    fun refreshLibrary() = send("refreshLibrary")
    fun loadMoreLiked() = send("loadMoreLiked")
    fun loadMoreAlbums() = send("loadMoreAlbums")
    fun filterPlaylists(value: String) = send("libraryFilter", args("value" to value))
    fun playlistQuery(value: String) = send("libraryQuery", args("value" to value))
    fun albumQuery(value: String) = send("albumQuery", args("value" to value))
    fun openCollection(item: Collection) = send("openCollection", args("item" to item.json()))
    fun loadMoreCollection() = send("loadMoreCollection")
    fun toggleLiked(song: Song) = send("favorite", args("song" to song.json()))
    fun clearMessage(value: String) = send("clearMessage", args("value" to value))
    fun saveTheme(theme: Int) = send("theme", args("value" to theme))
    fun updateUiPalette(systemDark: Boolean, palette: JSONObject) = send("uiPalette", args("dark" to systemDark, "palette" to palette))
    fun saveLyricPrefs(prefs: LyricPrefs) = send("lyricPrefs", args("align" to prefs.align, "shift" to prefs.shift, "awake" to prefs.awake))
    fun setLyricMode(static: Boolean) = send("lyricMode", args("static" to static))
    fun showLyrics(visible: Boolean) = send("lyricsVisible", args("visible" to visible))

    fun createQr() = send("login")
    fun cancelQr() = send("cancelLogin")
    fun logout() = send("logout")
    fun refreshRoom() = send("roomRefresh")
    fun chooseRoomSong(song: Song) = send("roomChooseSong", args("song" to song.json()))
    fun savePublicRoom(public: Boolean) = send("roomPublic", args("public" to public))
    fun createRoom() = send("roomCreate")
    fun previewInvite(link: String) = send("roomPreview", args("invite" to link))
    fun dismissInvite() = send("roomDismissInvite")
    fun joinInvite() = send("roomJoin")
    fun shareInvite() = send("roomShare")
    fun leaveRoom() = send("roomLeave")
    fun roomOperation(song: Song, bizId: String, operation: String) = send("roomOperation", args("song" to song.json(), "bizId" to bizId, "operation" to operation))
    fun chatDraft(text: String) = send("roomChatDraft", args("text" to text))
    fun sendChat() = send("roomChatSend")

    fun playTracks(songs: List<Song>, index: Int, shuffle: Boolean = false) {
        if (state.value.room != null) {
            songs.getOrNull(index)?.let { recommendFlow.value = it; return }
        }
        send("playerPlayTracks", args("songs" to JSONArray(songs.map(Song::json)), "at" to index, "shuffle" to shuffle, "randomStart" to shuffle))
    }

    fun clearPlaylistPicker() { pickFlow.value = null }
    fun clearRecommendAsk() { recommendFlow.value = null }
    fun confirmRecommend(song: Song) {
        recommendFlow.value = null
        send("roomOperation", args("song" to song.json(), "bizId" to "0", "operation" to "add"))
    }
    fun selectPlayback(index: Int) = send("playerSelect", args("index" to index))
    suspend fun sendSms(phone: String) = withContext(Dispatchers.IO) {
        NativeBridge.request("smsSend", args("phone" to phone))
        Unit
    }

    suspend fun smsLogin(phone: String, captcha: String) {
        val result = withContext(Dispatchers.IO) {
            NativeBridge.request("smsLogin", args("phone" to phone, "captcha" to captcha))
        }
        val session = result.optString("session")
        if (session.isBlank()) throw MusicError("登录成功但未取得会话，请重试")
        prefs.saveSession(session)
        send("restoreSession", args("session" to session))
    }

    suspend fun sendMedia(
        kind: String,
        path: String,
        mime: String,
        name: String,
        width: Int,
        height: Int,
        duration: Long,
        targetUid: String = "",
        targetRoomId: String = "",
    ): String = withContext(Dispatchers.IO) {
        val result = NativeBridge.request(
            "uploadMedia",
            args(
                "kind" to kind, "path" to path, "mime" to mime, "name" to name,
                "width" to width, "height" to height, "duration" to duration,
                "uid" to targetUid, "roomId" to targetRoomId,
                "target" to if (targetRoomId.isNotBlank()) "room" else "private",
            ),
        )
        result.optString("url").ifBlank { throw MusicError("网易云未返回附件地址") }
    }

    suspend fun connectNim(roomId: String) {
        if (roomId.isBlank()) return
        nimMessages.value = prefs.roomStickers().let { raw ->
            runCatching {
                val array = org.json.JSONArray(raw)
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { row ->
                    RoomMessage(
                        row.optString("key"), row.optString("name"), "",
                        row.optLong("time"), row.optString("url"),
                        row.optInt("width"), row.optInt("height"),
                    )
                }
            }.getOrDefault(emptyList())
        }
        val credentials = withContext(Dispatchers.IO) {
            runCatching { NativeBridge.request("nimToken", args("roomId" to roomId)) }.getOrNull()
        } ?: return
        val account = credentials.optString("account")
        val token = credentials.optString("token")
        val chatRoomId = credentials.optString("chatRoomId")
        if (account.isBlank() || token.isBlank() || chatRoomId.isBlank()) return
        NimChatroom.connect(
            account, token, chatRoomId,
            listener = { status -> nimStatusFlow.value = status },
            onMembers = { scope.launch { send("roomRefresh") } },
            onReaction = { text -> applyReaction(text) },
            onMessage = { text -> applyNimMessage(text, System.currentTimeMillis()) },
        )
        NimChatroom.loadHistory(chatRoomId) { items ->
            items.forEach { (text, time) -> applyNimMessage(text, time) }
        }
    }

    private fun applyReaction(source: String) {
        val root = runCatching { JSONObject(source) }.getOrNull() ?: return
        val pair = findReaction(root) ?: return
        val id = pair.optString("songBizId").ifBlank { pair.optString("songId") }
        val sid = pair.optString("songId")
        if (id.isBlank() || id == "0") return
        val up = pair.optInt("upCnt", -1)
        val zan = pair.optInt("zanCnt", -1)
        if (up < 0 && zan < 0) return
        reactions.value = reactions.value.toMutableMap().also { map ->
            val old = map[id] ?: 0 to 0
            val value = (if (up >= 0) up else old.first) to (if (zan >= 0) zan else old.second)
            map[id] = value
            if (sid.isNotBlank() && sid != id) map[sid] = value
            android.util.Log.d("YiqidiReaction", "id=$id up=${value.first} zan=${value.second}")
        }
    }

    private fun applyNimMessage(source: String, time: Long) {
        val url = Regex("emojiImgUrl=([^,}\\s]+)").find(source)?.groupValues?.get(1).orEmpty()
            .trim('"').let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
        val eid = Regex("emojiId=(\\d+)").find(source)?.groupValues?.get(1).orEmpty()
        if (url.isBlank() || eid.isBlank()) return
        val name = Regex("emojiName=([^,}]+)").find(source)?.groupValues?.get(1)?.trim().orEmpty()
            .ifBlank { "表情" }
        val nickname = Regex("nickname=([^,}]+)").find(source)?.groupValues?.get(1)?.trim().orEmpty()
        val uid = Regex("userId=(\\d+)").find(source)?.groupValues?.get(1).orEmpty()
        val msgId = Regex("msgId=(\\d+)").find(source)?.groupValues?.get(1).orEmpty()
        val now = time
        val width = Regex("width=(\\d+)").find(source)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val height = Regex("height=(\\d+)").find(source)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val key = "nim:${msgId.ifBlank { "$uid:$now" }}"
        if (nimMessages.value.any { it.key == key }) return
        val message = RoomMessage(key, nickname, "", now, url, width, height)
        val next = (nimMessages.value + message).takeLast(200)
        nimMessages.value = next
        prefs.saveRoomStickers(org.json.JSONArray(next.map { item ->
            args("key" to item.key, "name" to item.name, "time" to item.time, "url" to item.imageUrl, "width" to item.imageWidth, "height" to item.imageHeight)
        }).toString())
    }

    private fun findReaction(node: JSONObject): JSONObject? {
        if (node.has("upCnt") || node.has("zanCnt")) return node
        node.keys().forEach { key ->
            val value = node.opt(key) ?: return@forEach
            when (value) {
                is JSONObject -> findReaction(value)?.let { return it }
                is org.json.JSONArray -> for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item is JSONObject) findReaction(item)?.let { return it }
                    else if (item is String && item.contains("upCnt")) {
                        runCatching { JSONObject(item) }.getOrNull()?.let { findReaction(it) }?.let { return it }
                    }
                }
                is String -> if (value.contains("upCnt") || value.contains("zanCnt")) {
                    runCatching { JSONObject(value) }.getOrNull()?.let { findReaction(it) }?.let { return it }
                }
            }
        }
        return null
    }

    fun leaveNim(roomId: String) {
        NimChatroom.leaveRoom(roomId)
        nimStatusFlow.value = ""
    }

    fun disconnectNim() {
        NimChatroom.disconnect()
        nimStatusFlow.value = ""
    }

    suspend fun collectSticker(sticker: QSticker) = withContext(Dispatchers.IO) {
        NativeBridge.request("stickerCollect", args("emojiId" to sticker.id, "emojiGroupId" to sticker.group))
        Unit
    }

    suspend fun removeSticker(sticker: QSticker) = withContext(Dispatchers.IO) {
        NativeBridge.request("stickerRemove", args("emojiIds" to org.json.JSONArray(listOf(sticker.id))))
        Unit
    }

    suspend fun sendSticker(uid: String, sticker: QSticker) = withContext(Dispatchers.IO) {
        NativeBridge.request("stickerSend", args("uid" to uid, "senderUid" to accountUid(), "emoji" to sticker.json()))
        Unit
    }

    suspend fun sendRoomSticker(roomId: String, sticker: QSticker) = withContext(Dispatchers.IO) {
        NativeBridge.request("roomStickerSend", args("roomId" to roomId, "emoji" to sticker.json()))
        Unit
    }

    suspend fun joinRoomDirect(link: String): Unit = withContext(Dispatchers.IO) {
        val preview = NativeBridge.request("roomPreview", args("invite" to link))
        val status = preview.optString("status")
        if (status != "AVAILABLE") throw MusicError("房间已过期或暂不可加入")
        val uid = state.value.account?.uid ?: throw MusicError("请先登录网易云音乐")
        runCatching { NativeBridge.request("roomJoin", args("invite" to link, "uid" to uid)) }
            .getOrElse { throw MusicError("房间已过期或暂不可加入") }
        Unit
    }

    fun searchHistory(): List<String> = prefs.searchHistory()
    fun accountUid(): String = state.value.account?.uid.orEmpty()
    fun rememberSearch(term: String): List<String> = prefs.addSearch(term)
    fun clearSearchHistory() = prefs.clearSearchHistory()

    fun currentFavorite(): Boolean = player.value.song?.id?.let { it in ui.value.liked } == true
    fun toggleFavoriteCurrent() = player.value.song?.let { toggleLiked(it) }

    suspend fun playlistChoices(): List<Collection> = withContext(Dispatchers.IO) {
        val uid = state.value.account?.uid ?: throw MusicError("请先登录网易云音乐")
        NativeBridge.request("playlists", args("uid" to uid, "offset" to 0))
            .optJSONArray("items").objects(Collection::from)
    }

    suspend fun addToPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        NativeBridge.request("playlistAdd", args("playlistId" to playlistId, "id" to songId))
        Unit
    }

    suspend fun privateConversations(offset: Int = 0): JSONObject = withContext(Dispatchers.IO) {
        NativeBridge.request("privateConversations", args("offset" to offset))
    }

    suspend fun privateHistory(uid: String, before: Long = 0L): JSONObject = withContext(Dispatchers.IO) {
        NativeBridge.request("privateHistory", args("uid" to uid, "before" to before))
    }

    suspend fun sendPrivate(uid: String, text: String): JSONObject = withContext(Dispatchers.IO) {
        NativeBridge.request("privateSend", args("uid" to uid, "text" to text))
    }
}

internal class AudioControls(private val store: MusicStore) {
    val roomMode: Boolean get() = store.audioState.value.roomMode
    fun toggle() = store.send("playerToggle")
    fun previous() = store.send("playerPrevious")
    fun next() = store.send("playerNext")
    fun seek(position: Long) = store.send("playerSeek", args("position" to position))
    fun shuffle() = store.send("playerShuffleToggle")
    fun repeat() = store.send("playerRepeatCycle")
    fun enqueue(song: Song, next: Boolean) = store.send("playerEnqueue", args("song" to song.json(), "next" to next))
    fun remove(index: Int) = store.send("playerRemove", args("index" to index))
}