#[path = "runtime/account.rs"]
mod account;
#[path = "runtime/library.rs"]
mod library;
#[path = "runtime/player.rs"]
mod player;
#[path = "runtime/presentation.rs"]
mod presentation;
#[path = "runtime/room.rs"]
mod room;
#[cfg(test)]
#[path = "runtime/tests.rs"]
mod tests;

use crate::protocol::{number, text};
use account::AccountState;
use library::Library;
use player::Player;
use presentation::{Presentation, Track as PresentationTrack};
use room::Room;
use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};

#[derive(Default)]
struct App {
    player: Player,
    library: Library,
    account: AccountState,
    room: Room,
    presentation: Presentation,
    jobs: HashMap<&'static str, u64>,
    serial: u64,
    destination: &'static str,
    library_kind: &'static str,
    query: String,
    error: String,
    info: String,
    booted: bool,
    system_dark: bool,
    native_palette: Value,
}

#[derive(Default)]
struct Update {
    effects: Vec<Value>,
    ui: bool,
    player: bool,
    progress: bool,
}

impl App {
    fn event(&mut self, event: &str, a: &Value, now: u64) -> Value {
        let mut out = Update::default();
        match event {
            "snapshot" => { out.ui = true; out.player = true; }
            "uiSnapshot" => {
                if let Some(value) = a.get("dark").and_then(Value::as_bool) { self.system_dark = value; }
                if a["palette"].is_object() { self.native_palette = a["palette"].clone(); }
                return json!({"uiTree":crate::ui::render(&self.view(), &self.player.view(self.room.active()), self.player.position, self.library.active_line(self.player.position), self.system_dark, &self.native_palette)});
            }
            "uiPalette" => {
                self.native_palette = a.get("palette").filter(|value| value.is_object()).cloned().unwrap_or(Value::Null);
                self.system_dark = a["dark"] == true;
                return json!({"uiTree":crate::ui::render(&self.view(), &self.player.view(self.room.active()), self.player.position, self.library.active_line(self.player.position), self.system_dark, &self.native_palette)});
            }
            "boot" => if !self.booted {
                self.booted = true;
                if self.library.restore(&a["saved"]) { self.persist(&mut out); }
                self.restore_account(text(&a["session"]), &mut out);
                out.ui = true;
                out.player = true;
            },
            "restoreSession" => {
                self.restore_account(text(&a["session"]), &mut out);
                out.ui = true;
            },
            "work" => {
                let tag = number(&a["tag"]);
                if let Some(slot) = self.jobs.iter().find_map(|(&slot, &id)| (tag == id).then_some(slot)) {
                    self.jobs.remove(slot);
                    match slot {
                        "session" | "account" | "qr" | "liked" => self.account_reply(slot, &a["result"], now, &mut out),
                        "roomEntry" | "roomBeat" | "roomSong" | "roomQueue" | "roomChat" | "roomAction" | "roomSend" | "roomInvite" => self.room_reply(slot, &a["result"], now, &mut out),
                        _ => self.library_reply(slot, &a["result"], &mut out),
                    }
                }
            }
            "storageResult" => self.session_saved(a, &mut out),
            "navigate" => self.navigate(text(&a["page"]), &mut out),
            "selectKind" => {
                self.close_collection(&mut out);
                self.destination = "LIBRARY";
                self.library_kind = match text(&a["name"]) {
                    "ALBUMS" => "ALBUMS", "PLAYLISTS" => "PLAYLISTS", _ => "LIKED",
                };
                self.activate_library(&mut out);
                out.ui = true;
            }
            "query" => {
                self.query = text(&a["value"]).chars().take(160).collect();
                return json!({"input":{"query":self.query}});
            }
            "uiQuery" => {
                self.query = text(&a["value"]).chars().take(160).collect();
                out.ui = true;
            }
            "uiSearch" => { self.search_from_ui(a, &mut out); return self.finish_presentation(out, self.system_dark); }
            "uiBack" => return self.presentation_back(self.system_dark, &mut out),
            "clearMessage" => {
                if text(&a["value"]).is_empty() || self.error == a["value"] || self.info == a["value"] {
                    self.message("", false);
                    return json!({"input":{"error":"","info":""}});
                }
            }
            "platformError" => { self.message(text(&a["message"]), true); out.ui = true; }
            "uiQueuePlay" | "uiQueueRemove" | "uiRoomQueuePlay" | "uiRoomPublic" | "uiRoomInviteDraft" | "uiRoomPreview" | "uiShareRoom" | "uiRoomInvite" | "uiRoomJoin" | "uiRoomDraft" | "uiRoomSend" | "uiLeaveRequest" | "uiLeaveConfirm" | "uiCreateRoom" | "uiRestoreRoom" | "uiRoomRefresh" => {
                self.presentation_event(event, a, now, &mut out);
                self.sync_presentation(&mut out);
                return self.finish_presentation(out, self.system_dark);
            }
            name if name.starts_with("ui") => {
                self.presentation_event(name, a, now, &mut out);
                self.sync_presentation(&mut out);
                return self.finish_presentation(out, self.system_dark);
            }
            name if name.starts_with("player") || name.starts_with("audio") => self.player_event(name, a, now, &mut out),
            name if name.starts_with("room") => self.room_event(name, a, now, &mut out),
            "login" | "cancelLogin" | "logout" => self.account_event(event, &mut out),
            _ => self.library_event(event, a, &mut out),
        }
        let render = out.ui;
        let mut value = self.output(out);
        if render {
            let view = self.view();
            value["uiTree"] = crate::ui::render(&view, &self.player.view(self.room.active()), self.player.position,
                self.library.active_line(self.player.position), self.system_dark, &self.native_palette);
        }
        value
    }

    fn output(&self, out: Update) -> Value {
        let mut value = json!({"effects":out.effects});
        if out.ui { value["ui"] = self.view(); }
        if out.player { value["player"] = self.player.view(self.room.active()); }
        if out.player || out.progress {
            value["progress"] = json!({"position":self.player.position,"lyricLine":self.library.active_line(self.player.position)});
        }
        value
    }

    fn view(&self) -> Value {
        let mut view = self.library.view(self);
        let fields = view.as_object_mut().expect("library view is an object");
        fields.extend(self.account.view());
        fields.extend(self.room.view(self.room_busy()));
        let destination = if self.destination.is_empty() {"LIBRARY"} else {self.destination};
        fields.extend(json!({"destination":destination,
            "libraryKind":if self.library_kind.is_empty() {"LIKED"} else {self.library_kind},
            "query":self.query,"error":self.error,"info":self.info}).as_object().unwrap().clone());
        fields.insert("presentation".into(), self.presentation.view(&self.player.view(self.room.active())));
        view
    }

    fn navigate(&mut self, page: &str, out: &mut Update) {
        self.presentation.track = None;
        self.presentation.invite = false;
        self.presentation.invite_text.clear();
        self.close_collection(out);
        self.destination = match page {
            "SEARCH" => "SEARCH", "ROOMS" => "ROOMS", "SETTINGS" => "SETTINGS", _ => "LIBRARY",
        };
        self.message("", false);
        self.activate_library(out);
        out.ui = true;
    }

    fn presentation_back(&mut self, dark: bool, out: &mut Update) -> Value {
        let handled = self.presentation.back(self.library.has_collection(), self.destination, !self.player.song.is_null());
        if !handled {
            if self.library.has_collection() { self.library_event("closeCollection", &Value::Null, out); }
            else { self.navigate("LIBRARY", out); }
            return self.finish_presentation(std::mem::take(out), dark);
        }
        if !self.presentation.lyrics { self.library_event("lyricsVisible", &json!({"visible":false}), out); }
        self.finish_presentation(std::mem::take(out), dark)
    }

    fn finish_presentation(&self, out: Update, dark: bool) -> Value {
        let view = self.view();
        json!({"ui":if out.ui {view.clone()} else {Value::Null},
            "player":if out.player {self.player.view(self.room.active())} else {Value::Null},
            "progress":if out.player || out.progress {json!({"position":self.player.position,"lyricLine":self.library.active_line(self.player.position)})} else {Value::Null},
            "effects":out.effects,
            "uiTree":crate::ui::render(&view, &self.player.view(self.room.active()), self.player.position, self.library.active_line(self.player.position), dark, &self.native_palette)})
    }

    fn sync_presentation(&mut self, out: &mut Update) {
        let was_visible = self.presentation.lyrics;
        self.presentation.lyrics &= !self.player.song.is_null();
        if was_visible != self.presentation.lyrics {
            self.library_event("lyricsVisible", &json!({"visible":self.presentation.lyrics}), out);
        }
        self.presentation.full_player &= !self.player.song.is_null();
        if self.room.active() {
            self.presentation.invite = false;
            self.presentation.invite_text.clear();
        }
        let available = self.library.visible_tracks(self.destination, self.library_kind);
        if self.destination != "ROOMS" && self.presentation.track.as_ref().is_some_and(|track| !crate::protocol::online_song(&track.song) && available.iter().all(|song| text(&song["id"]) != text(&track.song["id"]))) {
            self.presentation.track = None;
        }
    }

    fn presentation_event(&mut self, event: &str, a: &Value, now: u64, out: &mut Update) {
        match event {
            "uiNavigate" => self.navigate(text(&a["page"]), out),
            "uiTrack" => {
                let songs = self.library.visible_tracks(self.destination, self.library_kind).to_vec();
                let id = text(&a["id"]);
                if crate::protocol::online_song(&a["song"]) && (id.is_empty() || id == text(&a["song"]["id"])) {
                    let song = a["song"].clone();
                    self.presentation.track = Some(PresentationTrack {song:song.clone(),songs:vec![song],at:0,details:false});
                } else if let Some(at) = songs.iter().position(|song| text(&song["id"]) == id).or_else(|| {
                    let index = number(&a["at"]) as usize;
                    (index < songs.len()).then_some(index)
                }) {
                    self.presentation.track = Some(PresentationTrack {song:songs[at].clone(),songs,at,details:false});
                } else if crate::protocol::online_song(&a["song"]) {
                    let song = a["song"].clone();
                    self.presentation.track = Some(PresentationTrack {song:song.clone(),songs:vec![song],at:0,details:false});
                }
            }
            "uiDismiss" => {
                self.presentation.dismiss();
                if !self.presentation.lyrics { self.library_event("lyricsVisible", &json!({"visible":false}), out); }
            }
            "uiToggleFullPlayer" => if !self.player.song.is_null() { self.presentation.full_player = !self.presentation.full_player; },
            "uiToggleQueue" => self.presentation.queue = !self.presentation.queue,
            "uiToggleLyrics" => {
                self.presentation.lyrics = !self.presentation.lyrics && !self.player.song.is_null();
                self.library_event("lyricsVisible", &json!({"visible":self.presentation.lyrics}), out);
            }
            "uiLyricsMode" => self.library_event("lyricMode", &json!({"static":a["static"]}), out),
            "uiLyricPrefs" => self.library_event("lyricPrefs", a, out),
            "uiTheme" => self.library_event("theme", a, out),
            "uiTrackDetails" => if let Some(track) = &mut self.presentation.track { track.details = true; },
            "uiPlay" => {
                let payload = &a["song"];
                let payload_id = text(&payload["id"]);
                let track = self.presentation.track.clone().filter(|track| payload_id.is_empty() || text(&track.song["id"]) == payload_id).or_else(|| {
                    let songs = self.library.visible_tracks(self.destination, self.library_kind).to_vec();
                    let id = if payload["id"].is_string() { text(&payload["id"]) } else { text(&a["id"]) };
                    if let Some(at) = songs.iter().position(|song| text(&song["id"]) == id) {
                        Some(PresentationTrack {song:songs[at].clone(),songs,at,details:false})
                    } else if crate::protocol::online_song(payload) {
                        let song = payload.clone();
                        Some(PresentationTrack {song:song.clone(),songs:vec![song],at:0,details:false})
                    } else {
                        let at = number(&a["at"]) as usize;
                        (at < songs.len()).then(|| PresentationTrack {song:songs[at].clone(),songs,at,details:false})
                    }
                });
                if let Some(track) = track {
                    self.presentation.track = None;
                    self.player_event("playerPlayTracks", &json!({"songs":track.songs,"at":track.at}), now, out);
                }
            }
            "uiPlayAll" => {
                let songs = self.library.visible_tracks(self.destination, self.library_kind).to_vec();
                if !songs.is_empty() { self.player_event("playerPlayTracks", &json!({"songs":songs,"at":0,"shuffle":a["shuffle"]}), now, out); }
            }
            "uiFavorite" => if let Some(track) = &self.presentation.track { self.library_event("favorite", &json!({"song":track.song}), out); },
            "uiChooseRoom" => if let Some(track) = self.presentation.track.clone() {
                self.presentation.track = None;
                self.room_event("roomChooseSong", &json!({"song":track.song}), now, out);
            },
            "uiEnqueue" | "uiPlayNext" => if let Some(track) = self.presentation.track.clone() {
                self.presentation.track = None;
                self.player_event("playerEnqueue", &json!({"song":track.song,"next":event=="uiPlayNext"}), now, out);
            },
            "uiRoomAdd" => if let Some(track) = self.presentation.track.clone() {
                self.presentation.track = None;
                self.room_event("roomOperation", &json!({"song":track.song,"bizId":"0","operation":"add"}), now, out);
            },
            "uiAddToPlaylist" => if let Some(track) = &self.presentation.track {
                out.effects.push(json!({"kind":"playlistPicker","song":track.song}));
            },
            "uiRoomSkip" | "uiRoomLike" => if let Some(track) = self.presentation.track.clone() {
                let biz = text(&self.room.playback_value()["bizId"]).to_owned();
                let song = if track.song.is_object() { track.song } else { self.player.song.clone() };
                self.presentation.track = None;
                self.room_event("roomOperation", &json!({"song":song,"bizId":biz,
                    "operation":if event=="uiRoomSkip"{"next"}else{"like"}}), now, out);
            },
            "uiShareTrack" => if let Some(track) = &self.presentation.track {
                out.effects.push(json!({"kind":"share","text":format!("{} - {}",text(&track.song["title"]),text(&track.song["artist"]))}));
            },
            "uiQueuePlay" => {
                let index = number(&a["at"]) as usize;
                if self.room.active() {
                    let row = self.room.queue().get(index).cloned();
                    if let Some(row) = row {
                        let song = row.get("song").unwrap_or(&row).clone();
                        self.room_event("roomOperation", &json!({"song":song,"bizId":row["bizId"],"operation":"next"}), now, out);
                    }
                } else if let Some(index) = self.player.tracks().iter().position(|song| {
                    if a["id"].is_string() { text(&song["id"]) == text(&a["id"]) } else { false }
                }).or_else(|| (index < self.player.tracks().len()).then_some(index)) {
                    self.player_event("playerSelect", &json!({"index":index}), now, out);
                }
            }
            "uiRoomQueuePlay" => {
                let index = number(&a["at"]) as usize;
                if let Some(row) = self.room.queue().get(index).cloned() {
                    let song = row.get("song").unwrap_or(&row).clone();
                    self.room_event("roomOperation", &json!({"song":song,"bizId":row["bizId"],"operation":"next"}), now, out);
                }
            }
            "uiQueueRemove" => self.player_event("playerRemove", &json!({"index":a["at"]}), now, out),
            "uiRoomPublic" => self.room_event("roomPublic", &json!({"public":a["value"]}), now, out),
            "uiRoomInviteDraft" => {
                self.presentation.invite = true;
                self.presentation.invite_text = text(&a["value"]).chars().take(4096).collect();
                self.room_event("roomDismissInvite", a, now, out);
            }
            "uiRoomPreview" => self.room_event("roomPreview", &json!({"invite":self.presentation.invite_text}), now, out),
            "uiShareRoom" => self.room_event("roomShare", a, now, out),
            "uiRoomInvite" => { self.presentation.invite = true; self.presentation.invite_text.clear(); }
            "uiRoomJoin" => {
                self.room_event("roomJoin", a, now, out);
                if !self.room.active() && !self.room_busy() { self.presentation.invite = false; self.presentation.invite_text.clear(); }
            }
            "uiRoomDraft" => self.room_event("roomChatDraft", &json!({"text":a["value"]}), now, out),
            "uiRoomSend" => self.room_event("roomChatSend", a, now, out),
            "uiLeaveRequest" => self.presentation.leave_confirmation = true,
            "uiLeaveConfirm" => {
                self.presentation.leave_confirmation = false;
                self.room_event("roomLeave", a, now, out);
            }
            "uiCreateRoom" => self.room_event("roomCreate", a, now, out),
            "uiRestoreRoom" | "uiRoomRefresh" => self.room_event("roomRefresh", a, now, out),
            "uiSearch" => self.search_from_ui(a, out),
            "uiSearchMore" => self.search_from_ui(&json!({"kind":a["kind"],"append":true,"results":true}), out),
            "uiClearSearch" => self.search_from_ui(&json!({"kind":a["kind"],"clear":true}), out),
            "uiRefreshLibrary" => self.library_event("refreshLibrary", a, out),
            "uiMoreLiked" => self.library_event("loadMoreLiked", a, out),
            "uiMoreAlbums" => self.library_event("loadMoreAlbums", a, out),
            "uiMoreCollection" => self.library_event("loadMoreCollection", a, out),
            "uiPlaylistFilter" => self.library_event("libraryFilter", &json!({"value":a["value"]}), out),
            "uiPlaylistQuery" => self.library_event("libraryQuery", &json!({"value":a["value"]}), out),
            "uiAlbumQuery" => self.library_event("albumQuery", &json!({"value":a["value"]}), out),
            "uiSearchKind" => self.search_from_ui(&json!({"kind":a["kind"]}), out),
            "uiLibraryKind" => {
                self.close_collection(out);
                self.destination = "LIBRARY";
                self.library_kind = match text(&a["name"]) {"ALBUMS"=>"ALBUMS","PLAYLISTS"=>"PLAYLISTS",_=>"LIKED"};
                self.activate_library(out);
            }
            "uiLyricPrefsAlign" => self.library_event("lyricPrefs", &json!({"align":a["value"],"shift":self.library.view(self)["lyricPrefs"]["shift"],"awake":self.library.view(self)["lyricPrefs"]["awake"]}), out),
            "uiLyricPrefsShift" => self.library_event("lyricPrefs", &json!({"align":self.library.view(self)["lyricPrefs"]["align"],"shift":(a["value"].as_f64().unwrap_or(0.5)*10000.0-5000.0) as i64,"awake":self.library.view(self)["lyricPrefs"]["awake"]}), out),
            "uiLyricPrefsAwake" => self.library_event("lyricPrefs", &json!({"align":self.library.view(self)["lyricPrefs"]["align"],"shift":self.library.view(self)["lyricPrefs"]["shift"],"awake":a["value"]}), out),
            "uiOpenCollection" => self.library_event("openCollection", a, out),
            "uiCloseCollection" => self.library_event("closeCollection", a, out),
            "uiLogin" => self.account_event("login", out),
            "uiCancelLogin" => self.account_event("cancelLogin", out),
            "uiLogout" => self.account_event("logout", out),
            _ => {}
        }
        out.ui = true;
    }

    fn next_tag(&mut self) -> u64 { self.serial += 1; self.serial }

    fn request(&mut self, slot: &'static str, method: &str, input: Value, delay: u64, out: &mut Update) {
        let tag = self.next_tag();
        self.jobs.insert(slot, tag);
        out.effects.push(json!({"kind":"request","slot":slot,"tag":tag,"method":method,"input":input,"delay":delay}));
    }

    fn cancel(&mut self, slot: &str, out: &mut Update) {
        if self.jobs.remove(slot).is_some() { out.effects.push(json!({"kind":"cancel","slot":slot})); }
    }

    fn message(&mut self, text: &str, error: bool) {
        self.error.clear();
        self.info.clear();
        if error { self.error.push_str(text); } else { self.info.push_str(text); }
    }

    fn persist(&self, out: &mut Update) {
        out.effects.push(json!({"kind":"persist","data":self.library.saved()}));
    }
}

fn array(v: &Value) -> Vec<Value> { v.as_array().cloned().unwrap_or_default() }

fn failure(v: &Value) -> Option<&str> { v.get("error").and_then(Value::as_str) }

pub(crate) fn dispatch(a: &Value) -> Value {
    static APP: OnceLock<Mutex<App>> = OnceLock::new();
    APP.get_or_init(|| Mutex::new(App::default()))
        .lock().unwrap_or_else(|e| e.into_inner())
        .event(text(&a["event"]), &a["data"], crate::native::room_time_ms())
}