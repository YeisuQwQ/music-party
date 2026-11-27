@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun QSearchPage(store: MusicStore, ui: MusicUiState, player: PlayerState, wide: Boolean) {
    val songs = ui.searchSongs
    val collections = ui.searchCollections
    var history by remember { mutableStateOf(store.searchHistory()) }
    val submitSearch: (String) -> Unit = { term ->
        val clean = term.trim()
        if (clean.isNotBlank()) {
            store.ui("uiQuery", args("value" to clean))
            history = store.rememberSearch(clean)
            store.ui("uiSearch", args("kind" to ui.searchKind))
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (wide) 30.dp else QPagePadding,
            end = if (wide) 30.dp else QPagePadding,
            top = 12.dp,
            bottom = if (wide) 30.dp else 130.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            QPageIntro(
                "搜索",
                "找到一首歌，也找到同频的人",
                eyebrow = "DISCOVER",
            )
        }
        item {
            QSearchField(store, ui, submitSearch)
        }
        if (ui.query.isBlank()) {
            if (history.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        QSectionTitle("搜索记录", "最近搜过的内容", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            store.clearSearchHistory()
                            history = emptyList()
                        }) { Text("清空") }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it }) { term ->
                            AssistChip(
                                onClick = { submitSearch(term) },
                                label = { Text(term) },
                                leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
            item {
                QSectionTitle("试试这些", "从一种心情开始")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("陈奕迅", "Lofi", "City Pop", "一起听", "周杰伦").forEach { text ->
                        item {
                            AssistChip(
                                onClick = { submitSearch(text) },
                                label = { Text(text) },
                                leadingIcon = { Icon(Icons.Rounded.TrendingUp, null, Modifier.size(16.dp)) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
            item {
                QEmptyState(
                    Icons.Rounded.Explore,
                    "搜一首歌，遇见同频的人",
                    "支持歌曲、歌单和歌手。找到喜欢的歌，邀请朋友一起听。",
                )
            }
        } else {
            item {
                QChoicePills(
                    options = listOf("songs" to "歌曲", "playlists" to "歌单", "artists" to "歌手"),
                    selected = ui.searchKind,
                    onSelect = { store.ui("uiSearchKind", args("kind" to it)) },
                )
            }
            if (ui.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (ui.searchError.isNotBlank()) {
                item {
                    QEmptyState(
                        Icons.Rounded.Search,
                        "搜索暂时不可用",
                        ui.searchError,
                        actionLabel = "重试",
                        onAction = { store.ui("uiSearch", args("kind" to ui.searchKind)) },
                    )
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (ui.searchKind) {
                                "playlists" -> "歌单"
                                "artists" -> "歌手"
                                else -> "歌曲"
                            },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (ui.searching) "搜索中…" else when (ui.searchKind) {
                                "playlists" -> "${collections.size} 个结果"
                                "artists" -> "${collections.size} 个结果"
                                else -> "${songs.size} 个结果"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (ui.searchKind) {
                    "playlists", "artists" -> {
                        if (collections.isEmpty() && !ui.searching) {
                            item { QEmptyState(Icons.Rounded.Album, "没有找到相关${if (ui.searchKind == "artists") "歌手" else "歌单"}", "换一个关键词试试。") }
                        } else {
                            items(collections, key = { "${it.kind}:${it.id}" }) { collection ->
                                QCollectionResultRow(collection) { store.openCollection(collection) }
                            }
                        }
                    }
                    else -> {
                        if (songs.isEmpty() && !ui.searching) {
                            item { QEmptyState(Icons.Rounded.MusicNote, "没有找到相关歌曲", "试试歌曲名、歌手或专辑名。") }
                        } else {
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                QTrackRow(
                                    song, index, player.song?.id == song.id, song.id in ui.liked,
                                    onClick = { store.playTracks(songs, index) },
                                    onMore = { store.ui("uiTrack", args("id" to song.id, "at" to index, "song" to song.json())) },
                                    onLike = { store.toggleLiked(song) },
                                )
                            }
                        }
                    }
                }
                if (ui.canSearchMore) {
                    item {
                        TextButton(
                            onClick = { store.ui("uiSearchMore", args("kind" to ui.searchKind)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("加载更多结果") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QSearchField(store: MusicStore, ui: MusicUiState, onSearch: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = { store.ui("uiQuery", args("value" to it)) },
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            singleLine = true,
            placeholder = { Text("搜索音乐") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (ui.query.isNotBlank()) {
                    IconButton(onClick = { store.ui("uiQuery", args("value" to "")) }) {
                        Icon(Icons.Rounded.Close, "清空")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch(ui.query)
                keyboard?.hide()
            }),
        )
        Button(
            onClick = { onSearch(ui.query); keyboard?.hide() },
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 18.dp),
            enabled = ui.query.isNotBlank() && !ui.searching,
        ) { Text("搜索") }
    }
}

@Composable
private fun QCollectionResultRow(collection: Collection, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(collection.cover, Modifier.size(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(collection.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    collection.detail.ifBlank { "${collection.count} 首 · ${collection.owner}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
