package dev.yiqidi.mobile

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val cover: String,
    val duration: Long,
    val albumId: String = "",
    val artistId: String = "",
    val format: String = "",
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
) {
    val artistLine: String get() = artist.ifBlank { "未知歌手" }
    val durationLabel: String get() = clockLabel(duration)
    fun uri(room: Boolean = false): Uri =
        Uri.Builder().scheme("yiqidi").authority("netease").appendPath(id).appendQueryParameter("room", if (room) "1" else "0").build()
    fun json(): JSONObject = args(
        "id" to id, "title" to title, "artist" to artist, "album" to album,
        "cover" to cover, "duration" to duration, "albumId" to albumId, "artistId" to artistId,
        "format" to format,
        "sampleRate" to sampleRate, "bitrate" to bitrate,
    )
    companion object {
        fun from(v: JSONObject): Song = Song(
            v.optString("id"), v.optString("title").ifBlank { "未知歌曲" }, v.optString("artist"),
            v.optString("album"), v.optString("cover"), v.optLong("duration"),
            v.optString("albumId"), v.optString("artistId"),
            v.optString("format"), v.optInt("sampleRate"), v.optInt("bitrate"),
        )
    }
}

data class Collection(val id: String, val title: String, val cover: String = "", val count: Int = 0, val owner: String = "", val detail: String = "", val kind: String = "playlist") {
    fun json() = args("id" to id, "title" to title, "cover" to cover, "count" to count, "owner" to owner, "detail" to detail, "kind" to kind)
    companion object {
        fun from(v: JSONObject) = Collection(v.optString("id"), v.optString("title"), v.optString("cover"), v.optInt("count"), v.optString("owner"), v.optString("detail"), v.optString("kind", "playlist"))
    }
}

data class Lyric(val at: Long?, val text: String, val translation: String)
data class LyricPrefs(val align: Int = 0, val shift: Int = 0, val awake: Boolean = false)
data class Account(val uid: String, val name: String, val avatar: String)
data class RoomTrack(val song: Song, val bizId: String, val owner: String, val uped: Boolean = false, val upCount: Int = 0, val likeCount: Int = 0)
data class RoomMessage(val key: String, val name: String, val text: String, val time: Long, val imageUrl: String = "", val imageWidth: Int = 0, val imageHeight: Int = 0)
data class InvitePreview(val link: String, val roomId: String, val inviter: String, val song: String, val status: String, val joinable: Boolean)
data class TrackChoice(val song: Song, val list: List<Song>)
enum class Panel { TRACK, DETAILS, QUEUE, LYRIC_SETTINGS }


fun clockLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

fun args(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
    values.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
}

fun <T> JSONArray?.objects(decode: (JSONObject) -> T): List<T> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(decode) }

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { i -> (opt(i) as? String)?.takeIf(String::isNotBlank) }

fun arraySongs(a: JSONArray?): List<Song> = a.objects(Song::from).filter { it.id.isNotBlank() }

fun JSONObject.lyrics(): List<Lyric> = optJSONArray("lines").objects {
    Lyric(if (it.isNull("at")) null else it.optLong("at"), it.optString("text"), it.optString("translation"))
}