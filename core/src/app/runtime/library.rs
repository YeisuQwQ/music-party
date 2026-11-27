use super::{array, failure, number, text, App, Update};
use crate::protocol::{online_id, online_song};
use serde_json::{json, Value};
use std::collections::BTreeSet;

#[derive(Default)]
struct Page {
    items: Vec<Value>,
    seen: BTreeSet<String>,
    offset: u64,
    more: bool,
    total: Option<u64>,
    ready: bool,
    error: String,
}

impl Page {
    fn accept(&mut self, reply: &Value, size: u64, max: u64) {
        let next = reply["next"].as_u64().unwrap_or(self.offset + size);
        if reply["more"] == true && (next <= self.offset || next > max) {
            self.error = "服务端分页没有继续，请刷新重试".into();
            self.more = false;
            return;
        }
        let previous = self.items.len();
        self.items.extend(array(&reply["items"]).into_iter().filter(|item| {
            let id = text(&item["id"]);
            online_id(id) && self.seen.insert(id.to_owned())
        }));
        if reply["more"] == true && self.items.len() == previous && (size != 100 || reply["items"].as_array().is_some_and(|items| !items.is_empty())) {
            self.error = "服务端重复返回同一页，请刷新重试".into();
            self.more = false;
            return;
        }
        self.total = reply["total"].as_u64().or(self.total);
        self.ready = true;
        self.error.clear();
        self.more = reply["more"] == true;
        self.offset = next;
    }

    fn view(&self, loading: bool, keep: impl Fn(&Value) -> bool) -> Value {
        let items: Vec<_> = self.items.iter().filter(|item| keep(item)).collect();
        json!({"items":items,"loaded":self.items.len(),"total":self.total,"more":self.more,
            "ready":self.ready,"loading":loading,"error":self.error})
    }
}

#[derive(Default)]
pub(super) struct Library {
    online_favorite: BTreeSet<String>,
    like_order: Vec<String>,
    likes_ready: bool,
    likes_error: String,
    favorite_target: bool,
    favorite_in_flight: String,
    theme: u64,
    lyric_prefs: Value,
    static_lyrics: bool,
    lyrics: Vec<Value>,
    lyrics_visible: bool,
    searched: String,
    search_kind: &'static str,
    search: Page,
    playlists: Page,
    albums: Page,
    liked_tracks: Page,
    playlist_filter: &'static str,
    playlist_query: String,
    album_query: String,
    collection: Value,
    collection_tracks: Page,
}

impl Library {
    pub fn has_collection(&self) -> bool { self.collection.is_object() }

    pub fn visible_tracks(&self, page: &str, kind: &str) -> &[Value] {
        if matches!(page, "LIBRARY" | "SEARCH" | "") && self.has_collection() {
            &self.collection_tracks.items
        } else if page == "SEARCH" && matches!(self.search_kind, "songs" | "") {
            &self.search.items
        } else if matches!(page, "LIBRARY" | "") && matches!(kind, "LIKED" | "") {
            &self.liked_tracks.items
        } else {
            &[]
        }
    }

    pub fn restore(&mut self, data: &Value) -> bool {
        self.theme = number(&data["theme"]).min(2);
        self.lyric_prefs = lyric_prefs(&data["lyricPrefs"]);
        data["legacyLibrary"] == true || ["songs", "recent", "lists", "listTracks", "liked", "importedLyrics"]
            .iter().any(|key| data.get(key).is_some())
    }

    pub fn saved(&self) -> Value { json!({"theme":self.theme,"lyricPrefs":self.lyric_prefs}) }

    pub fn online_liked(&mut self, ids: &Value) {
        self.online_favorite.clear();
        self.like_order = ids.as_array().into_iter().flatten().filter_map(Value::as_str)
            .filter(|id| online_id(id) && self.online_favorite.insert((*id).to_owned()))
            .map(str::to_owned).collect();
        self.likes_ready = true;
        self.likes_error.clear();
    }

    fn marked_playlist(&self, uid: &str) -> Option<&Value> {
        self.playlists.items.iter().find(|item| item["owner"] == uid && number(&item["specialType"]) == 5)
    }

    pub fn reset_online(&mut self) {
        *self = Self {
            theme: self.theme, lyric_prefs: self.lyric_prefs.clone(),
            static_lyrics: self.static_lyrics, lyrics_visible: self.lyrics_visible, ..Self::default()
        };
    }

    pub fn view(&self, app: &App) -> Value {
        let songs = self.search_kind.is_empty() || self.search_kind == "songs";
        let uid = app.account.uid();
        let query = self.playlist_query.trim().to_lowercase();
        let album_query = self.album_query.trim().to_lowercase();
        let mut liked = self.liked_tracks.view(app.jobs.contains_key("likedTracks"), |_| true);
        if !self.liked_tracks.ready && self.liked_tracks.error.is_empty() && self.marked_playlist(uid).is_none() {
            if !self.playlists.error.is_empty() { liked["error"] = json!(self.playlists.error); }
            else if self.playlists.ready && !self.playlists.more && !self.likes_error.is_empty() { liked["error"] = json!(self.likes_error); }
            else if app.jobs.contains_key("playlists") || app.jobs.contains_key("liked") { liked["loading"] = json!(true); }
        }
        json!({"liked":self.online_favorite,"likesReady":self.likes_ready,"favoriteBusy":self.favorite_in_flight,
            "likedPage":liked,
            "libraryQuery":self.playlist_query,"albumQuery":self.album_query,"playlistFilter":if self.playlist_filter.is_empty() {"all"} else {self.playlist_filter},
            "playlistsPage":self.playlists.view(app.jobs.contains_key("playlists"), |item| {
                let owner = text(&item["owner"]);
                let created = !owner.is_empty() && owner == uid;
                (match self.playlist_filter {"created" => created, "collected" => !created, _ => true})
                    && (query.is_empty() || text(&item["title"]).to_lowercase().contains(&query))
            }),
            "albumsPage":self.albums.view(app.jobs.contains_key("albums"), |item| {
                album_query.is_empty() || text(&item["title"]).to_lowercase().contains(&album_query)
            }),
            "collection":self.collection,"collectionPage":self.collection_tracks.view(app.jobs.contains_key("collection"), |_| true),
            "searchKind":if songs {"songs"} else {self.search_kind},"searchedQuery":self.searched,
            "searchSongs":if songs {&self.search.items[..]} else {&[][..]},"searchCollections":if songs {&[][..]} else {&self.search.items[..]},
            "searching":app.jobs.contains_key("search"),"canSearchMore":self.search.more,"searchError":self.search.error,
            "theme":self.theme,"lyricPrefs":self.lyric_prefs,"lyrics":self.lyrics,"lyricMode":self.static_lyrics,
            "lyricsLoading":app.jobs.contains_key("lyrics")})
    }

    pub fn active_line(&self, position: u64) -> i64 {
        if self.static_lyrics || !self.lyrics_visible { return -1; }
        let shift = self.lyric_prefs["shift"].as_i64().unwrap_or(0);
        self.lyrics.iter().rposition(|line| line["at"].as_u64().is_some_and(|at| i128::from(at) + i128::from(shift) <= i128::from(position)))
            .map_or(-1, |i| i as i64)
    }
}

fn lyric_prefs(v: &Value) -> Value {
    json!({"align":number(&v["align"]).min(1),"shift":v["shift"].as_i64().unwrap_or(0).clamp(-5000,5000),"awake":v["awake"]==true})
}

impl App {
    pub(super) fn close_collection(&mut self, out: &mut Update) {
        self.cancel("collection", out);
        self.library.collection = Value::Null;
        self.library.collection_tracks = Page::default();
        out.ui = true;
    }

    pub(super) fn library_event(&mut self, event: &str, a: &Value, out: &mut Update) {
        match event {
            "closeCollection" => { self.close_collection(out); self.activate_library(out); }
            "favorite" => self.favorite(&a["song"], out),
            "search" => self.search(a, out),
            "refreshLibrary" => {
                if self.library_kind == "ALBUMS" {
                    self.cancel("albums", out);
                    self.library.albums = Page::default();
                } else {
                    self.cancel("playlists", out);
                    self.cancel("likedTracks", out);
                    self.library.playlists = Page::default();
                    self.library.liked_tracks = Page::default();
                    self.library.likes_error.clear();
                    self.refresh_likes(out);
                }
                self.activate_library(out);
            }
            "loadMoreLiked" => if self.on_library() && self.is_liked_page() {
                self.load_liked_tracks(out);
            },
            "loadMoreAlbums" => if self.on_library() && self.library_kind == "ALBUMS" { self.load_albums(out); },
            "libraryFilter" => self.library.playlist_filter = match text(&a["value"]) { "created" => "created", "collected" => "collected", _ => "all" },
            "libraryQuery" => self.library.playlist_query = text(&a["value"]).chars().take(160).collect(),
            "albumQuery" => {
                self.library.album_query = text(&a["value"]).chars().take(160).collect();
                self.activate_library(out);
            }
            "openCollection" => {
                if !online_id(text(&a["item"]["id"])) { return; }
                self.close_collection(out);
                self.library.collection = a["item"].clone();
                self.load_collection_page(out);
            }
            "loadMoreCollection" => self.load_collection_page(out),
            "theme" => { self.library.theme = number(&a["value"]).min(2); self.persist(out); }
            "lyricPrefs" => { self.library.lyric_prefs = lyric_prefs(a); self.persist(out); out.progress = true; }
            "lyricMode" => { self.library.static_lyrics = a["static"] == true; out.progress = true; }
            "lyricsVisible" => {
                self.library.lyrics_visible = a["visible"] == true;
                self.load_lyrics(out);
            }
            _ => return,
        }
        out.ui = true;
    }

    pub(super) fn activate_library(&mut self, out: &mut Update) {
        if !self.on_library() || self.account.uid().is_empty() { return; }
        if self.library_kind == "PLAYLISTS" { self.load_playlists(out); }
        else if self.library_kind == "ALBUMS" {
            if !self.library.albums.ready || !self.library.album_query.trim().is_empty() { self.load_albums(out); }
        }
        else if self.is_liked_page() && !self.library.liked_tracks.ready && self.library.liked_tracks.error.is_empty() {
            if self.library.marked_playlist(self.account.uid()).is_none() { self.load_playlists(out); }
            self.load_liked_tracks(out);
        }
    }

    fn on_library(&self) -> bool {
        (self.destination.is_empty() || self.destination == "LIBRARY") && self.library.collection.is_null()
    }

    fn is_liked_page(&self) -> bool { self.library_kind.is_empty() || self.library_kind == "LIKED" }

    fn load_liked_tracks(&mut self, out: &mut Update) {
        let page = &self.library.liked_tracks;
        let offset = page.offset;
        if self.account.uid().is_empty() || self.jobs.contains_key("likedTracks") || (page.ready && !page.more && page.error.is_empty()) { return; }
        if let Some(playlist) = self.library.marked_playlist(self.account.uid()) {
            let id = playlist["id"].clone();
            self.library.liked_tracks.error.clear();
            self.request("likedTracks", "playlistTracks", json!({"id":id,"offset":offset}), 0, out);
        } else if self.library.playlists.ready && !self.library.playlists.more && self.library.playlists.error.is_empty()
            && self.library.likes_ready && self.library.likes_error.is_empty() && !self.jobs.contains_key("liked") {
            let total = self.library.like_order.len();
            let start = (offset as usize).min(total);
            let end = (start + 100).min(total);
            if start == end {
                self.library.liked_tracks.accept(&json!({"items":[],"total":total}), 100, 100_000);
            } else {
                let input = json!({"ids":&self.library.like_order[start..end],"offset":offset,"total":total});
                self.library.liked_tracks.error.clear();
                self.request("likedTracks", "likedTracks", input, 0, out);
            }
        }
    }

    fn load_playlists(&mut self, out: &mut Update) {
        let page = &self.library.playlists;
        if self.jobs.contains_key("playlists") || !page.error.is_empty() || (page.ready && !page.more) { return; }
        self.request("playlists", "playlists", json!({"uid":self.account.uid(),"offset":page.offset}), 0, out);
    }

    fn load_albums(&mut self, out: &mut Update) {
        let page = &self.library.albums;
        if self.jobs.contains_key("albums") || !page.error.is_empty() || (page.ready && !page.more) { return; }
        self.request("albums", "albums", json!({"offset":page.offset}), 0, out);
    }

    pub(super) fn refresh_likes(&mut self, out: &mut Update) {
        if self.account.uid().is_empty() || self.jobs.contains_key("liked") || self.jobs.contains_key("favorite") { return; }
        self.request("liked", "liked", json!({"uid":self.account.uid()}), 0, out);
    }

    pub(super) fn likes_reply(&mut self, reply: &Value, out: &mut Update) {
        if let Some(error) = failure(reply) { self.library.likes_error = error.to_owned(); }
        else { self.library.online_liked(&reply["ids"]); }
        self.activate_library(out);
    }

    fn favorite(&mut self, song: &Value, out: &mut Update) {
        let id = text(&song["id"]);
        if !online_song(song) { return; }
        if self.account.uid().is_empty() {
            self.message("登录后可收藏网易云歌曲", true);
        } else if !self.library.likes_ready || self.jobs.contains_key("liked") {
            self.message("喜欢列表尚未加载完成，请刷新曲库后再试", true);
        } else if !self.jobs.contains_key("favorite") {
            self.library.favorite_target = !self.library.online_favorite.contains(id);
            self.library.favorite_in_flight = id.to_owned();
            self.request("favorite", "favorite", json!({"id":id,"liked":self.library.favorite_target}), 0, out);
        }
    }

    pub(super) fn search_from_ui(&mut self, a: &Value, out: &mut Update) {
        if a["clear"] == true { self.query.clear(); }
        let query = if a["results"] == true { &self.library.searched } else { &self.query };
        let kind = a["kind"].as_str().unwrap_or(self.library.search_kind);
        self.library_event("search", &json!({"query":query,"kind":kind,"append":a["append"]}), out);
    }

    fn search(&mut self, a: &Value, out: &mut Update) {
        let query: String = text(&a["query"]).trim().chars().take(160).collect();
        let kind = match text(&a["kind"]) { "playlists" => "playlists", "artists" => "artists", _ => "songs" };
        if a["append"] == true {
            if self.jobs.contains_key("search") || !self.library.search.more || self.library.searched != query || self.library.search_kind != kind { return; }
        } else {
            self.cancel("search", out);
            self.library.search = Page::default();
            self.library.searched = query;
            self.library.search_kind = kind;
        }
        if self.library.searched.is_empty() { return; }
        self.library.search.error.clear();
        self.request("search", "search", json!({"query":self.library.searched,"kind":kind,"offset":self.library.search.offset}), 0, out);
    }

    fn load_collection_page(&mut self, out: &mut Update) {
        let page = &mut self.library.collection_tracks;
        if self.library.collection.is_null() || self.jobs.contains_key("collection") || (page.ready && !page.more && page.error.is_empty()) { return; }
        page.error.clear();
        let method = match text(&self.library.collection["kind"]) { "album" => "albumTracks", "artist" => "artistTracks", _ => "playlistTracks" };
        let request = json!({"id":self.library.collection["id"],"offset":page.offset});
        self.request("collection", method, request, 0, out);
    }

    pub(super) fn player_song_changed(&mut self, out: &mut Update) {
        if self.library.lyrics_visible { self.load_lyrics(out); }
    }

    fn load_lyrics(&mut self, out: &mut Update) {
        self.cancel("lyrics", out);
        self.library.lyrics.clear();
        if self.library.lyrics_visible && online_song(&self.player.song) {
            self.request("lyrics", "lyrics", json!({"id":self.player.song["id"]}), 0, out);
        }
        out.ui = true;
        out.progress = true;
    }

    pub(super) fn library_reply(&mut self, slot: &str, r: &Value, out: &mut Update) {
        out.ui = true;
        if let Some(error) = failure(r) {
            match slot {
                "search" => self.library.search.error = error.to_owned(),
                "playlists" => self.library.playlists.error = error.to_owned(),
                "albums" => self.library.albums.error = error.to_owned(),
                "likedTracks" => self.library.liked_tracks.error = error.to_owned(),
                "collection" => self.library.collection_tracks.error = error.to_owned(),
                "favorite" => { self.library.favorite_in_flight.clear(); self.message(error, true); }
                "lyrics" => self.message(error, true),
                _ => {},
            }
            return;
        }
        match slot {
            "search" => self.library.search.accept(r, 30, 10_000),
            "playlists" => { self.library.playlists.accept(r, 50, 10_000); self.activate_library(out); }
            "albums" => { self.library.albums.accept(r, 50, 100_000); self.activate_library(out); }
            "likedTracks" => self.library.liked_tracks.accept(r, 100, 100_000),
            "collection" => self.library.collection_tracks.accept(r, 100, 100_000),
            "favorite" => {
                let id = std::mem::take(&mut self.library.favorite_in_flight);
                if r["liked"] != self.library.favorite_target {
                    self.message("网易云未确认收藏状态", true);
                } else {
                    if self.library.favorite_target {
                        self.library.online_favorite.insert(id.clone());
                        self.library.like_order.retain(|value| value != &id);
                        self.library.like_order.insert(0, id);
                    } else {
                        self.library.online_favorite.remove(&id);
                        self.library.like_order.retain(|value| value != &id);
                    }
                    self.cancel("likedTracks", out);
                    self.library.liked_tracks = Page::default();
                    self.activate_library(out);
                }
            }
            "lyrics" => { self.library.lyrics = array(&r["lines"]); out.progress = true; }
            _ => {},
        }
    }
}