@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.yiqidi.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val QPagePadding = 20.dp
internal val QPhoneBottomSpace = 112.dp

@Composable
internal fun QAvatar(url: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Cover(url, modifier.size(size), shape = CircleShape, iconSize = size * 0.44f)
}

@Composable
internal fun QTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier, color = color, contentColor = contentColor, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun QSectionTitle(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun QPageIntro(title: String, subtitle: String, eyebrow: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun QTrackRow(
    song: Song,
    index: Int,
    playing: Boolean,
    liked: Boolean,
    onClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    showIndex: Boolean = true,
    owner: String = "",
    moreIcon: ImageVector = Icons.Rounded.MoreVert,
    moreLabel: String = "更多操作",
    moreCount: Int = -1,
    likeCount: Int = -1,
    modifier: Modifier = Modifier,
) {
    val container = if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(22.dp),
        color = container,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIndex) {
                Text(
                    if (playing) "▶" else (index + 1).toString().padStart(2, '0'),
                    Modifier.width(26.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Box {
                Cover(song.cover, Modifier.size(54.dp), shape = RoundedCornerShape(16.dp))
                if (playing) {
                    Surface(
                        Modifier.align(Alignment.BottomEnd).padding(3.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            null,
                            Modifier.padding(3.dp).size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (playing) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.artistLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (owner.isNotBlank()) {
                        Text(
                            "· $owner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 118.dp),
                        )
                    }
                }
            }
            Text(
                song.durationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (onLike != null) {
                IconButton(onClick = onLike, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (liked) "取消收藏" else "收藏",
                        Modifier.size(18.dp),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (likeCount >= 0) {
                    Text(likeCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) {
                    Icon(moreIcon, moreLabel, Modifier.size(20.dp))
                }
                if (moreCount >= 0) {
                    Text(
                        moreCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun QCollectionCard(
    collection: Collection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))) {
            Cover(collection.cover, Modifier.matchParentSize(), shape = RoundedCornerShape(24.dp), iconSize = 42.dp)
            Surface(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            ) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        compactCount(collection.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(
            collection.title,
            Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            collection.detail.ifBlank { "${collection.count} 首 · ${collection.owner.ifBlank { "网易云音乐" }}" },
            Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun QActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    icon,
                    null,
                    Modifier.padding(9.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun QChoicePills(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
internal fun QEmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, Modifier.padding(22.dp).size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
internal fun QEmojiRow(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val emojis = listOf("😀", "😂", "🥹", "😍", "😎", "🤔", "😴", "🥳", "😭", "😡", "👍", "👏", "🙌", "❤️", "🔥", "✨", "🎵", "🎧", "🌙", "☕")
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        emojis.forEach { emoji ->
            Surface(
                onClick = { onPick(emoji) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(emoji, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
internal fun QLoadingList(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) {
            Surface(
                Modifier.fillMaxWidth().height(70.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.fillMaxWidth(0.62f).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Box(Modifier.fillMaxWidth(0.4f).height(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    }
                }
            }
        }
    }
}

@Composable
internal fun QStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun QGradientPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    ),
    content: @Composable () -> Unit,
) {
    Box(modifier.clip(shape).background(Brush.linearGradient(colors))) {
        Box(Modifier.size(180.dp).align(Alignment.TopEnd).background(Color.White.copy(alpha = 0.07f), CircleShape))
        Box(Modifier.size(120.dp).align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.05f), CircleShape))
        content()
    }
}

@Composable
internal fun QNowPlayingProgress(position: Long, duration: Long, modifier: Modifier = Modifier) {
    val fraction = if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

internal fun qGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..5 -> "夜深了"
    in 6..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}

internal fun compactCount(value: Int): String = when {
    value >= 10000 -> "${value / 10000}.${(value % 10000) / 1000}万"
    value >= 1000 -> "${value / 1000}k"
    else -> value.toString()
}

internal fun chatClock(time: Long): String {
    if (time <= 0) return ""
    val millis = if (time < 10_000_000_000L) time * 1000 else time
    return runCatching { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis)) }.getOrDefault("")
}

internal fun bitrateLabel(song: Song): String = listOfNotNull(
    song.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" },
    song.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
    song.format.takeIf(String::isNotBlank),
).joinToString(" · ").ifBlank { "网易云在线音源" }

internal fun JSONArrayToObjects(value: org.json.JSONArray?): List<org.json.JSONObject> =
    if (value == null) emptyList() else (0 until value.length()).mapNotNull { value.optJSONObject(it) }
