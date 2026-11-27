@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CollectionContent(store: MusicStore, ui: MusicUiState, player: PlayerState, pickTrack: (TrackChoice) -> Unit) {
    val collection = ui.collection ?: return
    key(collection.kind, collection.id) {
        TrackPage(ui.collectionPage, store, ui, player, pickTrack, "没有可显示的歌曲", store::loadMoreCollection,
            detail = collection.detail)
    }
}

@Composable
internal fun TrackPage(
    page: CatalogPage<Song>, store: MusicStore, ui: MusicUiState, player: PlayerState, pickTrack: (TrackChoice) -> Unit,
    empty: String, onMore: () -> Unit, onRetry: () -> Unit = onMore, onRefresh: (() -> Unit)? = null, detail: String = "",
) {
    val songs = page.items
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (detail.isNotBlank()) item { Text(detail, Modifier.padding(start = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.bodyMedium) }
        if ((songs.isNotEmpty() && ui.room == null) || onRefresh != null) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    if (songs.isNotEmpty() && ui.room == null) {
                        PlayGroup(true, { store.playTracks(songs, 0) }, { store.playTracks(songs, 0, true) }, Icons.Rounded.Shuffle,
                            if (page.more || page.error.isNotBlank()) "播放已加载" else "播放全部")
                    }
                }
                if (onRefresh != null) IconAction(Icons.Rounded.Refresh, "刷新喜欢的音乐", !page.loading, onRefresh)
            }
        }
        if (page.ready) item { CatalogCount(page, "首歌曲") }
        if (songs.isEmpty() && !page.loading && page.error.isEmpty() && !page.more) item { EmptyNotice(Icons.Rounded.LibraryMusic, empty) }
        trackItems(songs, store, ui, player, pickTrack)
        if (page.loading || page.error.isNotBlank() || page.more) item {
            PageFooter(page.loading, page.error, page.more, if (page.error.isBlank()) onMore else onRetry)
        }
    }
}

@Composable
internal fun PageFooter(loading: Boolean, error: String, more: Boolean, onAction: () -> Unit) {
    when {
        loading -> Busy("加载中")
        error.isNotBlank() -> Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onAction) { Text("重试") }
        }
        more -> TextButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
    }
}

internal fun LazyListScope.trackItems(songs: List<Song>, store: MusicStore, ui: MusicUiState, player: PlayerState, pickTrack: (TrackChoice) -> Unit) {
    itemsIndexed(songs, key = { index, song -> "track:$index:${song.id}" }) { index, song ->
        val inRoom = ui.room != null
        val menu = { pickTrack(TrackChoice(song, songs)) }
        val liked = song.id in ui.liked
        TrackRow(song, index, songs.size, selected = player.song?.id == song.id,
            onClick = { if (inRoom) menu() else store.playTracks(songs, index) }, onMenu = menu,
            subtitle = listOf(song.artistLine, song.album).filter(String::isNotBlank).joinToString(" · "),
            trailing = {
                if (song.duration > 0) Text(song.durationLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (inRoom) IconAction(Icons.Rounded.Add, "推荐到房间") { store.roomOperation(song, "0", "add") }
                else if (ui.account != null) IconAction(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (liked) "取消收藏" else "收藏", ui.likesReady && ui.favoriteBusy.isEmpty()) { store.toggleLiked(song) }
                IconAction(Icons.Rounded.MoreVert, "歌曲操作", onClick = menu)
            },
        )
    }
}