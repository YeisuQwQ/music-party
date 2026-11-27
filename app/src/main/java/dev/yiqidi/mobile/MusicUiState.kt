package dev.yiqidi.mobile

import org.json.JSONObject

enum class Destination { LIBRARY, SEARCH, ROOMS, SETTINGS }
enum class LibraryKind(val title: String) {
    LIKED("我喜欢的音乐"), PLAYLISTS("我的歌单"), ALBUMS("收藏的专辑"),
}

data class CatalogPage<T>(
    val items: List<T> = emptyList(),
    val loaded: Int = 0,
    val total: Int? = null,
    val more: Boolean = false,
    val ready: Boolean = false,
    val loading: Boolean = false,
    val error: String = "",
) {
    companion object {
        fun <T> from(v: JSONObject?, decode: (JSONObject) -> T): CatalogPage<T> = if (v == null) CatalogPage() else CatalogPage(
            v.optJSONArray("items").objects(decode), v.optInt("loaded"),
            if (v.isNull("total")) null else v.optInt("total"),
            v.optBoolean("more"), v.optBoolean("ready"), v.optBoolean("loading"), v.optString("error"),
        )
    }
}

data class MusicUiState(
    val destination: Destination = Destination.LIBRARY,
    val libraryKind: LibraryKind = LibraryKind.LIKED,
    val query: String = "",
    val searchedQuery: String = "",
    val searchKind: String = "songs",
    val searchSongs: List<Song> = emptyList(),
    val searchCollections: List<Collection> = emptyList(),
    val likedPage: CatalogPage<Song> = CatalogPage(),
    val playlistsPage: CatalogPage<Collection> = CatalogPage(),
    val albumsPage: CatalogPage<Collection> = CatalogPage(),
    val collection: Collection? = null,
    val collectionPage: CatalogPage<Song> = CatalogPage(),
    val playlistQuery: String = "",
    val albumQuery: String = "",
    val playlistFilter: String = "all",
    val liked: Set<String> = emptySet(),
    val likesReady: Boolean = false,
    val favoriteBusy: String = "",
    val account: Account? = null,
    val accountBusy: Boolean = false,
    val qrUrl: String = "",
    val qrScanned: Boolean = false,
    val room: JSONObject? = null,
    val roomQueue: List<RoomTrack> = emptyList(),
    val chat: List<RoomMessage> = emptyList(),
    val chatDraft: String = "",
    val publicRoom: Boolean = false,
    val roomBusy: Boolean = false,
    val roomHeld: Boolean = false,
    val searching: Boolean = false,
    val canSearchMore: Boolean = false,
    val searchError: String = "",
    val error: String = "",
    val info: String = "",
    val theme: Int = 0,
    val lyricPrefs: LyricPrefs = LyricPrefs(),
    val lyrics: List<Lyric> = emptyList(),
    val lyricsLoading: Boolean = false,
    val lyricMode: Boolean = false,
    val roomChoiceSong: Song? = null,
    val invitePreview: InvitePreview? = null,
) {
    companion object {
        fun from(v: JSONObject) = MusicUiState(
            destination = Destination.entries.firstOrNull { it.name == v.optString("destination") } ?: Destination.LIBRARY,
            libraryKind = LibraryKind.entries.firstOrNull { it.name == v.optString("libraryKind") } ?: LibraryKind.LIKED,
            query = v.optString("query"), searchedQuery = v.optString("searchedQuery"), searchKind = v.optString("searchKind", "songs"),
            searchSongs = arraySongs(v.optJSONArray("searchSongs")),
            searchCollections = v.optJSONArray("searchCollections").objects(Collection::from),
            likedPage = CatalogPage.from(v.optJSONObject("likedPage"), Song::from),
            playlistsPage = CatalogPage.from(v.optJSONObject("playlistsPage"), Collection::from),
            albumsPage = CatalogPage.from(v.optJSONObject("albumsPage"), Collection::from),
            collection = v.optJSONObject("collection")?.let(Collection::from),
            collectionPage = CatalogPage.from(v.optJSONObject("collectionPage"), Song::from),
            playlistQuery = v.optString("libraryQuery"), albumQuery = v.optString("albumQuery"), playlistFilter = v.optString("playlistFilter", "all"),
            liked = v.optJSONArray("liked").strings().toSet(), likesReady = v.optBoolean("likesReady"), favoriteBusy = v.optString("favoriteBusy"),
            account = v.optJSONObject("account")?.let { Account(it.optString("uid"), it.optString("name"), it.optString("avatar")) },
            accountBusy = v.optBoolean("accountBusy"), qrUrl = v.optString("qrUrl"), qrScanned = v.optBoolean("qrScanned"),
            room = v.optJSONObject("room"), roomQueue = v.optJSONArray("roomQueue").objects { row ->
                row.optJSONObject("song")?.let {
                    RoomTrack(
                        Song.from(it), row.optString("bizId"), row.optString("owner"),
                        uped = row.optString("uped") == "up" || row.optBoolean("uped"),
                        upCount = row.optInt("upCount"),
                        likeCount = row.optInt("likeCount"),
                    )
                }
            }.filterNotNull(),
            chat = v.optJSONArray("chat").objects { RoomMessage(it.optString("key"), it.optString("name"), it.optString("text"), it.optLong("time")) }
                .filterNot { it.text.contains("升级App到最新版本即可查看该消息") || it.text == "[表情]" },
            chatDraft = v.optString("chatDraft"), publicRoom = v.optBoolean("publicRoom"), roomBusy = v.optBoolean("roomBusy"), roomHeld = v.optBoolean("roomHeld"),
            searching = v.optBoolean("searching"), searchError = v.optString("searchError"),
            canSearchMore = v.optBoolean("canSearchMore"), error = v.optString("error"), info = v.optString("info"), theme = v.optInt("theme"),
            lyricPrefs = v.optJSONObject("lyricPrefs")?.let { LyricPrefs(it.optInt("align"), it.optInt("shift"), it.optBoolean("awake")) } ?: LyricPrefs(),
            lyrics = v.optJSONArray("lyrics").objects { Lyric(if (it.isNull("at")) null else it.optLong("at"), it.optString("text"), it.optString("translation")) },
            lyricMode = v.optBoolean("lyricMode"), lyricsLoading = v.optBoolean("lyricsLoading"), roomChoiceSong = v.optJSONObject("roomChoiceSong")?.let(Song::from),
            invitePreview = v.optJSONObject("invitePreview")?.let {
                InvitePreview(it.optString("link"), it.optString("roomId"), it.optString("inviter"), it.optString("song"), if (it.isNull("status")) "未知" else it.optString("status"), it.optBoolean("joinable"))
            },
        )


    }
}

data class PlayerState(
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    val at: Int = -1,
    val active: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val previous: Boolean = false,
    val next: Boolean = false,
    val roomMode: Boolean = false,
) {
    companion object {
        fun from(v: JSONObject) = PlayerState(
            song = v.optJSONObject("song")?.let(Song::from), queue = arraySongs(v.optJSONArray("queue")),
            at = v.optInt("at", -1), active = v.optBoolean("active"), playing = v.optBoolean("playing"),
            buffering = v.optBoolean("buffering"), duration = v.optLong("duration"), shuffle = v.optBoolean("shuffle"),
            repeat = v.optInt("repeat"), previous = v.optBoolean("previous"), next = v.optBoolean("next"), roomMode = v.optBoolean("roomMode"),
        )
    }
}
