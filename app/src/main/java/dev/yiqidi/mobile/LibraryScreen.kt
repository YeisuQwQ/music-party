@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LibraryScreen(store: MusicStore, ui: MusicUiState, player: PlayerState, pickTrack: (TrackChoice) -> Unit, wide: Boolean) {
    if (ui.destination == Destination.LIBRARY && ui.collection != null) {
        CollectionContent(store, ui, player, pickTrack)
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (!wide) SecondaryScrollableTabRow(selectedTabIndex = ui.libraryKind.ordinal, edgePadding = 14.dp, minTabWidth = 68.dp) {
            LibraryKind.entries.forEach { kind ->
                Tab(ui.libraryKind == kind, { store.selectKind(kind) }, text = { Text(kind.title, maxLines = 1) })
            }
        }
        when {
            ui.account == null && ui.accountBusy -> Busy("正在读取网易云账号", Modifier.padding(14.dp))
            ui.account == null -> EmptyNotice(Icons.Rounded.AccountCircle, "登录后查看${ui.libraryKind.title}", action = {
                FilledTonalButton(onClick = { store.navigate(Destination.SETTINGS) }) { Text("前往登录") }
            })
            else -> when (ui.libraryKind) {
                LibraryKind.LIKED -> TrackPage(ui.likedPage, store, ui, player, pickTrack, "还没有喜欢的歌曲",
                    onMore = store::loadMoreLiked, onRetry = store::refreshLibrary, onRefresh = store::refreshLibrary)
                LibraryKind.PLAYLISTS -> PlaylistLibrary(store, ui)
                LibraryKind.ALBUMS -> AlbumLibrary(store, ui)
            }
        }
    }
}

@Composable
private fun PlaylistLibrary(store: MusicStore, ui: MusicUiState) {
    val page = ui.playlistsPage
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        CatalogToolbar(ui.playlistQuery, store::playlistQuery, "搜索歌单名称", "刷新音乐库", page.loading, store::refreshLibrary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "全部", "created" to "我创建的", "collected" to "我收藏的").forEach { (value, title) ->
                FilterChip(ui.playlistFilter == value, { store.filterPlaylists(value) }, label = { Text(title) })
            }
        }
        val filtered = ui.playlistQuery.isNotBlank() || ui.playlistFilter != "all"
        CatalogCount(page, "个歌单", filtered)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (page.items.isEmpty() && !page.loading && page.error.isEmpty()) item {
                EmptyNotice(Icons.Rounded.QueueMusic, if (filtered) "没有符合条件的歌单" else "还没有歌单")
            }
            itemsIndexed(page.items, key = { _, list -> list.id }) { index, list ->
                CollectionRow(list.title, collectionSummary(list), Icons.Rounded.QueueMusic, list.cover, index, page.items.size) {
                    store.openCollection(list)
                }
            }
            if (page.loading || page.error.isNotBlank()) item {
                PageFooter(page.loading, page.error, false, store::refreshLibrary)
            }
        }
    }
}

@Composable
internal fun CatalogToolbar(query: String, onQuery: (String) -> Unit, label: String, refresh: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(query, { onQuery(it.take(160)) }, Modifier.weight(1f), label = { Text(label) }, singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            trailingIcon = if (query.isNotEmpty()) {{ IconAction(Icons.Rounded.Close, "清空筛选") { onQuery("") } }} else null)
        IconAction(Icons.Rounded.Refresh, refresh, !loading, onRefresh)
    }
}

@Composable
internal fun CatalogCount(page: CatalogPage<*>, unit: String, filtered: Boolean = false) {
    if (!page.ready) return
    val summary = when {
        filtered -> "已找到 ${page.items.size} $unit"
        page.more && page.total != null -> "已加载 ${page.loaded} / ${page.total} $unit"
        page.more -> "已加载 ${page.loaded} $unit"
        else -> "${page.items.size} $unit"
    }
    Text(summary, Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun collectionSummary(item: Collection): String = listOf(item.detail, "${item.count} 首").filter(String::isNotBlank).joinToString(" · ")