@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.yiqidi.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class QMediaFile(
    val kind: String,
    val path: String,
    val mime: String,
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
)

@Composable
internal fun QAttachButton(onPicked: (QMediaFile) -> Unit, allowVoiceVideo: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf("image") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStart by remember { mutableLongStateOf(0L) }
    var showRecording by remember { mutableStateOf(false) }

    fun deliver(uri: Uri) {
        scope.launch {
            runCatching { loadMedia(context, uri, pendingKind) }
                .onSuccess(onPicked)
                .onFailure { Toast.makeText(context, it.message ?: "读取附件失败", Toast.LENGTH_SHORT).show() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44100)
            rec.setAudioEncodingBitRate(96000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            recordingFile = file
            recordingStart = System.currentTimeMillis()
            showRecording = true
        }.onFailure {
            recorder?.release()
            recorder = null
            Toast.makeText(context, it.message ?: "无法开始录音", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording(send: Boolean) {
        val rec = recorder
        val file = recordingFile
        recorder = null
        recordingFile = null
        showRecording = false
        if (rec != null) runCatching { rec.stop() }
        runCatching { rec?.release() }
        if (!send) {
            file?.delete()
            return
        }
        if (file == null || !file.exists() || file.length() < 512) {
            Toast.makeText(context, "录音太短了", Toast.LENGTH_SHORT).show()
            file?.delete()
            return
        }
        val duration = (System.currentTimeMillis() - recordingStart).coerceAtLeast(1000L)
        onPicked(QMediaFile("voice", file.absolutePath, "audio/mp4", "voice.m4a", 0, 0, duration))
    }

    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.AttachFile, "添加附件")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("图片") },
                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                onClick = {
                    menu = false
                    pendingKind = "image"
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            if (allowVoiceVideo) {
                DropdownMenuItem(
                    text = { Text("视频") },
                    leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                    onClick = {
                        menu = false
                        pendingKind = "video"
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                )
                DropdownMenuItem(
                    text = { Text("语音") },
                    leadingIcon = { Icon(Icons.Rounded.Mic, null) },
                    onClick = {
                        menu = false
                        permission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                )
            }
        }
    }

    if (showRecording) {
        AlertDialog(
            onDismissRequest = { stopRecording(false) },
            title = { Text("正在录音") },
            text = { Text("点击“停止并发送”完成语音附件。") },
            confirmButton = {
                TextButton(onClick = { stopRecording(true) }) { Text("停止并发送") }
            },
            dismissButton = {
                TextButton(onClick = { stopRecording(false) }) { Text("取消") }
            },
        )
    }
}

private suspend fun loadMedia(context: Context, uri: Uri, kind: String): QMediaFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: if (kind == "video") "video/mp4" else "image/jpeg"
    val rawName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }.orEmpty()
    if (kind == "video") {
        val ext = mime.substringAfterLast('/', "mp4").let { if (it == "quicktime") "mov" else it }
        val file = File(context.cacheDir, "video_${System.currentTimeMillis()}.$ext")
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("无法读取视频")
        val retriever = android.media.MediaMetadataRetriever()
        var width = 0
        var height = 0
        var duration = 0L
        runCatching {
            retriever.setDataSource(file.absolutePath)
            width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
        retriever.release()
        return@withContext QMediaFile("video", file.absolutePath, mime, rawName.ifBlank { file.name }, width, height, duration)
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: throw IllegalStateException("无法读取图片")
    val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
    val file = File(context.cacheDir, "image_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    if (out !== bitmap) out.recycle()
    bitmap.recycle()
    QMediaFile("image", file.absolutePath, "image/jpeg", rawName.ifBlank { file.name }, out.width, out.height)
}