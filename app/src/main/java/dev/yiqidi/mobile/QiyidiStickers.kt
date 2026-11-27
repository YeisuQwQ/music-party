@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class QSticker(
    val id: String,
    val group: String,
    val name: String,
    val url: String,
    val width: Int,
    val height: Int,
    val format: String,
) {
    fun json(): JSONObject = JSONObject().apply {
        put("emojiId", id)
        put("emojiGroupId", group)
        put("name", name)
        put("url", url)
        put("width", width)
        put("height", height)
        put("format", format)
    }
}

@Composable
internal fun QStickerPanel(
    store: MusicStore,
    scope: String,
    onText: (String) -> Unit,
    onSticker: (QSticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutine = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var group by remember { mutableStateOf("") }
    var stickers by remember { mutableStateOf<List<QSticker>>(emptyList()) }
    var cursor by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<QSticker?>(null) }

    LaunchedEffect(scope) {
        groups = emptyList(); stickers = emptyList(); group = ""; cursor = ""; more = false; loaded = false; error = ""
        runCatching { NativeBridge.request("stickerGroups", args("scope" to scope)) }
            .onSuccess { result ->
                groups = result.optJSONArray("groups").objects { it }.map { it.optString("id") to it.optString("name") }
                    .filter { it.first.isNotBlank() }
                group = groups.firstOrNull()?.first.orEmpty()
                loaded = true
            }.onFailure { error = it.message.orEmpty() }
    }
    LaunchedEffect(group) {
        if (group.isBlank()) return@LaunchedEffect
        stickers = emptyList(); cursor = ""; more = true; error = ""
        runCatching { NativeBridge.request("stickerPage", args("groupId" to group, "cursor" to "")) }
            .onSuccess { result ->
                stickers = result.optJSONArray("items").objects { QSticker(it.optString("emojiId"), it.optString("emojiGroupId"), it.optString("name"), it.optString("url"), it.optInt("width", 64), it.optInt("height", 64), it.optString("format", "png")) }
                cursor = result.optString("cursor")
                more = result.optBoolean("more")
            }.onFailure { error = it.message.orEmpty() }
    }
    fun loadMore() {
        if (!more || cursor.isBlank()) return
        coroutine.launch {
            runCatching { NativeBridge.request("stickerPage", args("groupId" to group, "cursor" to cursor)) }
                .onSuccess { result ->
                    stickers = stickers + result.optJSONArray("items").objects { QSticker(it.optString("emojiId"), it.optString("emojiGroupId"), it.optString("name"), it.optString("url"), it.optInt("width", 64), it.optInt("height", 64), it.optString("format", "png")) }
                    cursor = result.optString("cursor")
                    more = result.optBoolean("more")
                }.onFailure { error = it.message.orEmpty() }
        }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text("常用") },
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text("云表情") },
            )
        }
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            if (tab == 0) {
                QEmojiRow(onPick = onText, modifier = Modifier.fillMaxWidth())
            } else {
                Column(Modifier.fillMaxWidth()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(groups, key = { it.first }) { item ->
                                FilterChip(
                                    selected = item.first == group,
                                    onClick = { group = item.first },
                                    label = { Text(item.second, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(),
                                )
                            }
                        }
                        if (error.isNotBlank()) {
                            Text(error, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (!loaded) {
                            Text("正在加载表情…", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall)
                        } else if (stickers.isEmpty()) {
                            Text("这个分组还没有表情", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(128.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(stickers, key = { _, item -> item.id }) { index, item ->
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.combinedClickable(
                                            onClick = { onSticker(item) },
                                            onLongClick = { selected = item },
                                        ),
                                    ) {
                                        Box(Modifier.size(64.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                                            AsyncImage(
                                                model = item.url,
                                                contentDescription = item.name,
                                                modifier = Modifier.size(52.dp),
                                                contentScale = ContentScale.Fit,
                                            )
                                        }
                                    }
                                    LaunchedEffect(index) { if (index == stickers.lastIndex) loadMore() }
                                }
                            }
                        }
                    }
                }
        }
    }
    selected?.let { sticker ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("自定义表情") },
            text = { Text("收藏这个表情，或从自定义分组中删除？删除不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    coroutine.launch {
                        runCatching { store.collectSticker(sticker) }
                            .onSuccess { group = "" }
                            .onFailure { error = it.message.orEmpty() }
                        selected = null
                    }
                }) { Text("收藏") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        coroutine.launch {
                            runCatching { store.removeSticker(sticker) }
                                .onSuccess { group = "" }
                                .onFailure { error = it.message.orEmpty() }
                            selected = null
                        }
                    }) { Text("删除") }
                    TextButton(onClick = { selected = null }) { Text("取消") }
                }
            },
        )
    }
}
