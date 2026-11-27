package dev.yiqidi.mobile

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
internal class AndroidAudio(private val player: ExoPlayer, private val send: (String, JSONObject) -> Unit) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var mediaId: String? = null
    private var token = 0L
    private var ended = false
    private val report = Runnable { observe() }
    private val tick = object : Runnable {
        override fun run() {
            if (matches() && player.isPlaying) send("audioPosition", args("token" to token, "position" to player.currentPosition))
            handler.postDelayed(this, 250)
        }
    }

    init {
        player.addListener(this)
        handler.post(tick)
    }

    fun apply(effect: JSONObject) {
        val op = effect.getString("op")
        token = effect.getLong("token")
        when (op) {
            "load" -> {
                val song = Song.from(effect.getJSONObject("song"))
                val id = "yiqidi:$token"
                mediaId = id
                ended = false
                val metadata = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artistLine).setAlbumTitle(song.album)
                if (song.cover.isNotEmpty()) metadata.setArtworkUri(Uri.parse(song.cover))
                val item = MediaItem.Builder().setMediaId(id).setUri(song.uri(effect.optBoolean("room"))).setMediaMetadata(metadata.build()).build()
                player.setMediaItem(item, effect.optLong("position"))
                player.playWhenReady = effect.optBoolean("play")
                player.prepare()
            }
            "clear" -> {
                mediaId = null
                ended = false
                player.pause()
                player.clearMediaItems()
            }
            "pause" -> player.pause()
            "play" -> if (matches()) {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
            "seek", "align" -> if (matches()) {
                ended = false
                player.seekTo(effect.optLong("position"))
                if (op == "align") {
                    if (effect.optBoolean("play") && player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.playWhenReady = effect.optBoolean("play")
                }
            }
        }
        scheduleObservation()
    }

    private fun matches() = mediaId != null && player.currentMediaItem?.mediaId == mediaId

    override fun onEvents(player: Player, events: Player.Events) = scheduleObservation()

    private fun scheduleObservation() {
        handler.removeCallbacks(report)
        handler.post(report)
    }

    private fun observe() {
        if (!matches()) return
        val phase = when (player.playbackState) {
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> "ready"
            Player.STATE_ENDED -> if (ended) "idle" else "ended"
            else -> "idle"
        }
        if (phase == "ended") ended = true
        send("audioObserved", args("token" to token, "phase" to phase, "position" to player.currentPosition,
            "duration" to player.duration.coerceAtLeast(0), "buffering" to (player.playbackState == Player.STATE_BUFFERING),
            "active" to (player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playerError == null), "playing" to player.isPlaying))
    }

    override fun onPlayerError(error: PlaybackException) {
        val failedId = mediaId
        val failedToken = token
        val message = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<StreamFailure>().firstOrNull()?.message
            ?: "无法播放此歌曲，请检查网络或音源"
        handler.post {
            if (failedId == mediaId && failedToken == token) send("audioError", args("token" to failedToken, "message" to message))
        }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(this)
    }
}

private class StreamFailure(message: String) : IOException(message)

private const val FAVORITE_ACTION = "dev.yiqidi.mobile.action.FAVORITE"
private val FAVORITE_COMMAND = SessionCommand(FAVORITE_ACTION, Bundle.EMPTY)

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var audio: AndroidAudio
    private lateinit var transport: SessionPlayer
    private lateinit var session: MediaSession
    private val store get() = (application as YiqidiApplication).music

    private fun favoriteButton(): CommandButton {
        val liked = store.currentFavorite()
        return CommandButton.Builder(
            if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setSessionCommand(FAVORITE_COMMAND)
            .setDisplayName(if (liked) "取消收藏" else "收藏")
            .build()
    }

    private fun updateFavoriteButton() {
        if (::session.isInitialized) session.setCustomLayout(listOf(favoriteButton()))
    }

    override fun onCreate() {
        super.onCreate()
        val factory = DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(12_000)
        val resolver = ResolvingDataSource.Factory(factory) { spec ->
            if (spec.uri.scheme != "yiqidi" || spec.uri.authority != "netease") throw StreamFailure("只支持网易云在线音源")
            val reply = try {
                NativeBridge.evaluate("stream", args("id" to spec.uri.lastPathSegment.orEmpty(), "room" to (spec.uri.getQueryParameter("room") == "1")))
            } catch (error: MusicError) {
                throw StreamFailure(error.message.orEmpty())
            }
            spec.withUri(Uri.parse(reply.getString("url")))
        }
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(resolver))
            .setAudioAttributes(AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        transport = SessionPlayer(player, store)
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, transport)
            .setSessionActivity(launch)
            .setCustomLayout(listOf(favoriteButton()))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val base = super.onConnect(session, controller)
                    val commands = base.availableSessionCommands.buildUpon().add(FAVORITE_COMMAND).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setAvailablePlayerCommands(base.availablePlayerCommands)
                        .setCustomLayout(listOf(favoriteButton()))
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == FAVORITE_ACTION) {
                        store.toggleFavoriteCurrent()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
            })
            .build()
        audio = AndroidAudio(player, store::send)
        store.mediaStateChanged = {
            transport.refresh()
            updateFavoriteButton()
        }
        store.attach(audio)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        store.mediaStateChanged = null
        session.release()
        audio.close()
        store.detach(audio)
        player.release()
        super.onDestroy()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class SessionPlayer(player: ExoPlayer, private val store: MusicStore) : ForwardingSimpleBasePlayer(player) {
    fun refresh() = invalidateState()

    override fun getState(): State {
        val base = super.getState()
        val snapshot = store.audioState.value
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES, Player.COMMAND_RELEASE,
        ).addIf(Player.COMMAND_PLAY_PAUSE, snapshot.song != null)
            .addIf(Player.COMMAND_STOP, snapshot.song != null)
            .addIf(Player.COMMAND_SEEK_TO_NEXT, snapshot.next)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, snapshot.next)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, snapshot.previous)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, snapshot.previous)
            .addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, !snapshot.roomMode && snapshot.duration > 0)
            .addIf(Player.COMMAND_SET_REPEAT_MODE, !snapshot.roomMode && snapshot.song != null)
            .addIf(Player.COMMAND_SET_SHUFFLE_MODE, !snapshot.roomMode && snapshot.song != null)
            .build()
        return base.buildUpon().setAvailableCommands(commands).setRepeatMode(snapshot.repeat).setShuffleModeEnabled(snapshot.shuffle).build()
    }

    private fun command(name: String, data: JSONObject = JSONObject()): ListenableFuture<*> {
        store.send(name, data)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) = command("playerTransport", args("action" to if (playWhenReady) "play" else "pause"))
    override fun handleStop() = command("playerTransport", args("action" to "pause"))
    override fun handleSetRepeatMode(repeatMode: Int) = command("playerRepeat", args("mode" to repeatMode))
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean) = command("playerShuffle", args("enabled" to shuffleModeEnabled))
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = when (seekCommand) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> command("playerNext")
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> command("playerPrevious")
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> command("playerSeek", args("position" to positionMs.coerceAtLeast(0)))
        else -> Futures.immediateVoidFuture()
    }
}