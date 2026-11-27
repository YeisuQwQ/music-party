package dev.yiqidi.mobile

import android.content.Context
import android.util.Log
import com.netease.nimlib.sdk.NIMClient
import com.netease.nimlib.sdk.RequestCallback
import com.netease.nimlib.sdk.auth.AuthService
import com.netease.nimlib.sdk.auth.LoginInfo
import com.netease.nimlib.sdk.chatroom.ChatRoomService
import com.netease.nimlib.sdk.chatroom.ChatRoomServiceObserver
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomData
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomResultData

object NimChatroom {
    private const val TAG = "YiqidiNIM"
    private var initialised = false
    private var loggedAccount = ""
    private var joinedRoom = ""
    var onStatus: ((String) -> Unit)? = null
        private set
    var onMemberUpdate: ((String) -> Unit)? = null
        private set
    var onReaction: ((String) -> Unit)? = null
        private set
    var onMessage: ((String) -> Unit)? = null
        private set
    private var observing = false

    fun init(context: Context) {
        if (initialised) return
        runCatching { NIMClient.init(context, null, null) }
            .onSuccess { initialised = true }
            .onFailure { Log.w(TAG, "init failed", it) }
    }

    fun connect(account: String, token: String, chatRoomId: String, listener: ((String) -> Unit)? = null, onMembers: ((String) -> Unit)? = null, onReaction: ((String) -> Unit)? = null, onMessage: ((String) -> Unit)? = null) {
        if (!initialised || account.isBlank() || token.isBlank() || chatRoomId.isBlank()) return
        onStatus = listener
        onMemberUpdate = onMembers
        this.onReaction = onReaction
        this.onMessage = onMessage
        observeReceive()
        if (joinedRoom == chatRoomId && loggedAccount == account) {
            listener?.invoke("connected")
            return
        }
        joinedRoom = chatRoomId
        if (loggedAccount == account && NIMClient.getCurrentAccount() == account) {
            enterRoom(chatRoomId)
            return
        }
        val future = runCatching { NIMClient.getService(AuthService::class.java).login(LoginInfo(account, token)) }.getOrNull() ?: return
        future.setCallback(object : RequestCallback<LoginInfo> {
            override fun onSuccess(result: LoginInfo?) {
                loggedAccount = account
                enterRoom(chatRoomId)
            }

            override fun onFailed(code: Int) {
                Log.w(TAG, "login failed $code")
                listener?.invoke("login-failed:$code")
            }

            override fun onException(exception: Throwable?) {
                Log.w(TAG, "login exception", exception)
                listener?.invoke("login-error")
            }
        })
    }

    private fun enterRoom(chatRoomId: String) {
        val data = EnterChatRoomData(chatRoomId).apply {
            nick = "一起迪"
            avatar = " "
        }
        val future = runCatching { NIMClient.getService(ChatRoomService::class.java).enterChatRoom(data) }.getOrNull() ?: return
        future.setCallback(object : RequestCallback<EnterChatRoomResultData> {
            override fun onSuccess(result: EnterChatRoomResultData?) {
                Log.d(TAG, "connected $chatRoomId")
                joinedRoom = chatRoomId
                onStatus?.invoke("connected")
            }

            override fun onFailed(code: Int) {
                Log.w(TAG, "enter room failed $code")
                joinedRoom = ""
                onStatus?.invoke("room-failed:$code")
            }

            override fun onException(exception: Throwable?) {
                Log.w(TAG, "enter room exception", exception)
                joinedRoom = ""
                onStatus?.invoke("room-error")
            }
        })
    }

    fun loadHistory(chatRoomId: String, onMessages: (List<Pair<String, Long>>) -> Unit) {
        if (!initialised || chatRoomId.isBlank()) return
        runCatching {
            NIMClient.getService(ChatRoomService::class.java)
                .pullMessageHistory(chatRoomId, System.currentTimeMillis(), 100)
                .setCallback(object : RequestCallback<List<ChatRoomMessage>> {
                    override fun onSuccess(messages: List<ChatRoomMessage>?) {
                        val items = messages.orEmpty().mapNotNull { message ->
                            val text = listOfNotNull(message.remoteExtension?.toString(), message.content).joinToString(" ")
                            if (text.contains("emojiId=") && !text.contains("升级App到最新版本即可查看该消息")) text to message.time else null
                        }
                        onMessages(items.sortedBy { it.second })
                    }
                    override fun onFailed(code: Int) { Log.w(TAG, "history failed $code") }
                    override fun onException(exception: Throwable) { Log.w(TAG, "history exception", exception) }
                })
        }.onFailure { Log.w(TAG, "history error", it) }
    }

    private fun observeReceive() {
        if (observing) return
        observing = true
        runCatching {
            NIMClient.getService(ChatRoomServiceObserver::class.java).observeReceiveMessage({ messages: List<ChatRoomMessage> ->
                messages.forEach { message ->
                    val text = listOfNotNull(message.remoteExtension?.toString(), message.content).joinToString(" ")
                    if (text.contains("emojiId=") && !text.contains("升级App到最新版本即可查看该消息")) onMessage?.invoke(text)
                    if (text.contains("30008")) {
                        Log.d(TAG, "reaction $text")
                        onReaction?.invoke(text)
                    }
                    else if (text.contains("30005")) onMemberUpdate?.invoke(text)
                }
            }, true)
        }.onFailure { Log.w(TAG, "observe failed", it) }
    }

    fun leaveRoom(chatRoomId: String) {
        if (chatRoomId.isBlank()) return
        runCatching { NIMClient.getService(ChatRoomService::class.java).exitChatRoom(chatRoomId) }
        if (joinedRoom == chatRoomId) joinedRoom = ""
    }

    fun disconnect() {
        if (joinedRoom.isNotBlank()) leaveRoom(joinedRoom)
        runCatching { NIMClient.getService(AuthService::class.java).logout() }
        loggedAccount = ""
        joinedRoom = ""
    }
}