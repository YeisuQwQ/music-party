@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun QFullPlayer(store: MusicStore, ui: MusicUiState, player: PlayerState, wide: Boolean, onClose: () -> Unit) {
    val song = player.song ?: return
    val position by store.playerPosition.collectAsStateWithLifecycle()
    val activeLine by store.activeLyric.collectAsStateWithLifecycle()
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val dismissThreshold = with(LocalDensity.current) { 132.dp.toPx() }
    val backdrop = if (Build.VERSION.SDK_INT >= 31) {
        Modifier.fillMaxSize().blur(70.dp)
    } else {
        Modifier.fillMaxSize()
    }
    Surface(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = dragY }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragY = (dragY + dragAmount).coerceIn(0f, size.height.toFloat())
                    },
                    onDragEnd = {
                        val start = dragY
                        val closing = start >= dismissThreshold
                        scope.launch {
                            if (closing) {
                                animate(start, size.height.toFloat(), animationSpec = tween(180)) { value, _ -> dragY = value }
                                onClose()
                            } else {
                                animate(start, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> dragY = value }
                            }
                        }
                    },
                    onDragCancel = {
                        val start = dragY
                        scope.launch {
                            animate(start, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> dragY = value }
                        }
                    },
                )
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (song.cover.isNotBlank()) {
                AsyncImage(
                    model = song.cover,
                    contentDescription = null,
                    modifier = backdrop.alpha(0.48f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
            )
            Box(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)),
            )
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "收起播放器") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { store.ui("uiToggleQueue") }) { Icon(Icons.Rounded.QueueMusic, "播放队列") }
                    IconButton(onClick = {
                        store.ui("uiTrack", args("id" to song.id, "at" to player.at, "song" to song.json()))
                    }) { Icon(Icons.Rounded.MoreVert, "更多") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val sideBySide = wide && maxWidth >= 720.dp
                    if (sideBySide) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(46.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(song.cover, Modifier.weight(0.85f).aspectRatio(1f), shape = RoundedCornerShape(34.dp), iconSize = 64.dp)
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                                QPlayerMeta(store, ui, song, player)
                                Spacer(Modifier.height(18.dp))
                                QPlayerSlider(store, player, position)
                                Spacer(Modifier.height(10.dp))
                                QTransport(store, player, compact = false)
                                Spacer(Modifier.height(16.dp))
                                QLyricsPreview(ui, activeLine, maxHeight = 180.dp)
                            }
                        }
                    } else {
                        val targetCover = minOf(maxWidth * 0.56f, maxHeight * 0.38f)
                        val coverSize by animateDpAsState(
                            targetValue = targetCover,
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            label = "coverZoom",
                        )
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Cover(
                                song.cover,
                                Modifier.size(coverSize),
                                shape = RoundedCornerShape(30.dp),
                                iconSize = 48.dp,
                            )
                            QPlayerMeta(store, ui, song, player, centered = true)
                            QPlayerSlider(store, player, position)
                            QTransport(store, player, compact = true)
                            QLyricsPreview(ui, activeLine, maxHeight = 48.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QPlayerMeta(store: MusicStore, ui: MusicUiState, song: Song, player: PlayerState, centered: Boolean = false) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            song.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            song.artistLine,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (song.album.isNotBlank()) {
            Text(
                song.album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { store.toggleLiked(song) },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    if (song.id in ui.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (song.id in ui.liked) "取消收藏" else "收藏",
                    tint = if (song.id in ui.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (song.id in ui.liked) "已收藏" else "收藏",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QPlayerSlider(store: MusicStore, player: PlayerState, position: Long) {
    val duration = player.duration.coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Slider(
            value = (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { store.audio.seek((it * duration).toLong()) },
            enabled = !store.audio.roomMode,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clockLabel(position), style = MaterialTheme.typography.labelSmall)
            Text(clockLabel(player.duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QTransport(store: MusicStore, player: PlayerState, compact: Boolean) {
    val repeatLabel = when (player.repeat) {
        androidx.media3.common.Player.REPEAT_MODE_ONE -> "单曲循环"
        androidx.media3.common.Player.REPEAT_MODE_ALL -> "列表循环"
        else -> "顺序播放"
    }
    val repeatOn = player.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QModeControl(
            icon = Icons.Rounded.Shuffle,
            label = if (player.shuffle) "随机已开" else "随机播放",
            active = player.shuffle,
            enabled = !player.roomMode,
            onClick = store.audio::shuffle,
        )
        IconButton(onClick = store.audio::previous, enabled = player.previous && !player.roomMode, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(if (compact) 28.dp else 34.dp))
        }
        FilledIconButton(
            onClick = store.audio::toggle,
            modifier = Modifier.size(if (compact) 62.dp else 74.dp),
        ) {
            if (player.buffering) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(
                if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (player.playing) "暂停" else "播放",
                Modifier.size(if (compact) 30.dp else 38.dp),
            )
        }
        IconButton(onClick = store.audio::next, enabled = player.next && !player.roomMode, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(if (compact) 28.dp else 34.dp))
        }
        QModeControl(
            icon = if (player.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = repeatLabel,
            active = repeatOn,
            enabled = !player.roomMode,
            onClick = store.audio::repeat,
        )
    }
}

@Composable
private fun QModeControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container.copy(alpha = 0.45f),
                disabledContentColor = content.copy(alpha = 0.45f),
            ),
        ) {
            Icon(icon, label, Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun QLyricsPreview(ui: MusicUiState, activeLine: Int, maxHeight: androidx.compose.ui.unit.Dp) {
    val lines = ui.lyrics
    if (lines.isEmpty()) {
        Text(
            if (ui.lyricsLoading) "正在读取歌词…" else "暂无歌词",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(activeLine, lines.size) {
        if (activeLine in lines.indices) state.animateScrollToItem((activeLine - 1).coerceAtLeast(0))
    }
    LazyColumn(
        Modifier.fillMaxWidth().height(maxHeight),
        state = state,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lines, key = { index, line -> "${line.at}:$index" }) { index, line ->
            Text(
                line.text.ifBlank { "· · ·" },
                style = if (index == activeLine) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (index == activeLine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
