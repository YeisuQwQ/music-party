@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.yiqidi.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun QSettingsPage(store: MusicStore, ui: MusicUiState) {
    var showLogin by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = QPagePadding, end = QPagePadding, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { QPageIntro("设置", "让一起迪更贴合你的习惯", eyebrow = "PERSONALIZE") }
        item { QAccountCard(store, ui, onLogin = { showLogin = true }) }
        item { QAppearanceCard(store, ui) }
        item { QLyricCard(store, ui) }
    }
    QLoginSheet(store, ui, visible = showLogin, onDismiss = { showLogin = false })
}

@Composable
private fun QAccountCard(store: MusicStore, ui: MusicUiState, onLogin: () -> Unit) {
    val account = ui.account
    Surface(
        color = if (account == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (account == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Rounded.Login, null, Modifier.padding(14.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("登录网易云音乐", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("同步红心歌单、创建与加入好友房", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Login, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("登录")
                }
            }
        } else {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QAvatar(account.avatar, 58.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(account.name, style = MaterialTheme.typography.titleLarge)
                        Text("网易云音乐 · UID ${account.uid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    QTag("已登录", icon = Icons.Rounded.Check)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { store.navigate(Destination.ROOMS) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("一起听")
                    }
                    OutlinedButton(onClick = { store.logout() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Logout, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("退出登录")
                    }
                }
            }
        }
    }
}

@Composable
private fun QLoginSheet(store: MusicStore, ui: MusicUiState, visible: Boolean, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(visible) }
    var smsMode by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            sheet.show()
        } else if (mounted) {
            sheet.hide()
            mounted = false
        }
    }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }
    LaunchedEffect(ui.account) {
        if (ui.account != null && visible) onDismiss()
    }
    if (!mounted) return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 22.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (smsMode) "手机验证码登录" else "登录网易云音乐", style = MaterialTheme.typography.headlineSmall)
            if (!smsMode) {
                Text(
                    "扫码登录，或改用手机号 + 短信验证码登录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        onDismiss()
                        store.createQr()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("扫码登录") }
                TextButton(onClick = { smsMode = true; error = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("手机验证码登录")
                }
            } else {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { value -> phone = value.filter(Char::isDigit).take(11); error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手机号") },
                    placeholder = { Text("11 位手机号") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = captcha,
                        onValueChange = { value -> captcha = value.filter(Char::isDigit).take(6); error = "" },
                        modifier = Modifier.weight(1f),
                        label = { Text("验证码") },
                        placeholder = { Text("短信验证码") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {}),
                    )
                    Button(
                        onClick = {
                            if (phone.length == 11 && !sending && countdown == 0) {
                                scope.launch {
                                    sending = true
                                    error = ""
                                    runCatching { store.sendSms(phone) }
                                        .onSuccess { countdown = 60 }
                                        .onFailure { error = it.message.orEmpty().ifBlank { "验证码发送失败" } }
                                    sending = false
                                }
                            }
                        },
                        enabled = phone.length == 11 && !sending && countdown == 0,
                        modifier = Modifier.height(56.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (countdown > 0) "${countdown}s" else "发送验证码")
                    }
                }
                if (error.isNotBlank()) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (captcha.length >= 4 && !logging) {
                            scope.launch {
                                logging = true
                                error = ""
                                runCatching { store.smsLogin(phone, captcha) }
                                    .onFailure { error = it.message.orEmpty().ifBlank { "登录失败，请检查验证码" } }
                                logging = false
                            }
                        }
                    },
                    enabled = captcha.length >= 4 && !logging,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (logging) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("登录")
                }
                TextButton(onClick = { smsMode = false; error = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("返回扫码登录")
                }
            }
            Text(
                "验证码只提交给网易云音乐，不会保存在本机。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QAppearanceCard(store: MusicStore, ui: MusicUiState) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Rounded.Palette, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleMedium)
                    Text("主题与配色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("主题模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (value, label) ->
                    FilterChip(
                        selected = ui.theme == value,
                        onClick = { store.ui("uiTheme", args("value" to value)) },
                        label = { Text(label) },
                        shape = CircleShape,
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("跟随系统取色", Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.titleSmall)
                        QTag("已启用")
                    }
                    Text(
                        "配色会跟随系统壁纸与主题，自动生成协调的色调。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ).forEach { color ->
                            Box(Modifier.size(29.dp).clip(CircleShape).background(color))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QLyricCard(store: MusicStore, ui: MusicUiState) {
    val prefs = ui.lyricPrefs
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(Icons.Rounded.Tune, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("歌词", style = MaterialTheme.typography.titleMedium)
                    Text("对齐、同步与常亮", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("对齐方式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "左对齐", 1 to "居中").forEach { (value, label) ->
                    FilterChip(
                        selected = prefs.align == value,
                        onClick = { store.ui("uiLyricPrefsAlign", args("value" to value)) },
                        label = { Text(label) },
                        shape = CircleShape,
                    )
                }
            }
            Text("同步微调 · ${prefs.shift} ms", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = prefs.shift.toFloat().coerceIn(-5000f, 5000f),
                onValueChange = { store.ui("uiLyricPrefsShift", args("value" to ((it + 5000f) / 10000f))) },
                valueRange = -5000f..5000f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("歌词页保持屏幕常亮", style = MaterialTheme.typography.bodyMedium)
                    Text("仅在歌词页播放时生效", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = prefs.awake, onCheckedChange = { store.ui("uiLyricPrefsAwake", args("value" to it)) })
            }
        }
    }
}


