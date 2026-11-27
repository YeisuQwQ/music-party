@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchScreen(store: MusicStore, ui: MusicUiState, player: PlayerState, pickTrack: (TrackChoice) -> Unit) {
    if (ui.destination == Destination.SEARCH && ui.collection != null) {
        CollectionContent(store, ui, player, pickTrack)
        return
    }
    val focus = LocalFocusManager.current
    val format = ui.searchKind
    val submit = {
        focus.clearFocus()
        store.search(kind = format)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.query, onValueChange = { store.query(it.take(160)) }, modifier = Modifier.weight(1f),
                label = { Text("搜歌曲、歌单或艺人") }, shape = MaterialTheme.shapes.extraLarge, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = if (ui.query.isNotEmpty()) {{ IconAction(Icons.Rounded.Close, "清空搜索") { store.query(""); store.search(kind = format) } }} else null,
            )
            FilledIconButton(onClick = submit, enabled = ui.query.isNotBlank(), shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Search, "开始搜索") }
        }
        ChoiceRow(listOf("songs" to "歌曲", "playlists" to "歌单", "artists" to "艺人"), format, { selected ->
            if (format != selected) {
                focus.clearFocus()
                store.search(kind = selected)
            }
        }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                ui.searchedQuery.isBlank() -> item { EmptyNotice(Icons.Rounded.Search, "搜索网易云音乐") }
                ui.searching && ui.searchSongs.isEmpty() && ui.searchCollections.isEmpty() -> item { Busy("搜索中") }
                ui.searchError.isEmpty() && ui.searchSongs.isEmpty() && ui.searchCollections.isEmpty() -> item { EmptyNotice(Icons.Rounded.SearchOff, "没有找到结果") }
                else -> {
                    if (ui.searchKind == "songs") trackItems(ui.searchSongs, store, ui, player, pickTrack)
                    else itemsIndexed(ui.searchCollections, key = { _, result -> "${result.kind}:${result.id}" }) { index, result ->
                        val artist = result.kind == "artist"
                        CollectionRow(
                            result.title, if (artist) "艺人" else collectionSummary(result),
                            if (artist) Icons.Rounded.Person else Icons.Rounded.QueueMusic, result.cover, index, ui.searchCollections.size,
                        ) { store.openCollection(result) }
                    }
                }
            }
            if (ui.searchError.isNotBlank() || ui.canSearchMore) item {
                PageFooter(ui.searching, ui.searchError, ui.canSearchMore, {
                    store.search(ui.searchedQuery, ui.searchKind, append = ui.searchError.isEmpty())
                })
            }
        }
    }
}