@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MiniPlayer(store: MusicStore, ui: MusicUiState, player: PlayerState, onOpen: () -> Unit, onQueue: () -> Unit) {
    val position by store.playerPosition.collectAsState()
    val song = player.song ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, onClick = onOpen) {
        Row(Modifier.fillMaxWidth().height(70.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song.cover, Modifier.size(48.dp), shape = MaterialTheme.shapes.medium)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(song.artistLine, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator({ fraction(position, player.duration) }, Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            IconButton(onClick = store.audio::toggle, enabled = !player.buffering) { if (player.buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "暂停" else "播放") }
            IconButton(onClick = onQueue) { Icon(Icons.Rounded.QueueMusic, "播放队列") }
        }
    }
}

@Composable
internal fun SidePlayer(store: MusicStore, ui: MusicUiState, player: PlayerState, modifier: Modifier, onOpen: () -> Unit, onQueue: () -> Unit) {
    val song = player.song
    val position by store.playerPosition.collectAsState()
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        if (song == null) {
            EmptyNotice(Icons.Rounded.Headphones, "还没有正在播放的歌曲", Modifier.fillMaxSize())
        } else Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("正在播放", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconAction(Icons.Rounded.QueueMusic, "播放队列", onClick = onQueue)
                IconAction(Icons.Rounded.OpenInFull, "展开播放器", onClick = onOpen)
            }
            Cover(song.cover, Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge), shape = MaterialTheme.shapes.extraLarge)
            Text(song.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(fraction(position, player.duration), { store.audio.seek((it * player.duration).toLong()) }, enabled = !store.audio.roomMode)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clockLabel(position), style = MaterialTheme.typography.labelSmall)
                Text(song.format.ifBlank { "网易云音乐" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(clockLabel(player.duration), style = MaterialTheme.typography.labelSmall)
            }
            Transport(store, player, compact = true)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconToggleButton(player.shuffle, { store.audio.shuffle() }, enabled = !player.roomMode) { Icon(Icons.Rounded.Shuffle, "随机播放") }
                IconAction(if (player.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "切换循环模式", enabled = !player.roomMode, onClick = store.audio::repeat)
                IconAction(Icons.Rounded.QueueMusic, "播放队列", onClick = onQueue)
            }
        }
    }
}

@Composable
internal fun NowPlaying(store: MusicStore, ui: MusicUiState, player: PlayerState, wide: Boolean, lyricsMode: Boolean, onToggleLyrics: () -> Unit, onClose: () -> Unit, onPanel: (Panel) -> Unit, modifier: Modifier = Modifier) {
    val song = player.song
    val position by store.playerPosition.collectAsState()
    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Rounded.KeyboardArrowDown, "收起播放器", onClick = onClose)
            Text(if (lyricsMode) "歌词" else "正在播放", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconAction(Icons.Rounded.QueueMusic, "播放队列", onClick = { onPanel(Panel.QUEUE) })
            IconAction(Icons.Rounded.MoreVert, "曲目操作", onClick = { song?.let { onPanel(Panel.TRACK) } }, enabled = song != null)
        }
        if (song == null) {
            EmptyNotice(Icons.Rounded.MusicNote, "还没有正在播放的歌曲", Modifier.weight(1f))
        } else if (lyricsMode) {
            LyricPane(song, ui, position, wide, onToggleLyrics, store, { onPanel(Panel.LYRIC_SETTINGS) })
        } else if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(song.cover, Modifier.weight(1f).fillMaxHeight().aspectRatio(1f), shape = MaterialTheme.shapes.extraLarge, iconSize = 54.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrackIdentity(song, ui, onToggleLyrics) { store.toggleLiked(song) }
                    Spacer(Modifier.height(8.dp))
                    PlaybackControls(store, player, position, onPanel)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                Cover(song.cover, Modifier.fillMaxWidth().weight(1f).aspectRatio(1f), shape = MaterialTheme.shapes.extraLarge, iconSize = 48.dp)
                TrackIdentity(song, ui, onToggleLyrics) { store.toggleLiked(song) }
                PlaybackControls(store, player, position, onPanel)
            }
        }
    }
}

@Composable
private fun TrackIdentity(song: Song, ui: MusicUiState, onToggleLyrics: () -> Unit, onLike: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.album.isNotBlank()) Text(song.album, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalIconButton(onClick = onToggleLyrics) { Icon(Icons.Rounded.Lyrics, "查看歌词") }
        IconAction(if (song.id in ui.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (song.id in ui.liked) "取消收藏" else "收藏歌曲", onClick = onLike)
    }
}

@Composable
private fun PlaybackControls(store: MusicStore, player: PlayerState, position: Long, onPanel: (Panel) -> Unit) {
    val song = player.song ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Slider(fraction(position, player.duration), { store.audio.seek((it * player.duration).toLong()) }, enabled = !store.audio.roomMode)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(clockLabel(position), style = MaterialTheme.typography.labelMedium)
            Text(listOfNotNull(song.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" }, song.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }, song.format.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "在线音源" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(clockLabel(player.duration), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Transport(store, player, compact = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FilledIconToggleButton(player.shuffle, { store.audio.shuffle() }, enabled = !store.audio.roomMode) { Icon(Icons.Rounded.Shuffle, "随机播放") }
            IconAction(if (player.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "切换循环模式", enabled = !store.audio.roomMode, onClick = store.audio::repeat)
            IconAction(Icons.Rounded.QueueMusic, "播放队列", onClick = { onPanel(Panel.QUEUE) })
        }
    }
}

@Composable
private fun Transport(store: MusicStore, player: PlayerState, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = store.audio::previous, enabled = player.previous && !store.audio.roomMode) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(if (compact) 30.dp else 38.dp)) }
        FilledIconButton(onClick = store.audio::toggle, enabled = !player.buffering, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(if (compact) 58.dp else 72.dp)) {
            if (player.buffering) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) else Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "暂停" else "播放", Modifier.size(if (compact) 30.dp else 38.dp))
        }
        IconButton(onClick = store.audio::next, enabled = player.next && !store.audio.roomMode) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(if (compact) 30.dp else 38.dp)) }
    }
}

@Composable
private fun LyricPane(song: Song, ui: MusicUiState, position: Long, wide: Boolean, toggle: () -> Unit, store: MusicStore, onSettings: () -> Unit) {
    val prefs = ui.lyricPrefs
    val active by store.activeLyric.collectAsState()
    val lyricList = rememberLazyListState()
    LaunchedEffect(active, ui.lyrics) {
        if (active in ui.lyrics.indices) lyricList.animateScrollToItem((active - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song.cover, Modifier.size(46.dp), shape = MaterialTheme.shapes.small)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconAction(Icons.Rounded.Tune, "歌词设置", onClick = onSettings)
        }
        if (ui.lyricsLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Busy("读取在线歌词") }
        } else if (ui.lyrics.isEmpty()) {
            EmptyNotice(Icons.Rounded.Lyrics, "暂时没有歌词", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lyricList, contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.Start) {
                itemsIndexed(ui.lyrics, key = { index, line -> "${line.at}:$index" }) { index, line ->
                    val current = index == active
                    Text(line.text.ifBlank { line.translation }, Modifier.fillMaxWidth(), style = if (current) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active >= 0 && kotlin.math.abs(index - active) > 2) 0.45f else 0.8f),
                        textAlign = if (prefs.align == 1) TextAlign.Center else TextAlign.Start)
                    if (current && line.translation.isNotBlank() && line.translation != line.text) Text(line.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Slider(fraction(position, playerDuration(ui, song)), { store.audio.seek((it * playerDuration(ui, song)).toLong()) }, enabled = !store.audio.roomMode)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${ui.lyricPrefs.shift} ms", style = MaterialTheme.typography.labelSmall)
            ChoiceRow(listOf(false to "同步", true to "静态"), ui.lyricMode, store::setLyricMode, Modifier.widthIn(max = if (wide) 280.dp else 220.dp))
            IconAction(Icons.Rounded.MoreVert, "歌词操作", onClick = onSettings)
        }
    }
}

private fun fraction(position: Long, duration: Long): Float = if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
private fun playerDuration(ui: MusicUiState, song: Song): Long = song.duration.takeIf { it > 0 } ?: ui.lyrics.lastOrNull()?.at ?: 1L
