mod player;

use crate::protocol::{number, text};
use player::Player;
use serde_json::{json, Value};
use std::sync::{Mutex, OnceLock};

#[derive(Default)]
pub struct App {
    player: Player,
    account: Value,
    room: Value,
    room_queue: Vec<Value>,
    chat: Vec<Value>,
    lyrics: Vec<Value>,
    recent: Vec<Value>,
    liked: Vec<String>,
    local_liked: Vec<String>,
    offline_lists: Vec<Value>,
    online_playlists: Vec<Value>,
    online_playlist: Value,
    offline_songs: Vec<Value>,
    qr: Value,
    invite: Value,
    error: String,
    info: String,
    previous_beat: Option<Value>,
}

impl App {
    pub fn dispatch(&mut self, name: &str, a: &Value) -> Option<Value> {
        if name.starts_with("room") && !matches!(name, "roomActivate" | "roomUpdate" | "roomQueueUpdate" | "roomChatUpdate" | "roomFinish") {
            return Some(self.room_action(name, a));
        }
        if name.starts_with("player") || matches!(name, "audioAttached" | "audioObserved" | "audioCompleted" | "mediaAction") {
            return Some(self.player_action(name, a));
        }
        match name {
            "library" => Some(self.view()),
            "libraryBoot" => Some(json!({"songs":self.offline_songs,"lists":self.offline_lists,"recent":self.recent,"liked":self.liked,"account":self.account})),
            "librarySave" => { self.offline_songs = array(&a["songs"]); self.offline_lists = array(&a["lists"]); self.recent = array(&a["recent"]); self.local_liked = strings(&a["liked"]); if self.account.is_null() { self.liked=self.local_liked.clone(); } Some(self.view()) }
            "libraryLiked" => { self.liked=strings(&a["ids"]); self.liked.extend(self.local_liked.iter().cloned()); self.liked.sort(); self.liked.dedup(); Some(self.view()) }
            "localFavorite" => { let id=text(&a["id"]).to_owned(); if self.local_liked.iter().any(|v| v == &id) { self.local_liked.retain(|v| v != &id); } else { self.local_liked.push(id); } self.liked=self.local_liked.clone(); Some(self.view()) }
            "collection" => Some(self.collection_action(text(&a["op"]),a)),
            "libraryState" => { if a["account"].is_object() { self.account = a["account"].clone(); } else if a["account"].is_null() { self.account=Value::Null; self.liked=self.local_liked.clone(); } if a["error"].is_string() { self.error = text(&a["error"]).to_owned(); } if a["info"].is_string() { self.info = text(&a["info"]).to_owned(); } Some(self.view()) }
            "qrState" => { self.qr = a.clone(); Some(self.view()) }
            "inviteState" => { self.invite = a.clone(); Some(self.view()) }
            "roomState" => { if a["room"].is_object() { self.room = a["room"].clone(); } else if a["room"].is_null() { self.room = Value::Null; } if a["queue"].is_array() { self.room_queue = array(&a["queue"]); } if a["chat"].is_array() { self.chat = array(&a["chat"]); } Some(self.view()) }
            "setMessage" => { self.error = text(&a["error"]).to_owned(); self.info = text(&a["info"]).to_owned(); Some(self.view()) }
            "parseLyrics" => Some(json!({"lyrics":array(&a["lines"])})),
            _ => None,
        }
    }

    fn view(&self) -> Value {
        json!({"player":self.player.view(),"library":{"songs":self.offline_songs,"lists":self.offline_lists,
            "recent":self.recent,"liked":self.liked,"account":self.account,"onlinePlaylists":self.online_playlists,
            "onlinePlaylist":self.online_playlist,"lyrics":self.lyrics,"room":self.room,"roomQueue":self.room_queue,
            "chat":self.chat,"qr":self.qr,"invite":self.invite,"error":self.error,"info":self.info}})
    }

    fn player_action(&mut self, name: &str, a: &Value) -> Value {
        let mut effect = Value::Null;
        match name {
            "audioAttached" => { self.player.attached = a["attached"] == true; effect=self.player.effect("attach",json!({"attached":self.player.attached})); }
            "audioObserved" => { self.player.observe(a); }
            "playerPlayTracks" => if !self.player.in_room {
                let songs = array(&a["songs"]); let at = number(&a["at"]) as usize;
                let shuffle = a["shuffle"] == true;
                self.player.queue.replace(songs.clone(),at,shuffle);
                if let Some(index) = self.player.queue.at { effect=self.player.load(songs.get(index).cloned().unwrap_or_default(),0,true); }
            }
            "playerEnqueue" if !self.player.in_room => { let was_empty=self.player.song.is_null(); self.player.queue.enqueue(a["song"].clone(),a["next"] == true); if was_empty { self.player.queue.at=Some(0); effect=self.player.load(self.player.queue.song(),0,false); } }
            "playerRemove" if !self.player.in_room => { let index=number(&a["index"]) as usize; let current=self.player.queue.remove(index); if current { let song=self.player.queue.song(); effect=self.player.load(song,0,!self.player.queue.songs.is_empty()); } else if self.player.song.is_null() { effect=self.player.load(self.player.queue.song(),0,false); } }
            "playerSelect" if !self.player.in_room => { let index = if a["id"].is_string() { self.player.queue.songs.iter().position(|s| text(&s["id"]) == text(&a["id"])) } else { Some(number(&a["index"]) as usize) }; if let Some(i)=index.filter(|&i| i<self.player.queue.songs.len()) { self.player.queue.at=Some(i); effect=self.player.load(self.player.queue.song(),self.player.position,true); } }
            "playerToggle" => { if self.player.song.is_null() && !self.player.queue.songs.is_empty() { self.player.queue.at=Some(0); effect=self.player.load(self.player.queue.song(),0,true); } else { self.player.active=!self.player.active; self.player.token+=1; effect=self.player.effect(if self.player.active {"play"} else {"pause"},Value::Null); } }
            "playerSeek" if !self.player.in_room => { self.player.position=number(&a["position"]).min(self.player.duration); self.player.token+=1; effect=self.player.effect("seek",json!({"position":self.player.position})); }
            "playerPrevious" if !self.player.in_room => { if self.player.position>3000 { self.player.position=0; self.player.token+=1; effect=self.player.effect("seek",json!({"position":0})); } else { effect=self.step( -1,false); } }
            "playerNext" if !self.player.in_room => effect=self.step(1,false),
            "playerShuffle" if !self.player.in_room => { self.player.queue.shuffle(a["enabled"] == true); effect=self.player.effect("shuffle",json!({"enabled":self.player.queue.shuffled})); }
            "playerRepeat" if !self.player.in_room => { self.player.queue.repeat=number(&a["mode"]) as i32; effect=self.player.effect("repeat",json!({"mode":self.player.queue.repeat})); }
            "audioCompleted" if !self.player.in_room => { self.player.playing=false; self.player.active=false; effect=self.step(1,true); }
            "mediaAction" => { if !self.player.in_room { match text(&a["action"]) { "play"=>{self.player.active=true;effect=self.player.effect("play",Value::Null);},"pause"=>{self.player.active=false;effect=self.player.effect("pause",Value::Null);},"toggle"=>{self.player.active=!self.player.active;effect=self.player.effect(if self.player.active {"play"} else {"pause"},Value::Null);},"next"=>effect=self.step(1,false),"previous"=>effect=self.step(-1,false),_=>{} } } }
            "playerEnterRoom" => { if !self.player.in_room { self.player.saved_position=self.player.position; self.player.saved_song=self.player.song.clone(); self.player.saved_queue_at=self.player.queue.at; self.player.in_room=true; effect=self.player.load(Value::Null,0,false); self.player.in_room=true; } }
            "playerExitRoom" => { if self.player.in_room { self.player.in_room=false; effect=self.player.at_restore(); } }
            _ => {}
        }
        json!({"player":self.player.view(),"effect":effect})
    }

    fn step(&mut self, direction: i32, ended: bool) -> Value {
        if self.player.queue.step(direction,ended) { let song=self.player.queue.song(); self.player.load(song,0,true) }
        else if ended { self.player.active=false; self.player.playing=false; self.player.effect("pause",Value::Null) }
        else { Value::Null }
    }

    fn collection_action(&mut self, op: &str, a: &Value) -> Value {
        match op {
            "load" => { self.online_playlist=a["playlist"].clone(); self.online_playlist_tracks(Vec::new()); }
            "append" => self.online_playlist_tracks(array(&a["tracks"])),
            "close" => { self.online_playlist=Value::Null; self.online_playlist_tracks(Vec::new()); }
            "playlists" => self.online_playlists=array(&a["items"]),
            _=>{}
        }
        self.view()
    }

    fn online_playlist_tracks(&mut self, values: Vec<Value>) {
        if !self.online_playlist.is_object() { return; }
        self.online_playlist["items"] = json!(if self.online_playlist["items"].is_array() { let mut out=array(&self.online_playlist["items"]); out.extend(values); out } else { values });
    }

    fn room_action(&mut self, name: &str, a: &Value) -> Value {
        let mut effect=Value::Null;
        match name {
            "roomPreviewState" => self.invite=a.clone(),
            "roomActivate" => { self.room=a["room"].clone(); self.room_queue.clear(); self.chat.clear(); self.player.saved_position=self.player.position; self.player.saved_song=self.player.song.clone(); self.player.saved_queue_at=self.player.queue.at; self.player.in_room=true; effect=self.player.load(Value::Null,0,false); self.player.in_room=true; }
            "roomUpdate" => { let was=self.room.clone(); self.room=a["room"].clone(); if self.room.is_null() { self.room_queue.clear(); self.chat.clear(); if self.player.in_room { self.player.in_room=false; effect=self.player.at_restore(); } } else if !self.player.in_room { self.player.saved_position=self.player.position; self.player.saved_song=self.player.song.clone(); self.player.saved_queue_at=self.player.queue.at; self.player.in_room=true; effect=self.player.load(Value::Null,0,false); self.player.in_room=true; } else if was["roomId"]!=self.room["roomId"] { effect=self.player.load(Value::Null,0,false); self.player.in_room=true; } }
            "roomQueueUpdate" => self.room_queue=array(&a["items"]),
            "roomChatUpdate" => self.chat=array(&a["items"]),
            "roomFinish" => { self.room=Value::Null; self.room_queue.clear(); self.chat.clear(); }
            "roomState" => { if a["room"].is_object() { self.room=a["room"].clone(); } else if a["room"].is_null() { self.room=Value::Null; self.room_queue.clear(); self.chat.clear(); } if a["queue"].is_array() { self.room_queue=array(&a["queue"]); } if a["chat"].is_array() { self.chat=array(&a["chat"]); } }
            "roomInvitePreview" => { self.invite=a.clone(); }
            "roomInviteDismiss" => { self.invite=Value::Null; }
            "roomSync" => return self.room_sync(a),
            _=>{}
        }
        json!({"player":self.player.view(),"effect":effect,"library":{"room":self.room,"roomQueue":self.room_queue,"chat":self.chat,"invite":self.invite}})
    }
}

fn array(v: &Value) -> Vec<Value> { v.as_array().cloned().unwrap_or_default() }
fn strings(v: &Value) -> Vec<String> { v.as_array().into_iter().flatten().filter_map(Value::as_str).map(str::to_owned).collect() }

impl App {
    fn room_sync(&mut self, a: &Value) -> Value {
        let previous=self.previous_beat.clone();
        let beat=a.get("playback").filter(|v| v.is_object()).cloned().or_else(|| self.room["playback"].as_object().map(|_| self.room["playback"].clone()));
        let Ok(input) = serde_json::from_value::<crate::reconcile::SyncInput>(json!({
            "previous":previous,"next":beat,"now":number(&a["now"]),"currentKey":a["currentKey"],
            "playerPosition":number(&a["playerPosition"]),"held":a["held"]==true,"playerActive":a["playerActive"]==true
        })) else { return json!({"accept":false,"action":"keep"}); };
        let plan=crate::reconcile::plan(&input);
        if plan.accept { self.previous_beat=input.next.as_ref().map(|p|json!(p)); }
        let mut action=plan.action.to_owned();
        let mut effect=Value::Null;
        match action.as_str() {
            "stop" => { if self.player.in_room { effect=self.player.load(Value::Null,0,false); self.player.in_room=true; } }
            "load" => if a["song"].is_object() { self.player.in_room=true; effect=self.player.load(a["song"].clone(),plan.position,true); self.player.in_room=true; },
            "align" => { self.player.position=plan.position; effect=self.player.effect(if a["held"]==true {"pause"} else {"seek"},json!({"position":plan.position,"play":a["held"]!=true})); }
            "hold" => { effect=self.player.effect("pause",Value::Null); }
            _=>{}
        }
        json!({"accept":plan.accept,"action":action,"position":plan.position,"key":plan.key,"player":self.player.view(),"effect":effect})
    }
}

pub fn dispatch(name: &str, a: &Value) -> Option<Value> {
    static APP: OnceLock<Mutex<App>> = OnceLock::new();
    APP.get_or_init(||Mutex::new(App::default())).lock().ok()?.dispatch(name,a)
}