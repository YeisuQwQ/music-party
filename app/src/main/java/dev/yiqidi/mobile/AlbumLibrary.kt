@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AlbumLibrary(store: MusicStore, ui: MusicUiState) {
    val page = ui.albumsPage
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        CatalogToolbar(ui.albumQuery, store::albumQuery, "搜索专辑名称", "刷新专辑", page.loading, store::refreshLibrary)
        CatalogCount(page, "张专辑", ui.albumQuery.isNotBlank())
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 118.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (page.items.isEmpty() && !page.loading && page.error.isEmpty() && !page.more) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyNotice(Icons.Rounded.Album, if (ui.albumQuery.isNotBlank()) "没有符合条件的专辑" else "还没有收藏专辑")
            }
            items(page.items, key = { it.id }) { album ->
                Surface(onClick = { store.openCollection(album) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        Cover(album.cover, Modifier.fillMaxWidth().aspectRatio(1f), glyph = Icons.Rounded.Album, iconSize = 34.dp)
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.detail.ifBlank { "未知艺人" }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.count} 首", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (page.loading || page.error.isNotBlank() || page.more) item(span = { GridItemSpan(maxLineSpan) }) {
                PageFooter(page.loading, page.error, page.more, if (page.error.isBlank()) store::loadMoreAlbums else store::refreshLibrary)
            }
        }
    }
}