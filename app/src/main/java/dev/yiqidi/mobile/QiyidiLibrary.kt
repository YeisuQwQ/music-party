@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun QLibraryPage(store: MusicStore, ui: MusicUiState, player: PlayerState, wide: Boolean) {
    val kind = ui.libraryKind
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
        item(key = "hero") {
            QHomeHero(
                ui = ui,
                player = player,
                wide = wide,
                onListen = { store.navigate(Destination.ROOMS) },
                onSearch = { store.navigate(Destination.SEARCH) },
                onLibrary = { store.selectKind(LibraryKind.LIKED) },
            )
        }
        item(key = "quick") { QQuickActions(store, ui) }
        item(key = "tabs") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QSectionTitle("我的音乐", "已从网易云音乐同步")
                QLibraryTabs(store, kind)
            }
        }
        when (kind) {
            LibraryKind.LIKED -> {
                item(key = "liked-head") { QLikedToolbar(store, ui) }
                if (ui.likedPage.items.isEmpty() && !ui.likedPage.ready) {
                    item(key = "liked-loading") { QLoadingList(if (wide) 8 else 6) }
                } else if (ui.likedPage.items.isEmpty()) {
                    item(key = "liked-empty") {
                        QEmptyState(
                            Icons.Rounded.Favorite,
                            "还没有喜欢的音乐",
                            "在网易云音乐里点亮红心，这里会自动同步。",
                        )
                    }
                } else {
                    itemsIndexed(ui.likedPage.items, key = { _, song -> song.id }) { index, song ->
                        QTrackRow(
                            song = song,
                            index = index,
                            playing = player.song?.id == song.id,
                            liked = song.id in ui.liked,
                            onClick = { store.playTracks(ui.likedPage.items, index) },
                            onMore = { store.ui("uiTrack", args("id" to song.id, "at" to index, "song" to song.json())) },
                            onLike = { store.toggleLiked(song) },
                        )
                    }
                    if (ui.likedPage.more) {
                        item(key = "liked-more") {
                            TextButton(onClick = { store.ui("uiMoreLiked") }, modifier = Modifier.fillMaxWidth()) {
                                Text("加载更多 · ${ui.likedPage.loaded}${ui.likedPage.total?.let { " / $it" } ?: ""}")
                            }
                        }
                    }
                }
            }
            LibraryKind.PLAYLISTS -> {
                item(key = "playlist-tools") { QCollectionTools(store, ui, kind) }
                item(key = "playlist-grid") {
                    QCollectionGrid(
                        items = ui.playlistsPage.items,
                        columns = if (wide) 4 else 2,
                        onOpen = store::openCollection,
                    )
                }
                if (ui.playlistsPage.loading) item(key = "playlist-loading") { QLoadingList(4) }
                if (ui.playlistsPage.error.isNotBlank()) item(key = "playlist-error") {
                    QEmptyState(Icons.Rounded.Refresh, "歌单加载失败", ui.playlistsPage.error, actionLabel = "重试", onAction = { store.refreshLibrary() })
                }
            }
            LibraryKind.ALBUMS -> {
                item(key = "album-tools") { QCollectionTools(store, ui, kind) }
                item(key = "album-grid") {
                    QCollectionGrid(
                        items = ui.albumsPage.items,
                        columns = if (wide) 4 else 2,
                        onOpen = store::openCollection,
                    )
                }
                if (ui.albumsPage.loading) item(key = "album-loading") { QLoadingList(4) }
                if (ui.albumsPage.error.isNotBlank()) item(key = "album-error") {
                    QEmptyState(Icons.Rounded.Refresh, "专辑加载失败", ui.albumsPage.error, actionLabel = "重试", onAction = { store.refreshLibrary() })
                }
            }
        }
    }
}

@Composable
private fun QHomeHero(ui: MusicUiState, player: PlayerState, wide: Boolean, onListen: () -> Unit, onSearch: () -> Unit, onLibrary: () -> Unit) {
    val name = ui.account?.name?.takeIf { it.isNotBlank() } ?: "音乐同好"
    val greeting = qGreeting()
    val liked = ui.likedPage.total ?: ui.likedPage.loaded
    val playlists = ui.playlistsPage.total ?: ui.playlistsPage.loaded
    val albums = ui.albumsPage.total ?: ui.albumsPage.loaded
    val gradient = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(gradient)),
    ) {
        Box(Modifier.size(230.dp).align(Alignment.TopEnd).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.06f), CircleShape))
        Box(Modifier.size(140.dp).align(Alignment.BottomStart).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.06f), CircleShape))
        Icon(
            Icons.Rounded.Headphones,
            null,
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp).size(if (wide) 118.dp else 92.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.13f),
        )
        Column(
            Modifier.fillMaxWidth().padding(if (wide) 28.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                QTag(if (player.roomMode) "一起听中" else "实时同步", icon = Icons.Rounded.GraphicEq)
            }
            Text(
                "$greeting，$name",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "把喜欢的歌，\n听成我们的故事",
                style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "和同频的人一起听歌 · 房间进度实时同步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onListen, shape = CircleShape) {
                    Icon(Icons.Rounded.Groups, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("一起听")
                }
                FilledTonalButton(onClick = onSearch, shape = CircleShape) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("发现音乐")
                }
                if (ui.account == null) {
                    TextButton(onClick = onLibrary) { Text("先登录") }
                }
            }
            val stats = buildList {
                if (liked > 0) add(compactCount(liked) to "喜欢")
                if (playlists > 0) add(compactCount(playlists) to "歌单")
                if (albums > 0) add(compactCount(albums) to "专辑")
            }
            if (stats.isEmpty()) {
                Text(
                    if (ui.account == null) "登录后同步你的网易云收藏" else "正在同步你的网易云收藏…",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    stats.forEach { (value, label) -> QStat(value, label) }
                }
            }
        }
    }
}

@Composable
private fun QQuickActions(store: MusicStore, ui: MusicUiState) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 1.dp),
    ) {
        item {
            QActionTile(Icons.Rounded.Groups, "一起听", "好友房 / 匹配", onClick = { store.navigate(Destination.ROOMS) }, modifier = Modifier.width(132.dp))
        }
        item {
            QActionTile(Icons.Rounded.Search, "搜索", "歌曲 / 歌单 / 歌手", onClick = { store.navigate(Destination.SEARCH) }, modifier = Modifier.width(132.dp))
        }
        item {
            QActionTile(Icons.Rounded.Favorite, "我喜欢的", "${ui.likedPage.total ?: ui.likedPage.loaded} 首", onClick = { store.selectKind(LibraryKind.LIKED) }, modifier = Modifier.width(132.dp))
        }
        item {
            QActionTile(Icons.Rounded.Album, "收藏专辑", "${ui.albumsPage.total ?: ui.albumsPage.loaded} 张", onClick = { store.selectKind(LibraryKind.ALBUMS) }, modifier = Modifier.width(132.dp))
        }
    }
}

@Composable
private fun QLibraryTabs(store: MusicStore, kind: LibraryKind) {
    QChoicePills(
        options = listOf(
            "LIKED" to "我喜欢的音乐",
            "PLAYLISTS" to "我的歌单",
            "ALBUMS" to "收藏的专辑",
        ),
        selected = kind.name,
        onSelect = { store.ui("uiLibraryKind", args("name" to it)) },
    )
}

@Composable
private fun QLikedToolbar(store: MusicStore, ui: MusicUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("我喜欢的音乐", style = MaterialTheme.typography.titleLarge)
            Text(
                if (ui.likedPage.total != null) "已加载 ${ui.likedPage.loaded} / ${ui.likedPage.total} 首" else "正在同步网易云红心",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = { store.ui("uiPlayAll", args("shuffle" to true)) }, enabled = ui.likedPage.items.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, "随机播放")
        }
        Button(onClick = { store.ui("uiPlayAll", args("shuffle" to false)) }, enabled = ui.likedPage.items.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("播放")
        }
    }
}

@Composable
private fun QCollectionTools(store: MusicStore, ui: MusicUiState, kind: LibraryKind) {
    val query = if (kind == LibraryKind.ALBUMS) ui.albumQuery else ui.playlistQuery
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (kind == LibraryKind.ALBUMS) "收藏的专辑" else "我的歌单", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (kind == LibraryKind.ALBUMS) "${ui.albumsPage.total ?: ui.albumsPage.loaded} 张专辑" else "${ui.playlistsPage.total ?: ui.playlistsPage.loaded} 个歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { store.refreshLibrary() }) { Icon(Icons.Rounded.Refresh, "刷新") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                if (kind == LibraryKind.ALBUMS) store.ui("uiAlbumQuery", args("value" to value))
                else store.ui("uiPlaylistQuery", args("value" to value))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(if (kind == LibraryKind.ALBUMS) "搜索专辑名称" else "搜索歌单名称") },
        )
        if (kind == LibraryKind.PLAYLISTS) {
            QChoicePills(
                options = listOf("all" to "全部", "created" to "我创建的", "collected" to "我收藏的"),
                selected = ui.playlistFilter,
                onSelect = { store.ui("uiPlaylistFilter", args("value" to it)) },
            )
        }
    }
}

@Composable
private fun QCollectionGrid(
    items: List<Collection>,
    columns: Int,
    onOpen: (Collection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        QEmptyState(Icons.Rounded.LibraryMusic, "这里还没有内容", "在网易云音乐收藏歌单或专辑后，会自动出现在这里。")
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { item ->
                    QCollectionCard(item, { onOpen(item) }, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun QCollectionPage(
    store: MusicStore,
    ui: MusicUiState,
    player: PlayerState,
    wide: Boolean,
    collectionSnapshot: Collection? = null,
) {
    val collection = collectionSnapshot ?: ui.collection ?: return
    val page = ui.collectionPage
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (wide) 30.dp else QPagePadding,
            end = if (wide) 30.dp else QPagePadding,
            top = 6.dp,
            bottom = if (wide) 28.dp else 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { store.closeCollection() }) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text(if (collection.kind == "album") "专辑" else "歌单", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(collection.cover, Modifier.size(if (wide) 142.dp else 112.dp), shape = RoundedCornerShape(28.dp), iconSize = 34.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(collection.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(collection.detail.ifBlank { "${collection.count} 首 · ${collection.owner.ifBlank { "网易云音乐" }}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { store.ui("uiPlayAll", args("shuffle" to false)) }) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("播放全部")
                        }
                        FilledTonalIconButton(onClick = { store.ui("uiPlayAll", args("shuffle" to true)) }) {
                            Icon(Icons.Rounded.Shuffle, "随机播放")
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("歌曲", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${page.loaded}${page.total?.let { " / $it" } ?: ""} 首", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (page.items.isEmpty() && !page.ready) {
            item { QLoadingList(6) }
        } else if (page.items.isEmpty()) {
            item { QEmptyState(Icons.Rounded.LibraryMusic, "歌单是空的", "换一个歌单看看吧。") }
        } else {
            itemsIndexed(page.items, key = { _, song -> song.id }) { index, song ->
                QTrackRow(
                    song, index, player.song?.id == song.id, song.id in ui.liked,
                    onClick = { store.playTracks(page.items, index) },
                    onMore = { store.ui("uiTrack", args("id" to song.id, "at" to index, "song" to song.json())) },
                    onLike = { store.toggleLiked(song) },
                )
            }
            if (page.more) item { TextButton(onClick = { store.ui("uiMoreCollection") }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
        }
    }
}
