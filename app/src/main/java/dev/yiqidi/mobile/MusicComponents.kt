@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
internal fun Cover(
    artwork: String,
    modifier: Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    glyph: ImageVector = Icons.Rounded.MusicNote,
    iconSize: Dp = 24.dp,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(shape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(glyph, null, Modifier.size(iconSize), tint = colors.onSecondaryContainer)
        if (artwork.isNotBlank()) {
            AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun IconAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        enableUserInput = false,
    ) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }
    }
}

@Composable
internal fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun EmptyNotice(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(34.dp)) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (!detail.isNullOrEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(2.dp)); action() }
    }
}

@Composable
internal fun TrackRow(
    song: Song,
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    subtitle: String = song.artistLine,
    trailing: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onMenu,
        onLongClickLabel = if (onMenu == null) null else "歌曲操作",
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Cover(song.cover, Modifier.size(48.dp)) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing ?: { if (onMenu != null) IconAction(Icons.Rounded.MoreVert, "歌曲操作", onClick = onMenu) },
        contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
    ) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
internal fun CollectionRow(
    title: String,
    detail: String,
    icon: ImageVector,
    cover: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Cover(cover, Modifier.size(48.dp), glyph = icon) },
        supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        contentPadding = PaddingValues(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
internal fun PlayGroup(enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit, shuffleIcon: ImageVector, playLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onPlay, enabled = enabled) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(playLabel) }
        FilledTonalButton(onShuffle, enabled = enabled) { Icon(shuffleIcon, null); Spacer(Modifier.width(6.dp)); Text("随机") }
    }
}

@Composable
internal fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(selected == value, { onSelect(value) }, SegmentedButtonDefaults.itemShape(index, options.size)) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun Busy(label: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoadingIndicator(Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}