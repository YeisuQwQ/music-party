use super::{array, failure, text, App, Update};
use crate::protocol::online_song;
use serde_json::{json, Map, Value};

#[derive(Default)]
pub(super) struct Room {
    snapshot: Value,
    queue: Vec<Value>,
    chat: Vec<Value>,
    preview: Value,
    invite_link: String,
    draft: String,

    public: bool,
    held: bool,
    resume_pending: bool,
    heartbeat_ms: u64,
    choice_song: Value,
    current_key: String,
    pending_key: String,
    pending_position: u64,
    previous: Option<crate::protocol::Playback>,
}

impl Room {
    pub fn active(&self) -> bool { self.snapshot.is_object() && !text(&self.snapshot["roomId"]).is_empty() }
    pub fn id(&self) -> &str { text(&self.snapshot["roomId"]) }
    pub fn queue(&self) -> &[Value] { &self.queue }
    pub fn playback_value(&self) -> Value { self.snapshot.get("playback").cloned().unwrap_or(Value::Null) }
    pub fn view(&self, busy: bool) -> Map<String, Value> {
        json!({"room":if self.active(){self.snapshot.clone()}else{Value::Null},"roomQueue":self.queue,
            "chat":self.chat,"chatDraft":self.draft,"roomBusy":busy,"roomHeld":self.held,"publicRoom":self.public,"roomChoiceSong":self.choice_song,
            "invitePreview":if self.preview.is_null(){Value::Null}else{self.preview.clone()}})
            .as_object().unwrap().clone()
    }
}

impl App {
    pub(super) fn room_busy(&self) -> bool {
        ["roomEntry", "roomAction", "roomInvite"].iter().any(|slot| self.jobs.contains_key(slot))
    }

    pub(super) fn room_event(&mut self, event: &str, a: &Value, _now: u64, out: &mut Update) {
        match event {
            "roomRefresh" => {
                if self.account.uid().is_empty() { self.message("请先登录网易云音乐", true); }
                else if !self.room_busy() { self.request("roomEntry", "roomStatus", json!({"uid":self.account.uid()}), 0, out); }
            }
            "roomCreate" | "roomJoin" => {
                if self.account.uid().is_empty() { self.message("请先登录网易云音乐", true); }
                else if self.room.active() { self.message("请先离开当前房间", true); }
                else if event == "roomCreate" && !online_song(&self.room.choice_song) { self.message("请先选择一首网易云在线歌曲", true); }
                else if event == "roomJoin" && self.room.preview.is_null() { self.message("请先预览好友邀请", true); }
                else if event == "roomJoin" && !self.room_joinable() {
                    let message = if text(&self.room.preview["status"]) == "AVAILABLE" { "不能加入自己创建的房间" } else { "邀请已失效，无法加入" };
                    self.message(message, true);
                }
                else if !self.room_busy() {
                    let (method, input) = if event == "roomCreate" {
                        ("roomCreate", json!({"id":self.room.choice_song["id"],"uid":self.account.uid(),"public":self.room.public}))
                    } else {
                        ("roomJoin", json!({"invite":self.room.invite_link,"uid":self.account.uid()}))
                    };
                    self.request("roomAction", method, input, 0, out);
                }
            }
            "roomLeave" => if self.room.active() && !self.room_busy() {
                self.request("roomAction", "roomLeave", json!({"roomId":self.room.id()}), 0, out);
            },
            "roomPreview" => {
                if self.room.active() || self.jobs.contains_key("roomAction") { return; }
                self.cancel("roomInvite", out);
                self.room.preview = Value::Null;
                self.room.invite_link.clear();
                let invite: String = text(&a["invite"]).trim().chars().take(4096).collect();
                match crate::protocol::parse_invite(&invite) {
                    Ok(i) => {
                        self.room.invite_link = crate::protocol::share_url(&i.room_id, &i.inviter_uid);
                        self.message("", false);
                        self.request("roomInvite", "roomPreview", json!({"invite":self.room.invite_link}), 0, out);
                    }
                    Err(error) => self.message(&error, true),
                }
            }
            "roomDismissInvite" => {
                self.cancel("roomInvite", out);
                self.room.preview = Value::Null;
                self.room.invite_link.clear();
            }
            "roomChooseSong" => {
                if !self.room.active() && online_song(&a["song"]) {
                    self.room.choice_song = a["song"].clone();
                    self.close_collection(out);
                    self.destination = "ROOMS";
                } else { self.message("好友房需要网易云在线歌曲", true); }
            }
            "roomShare" => {
                let invite = text(&self.room.snapshot["invite"]);
                if invite.starts_with("https://st.music.163.com/listen-together/multishare/index.html?") { out.effects.push(json!({"kind":"share","text":invite})); }
                else { self.message("房间尚无可分享的邀请链接", true); }
            }
            "roomPublic" => self.room.public = a["public"] == true,
            "roomQueueRefresh" => self.room_queue_request(out),
            "roomBeatNow" => {
                self.room_beat_request(0, out);
                self.room_queue_request(out);
            }
            "roomChatRefresh" => self.room_chat_request(out),
            "roomOperation" => {
                if !self.room.active() { return; }
                if !online_song(&a["song"]) { self.message("房间需要有效的网易云在线歌曲", true); }
                else if !self.room_busy() {
                    let operation = match text(&a["operation"]) { "add"=>"add", "next"=>"next", "up"=>"up", "like"=>"like", _=>"" };
                    if operation.is_empty() { self.message("不支持的房间操作", true); }
                    else {

                        self.request("roomAction", "roomOperation", json!({"roomId":self.room.id(),"id":a["song"]["id"],"bizId":a["bizId"],"operation":operation}), 0, out);
                    }
                }
            }
            "roomChatDraft" => self.room.draft = text(&a["text"]).chars().take(400).collect(),

            "roomChatSend" => if self.room.active() && !self.room.draft.trim().is_empty() && !self.jobs.contains_key("roomSend") {
                self.request("roomSend", "roomChatSend", json!({"roomId":self.room.id(),"text":self.room.draft.trim()}), 0, out);
            },
            "roomTransport" => if self.room.active() {
                self.room.held = a["playing"] != true;
                out.effects.push(self.player.audio(if self.room.held { "pause" } else { "play" }, Value::Null));
                if !self.room.held {
                    self.room.resume_pending = true;
                    self.cancel("roomBeat", out);
                    self.room_beat_request(0, out);
                }
                out.player = true;
            },
            "roomBeat" => if !self.jobs.contains_key("roomBeat") { self.room_beat_request(0, out); },
            _ => return,
        }
        out.ui = true;
    }

    fn room_joinable(&self) -> bool {
        if text(&self.room.preview["status"]) != "AVAILABLE" { return false; }
        crate::protocol::parse_invite(&self.room.invite_link).is_ok_and(|invite| invite.inviter_uid != self.account.uid())
    }

    fn room_queue_request(&mut self, out: &mut Update) {
        if self.room.active() && !self.jobs.contains_key("roomQueue") { self.request("roomQueue", "roomQueue", json!({"roomId":self.room.id()}), 0, out); }
    }

    fn room_chat_request(&mut self, out: &mut Update) {
        if self.room.active() && !self.jobs.contains_key("roomChat") { self.request("roomChat", "roomChatHistory", json!({"roomId":self.room.id(),"uid":self.account.uid()}), 0, out); }
    }

    fn room_beat_request(&mut self, delay: u64, out: &mut Update) {
        if self.room.active() {
            self.request("roomBeat", "roomHeartbeat", json!({"roomId":self.room.id(),"uid":self.account.uid()}), delay, out);
        }
    }

    pub(super) fn room_reply(&mut self, slot: &str, r: &Value, now: u64, out: &mut Update) {
        if let Some(error) = failure(r) {
            if r["code"] == 488 && self.room.active() { self.room_expired(out); }
            else {
                self.message(error, true);
                if slot == "roomBeat" { self.room_beat_request(self.room.heartbeat_ms.max(5_000), out); }
            }
            out.ui = true;
            return;
        }
        match slot {
            "roomEntry" => if r["room"].is_object() { self.room_accept(&r["room"], now, out); } else if self.room.active() { self.room_finish(out); },
            "roomAction" if r["room"].is_object() => self.room_accept(&r["room"], now, out),
            "roomAction" if r["left"] == true => self.room_finish(out),
            "roomAction" if r["accepted"] == true => {
                self.room_queue_request(out);
                self.cancel("roomBeat", out);
                self.room_beat_request(0, out);
            },
            "roomInvite" => {
                if crate::protocol::parse_invite(&self.room.invite_link).is_ok_and(|invite| invite.room_id == text(&r["roomId"])) {
                    let status = text(&r["status"]);
                    let joinable = status == "AVAILABLE" && crate::protocol::parse_invite(&self.room.invite_link)
                        .is_ok_and(|invite| invite.inviter_uid != self.account.uid());
                    self.room.preview = json!({"link":self.room.invite_link,"roomId":r["roomId"],"inviter":r["inviter"],"song":r["song"],"status":r["status"],"joinable":joinable});
                } else { self.message("服务端返回的房间与邀请不一致", true); }
            }
            "roomBeat" => {
                self.room.heartbeat_ms = crate::protocol::heartbeat_seconds(&r["interval"]) * 1000;
                if r["snapshot"].is_object() { self.room.snapshot = r["snapshot"].clone(); }
                if r["playback"].is_object() { self.room_plan(&r["playback"], now, out); }
                self.room_queue_request(out);
                self.room_chat_request(out);
                self.room_beat_request(self.room.heartbeat_ms, out);
            }
            "roomSong" if self.room.active() && !self.room.pending_key.is_empty() => {
                let song = r.get("song").unwrap_or(r);
                if song.is_object() && !song.is_null() {
                    let data = json!({"song":song,"key":self.room.pending_key,"position":self.room.pending_position,"held":self.room.held});
                    self.room.current_key = self.room.pending_key.clone();
                    self.player_event("playerRoomLoad", &data, now, out);
                }
            }
            "roomQueue" => self.room.queue = array(&r["items"]).into_iter().map(|item| json!({"song":item,"bizId":item["bizId"],"owner":item["owner"]})).collect(),
            "roomChat" => self.room.chat = array(&r["items"]),
            "roomSend" => { self.room.draft.clear(); self.room_chat_request(out); }
            _ => {}
        }
        out.ui = true;
        out.player = true;
    }

    fn room_accept(&mut self, snapshot: &Value, now: u64, out: &mut Update) {
        if text(&snapshot["roomId"]).is_empty() { self.message("网易云返回无效房间状态", true); return; }
        let changed = !self.room.active() || self.room.id() != text(&snapshot["roomId"]);
        self.room.snapshot = snapshot.clone();
        if changed {
            self.cancel("roomInvite", out);
            self.room.preview = Value::Null;
            self.room.invite_link.clear();
            self.room.choice_song = Value::Null;
            self.room.public = false;
            self.room.previous = None;
            self.room.current_key.clear();
            self.room.pending_key.clear();
            self.room.held = false;

            self.room.queue.clear();
            self.room.chat.clear();
            self.player_event("playerEnterRoom", &Value::Null, now, out);
            if snapshot["playback"].is_object() { self.room_plan(&snapshot["playback"], now, out); }
            self.room_beat_request(0, out);
            self.room_queue_request(out);
            self.room_chat_request(out);
        }
        self.message("", false);
    }

pub(super) fn room_finish(&mut self, out: &mut Update) {
        for slot in ["roomEntry", "roomBeat", "roomSong", "roomQueue", "roomChat", "roomAction", "roomSend", "roomInvite"] { self.cancel(slot, out); }
        self.room.snapshot = Value::Null;
        self.room.queue.clear();
        self.room.chat.clear();
        self.room.preview = Value::Null;
        self.room.invite_link.clear();
        self.room.choice_song = Value::Null;
        self.room.public = false;
        self.room.previous = None;
        self.room.current_key.clear();
        self.room.pending_key.clear();
        self.room.held = false;
        self.player_event("playerExitRoom", &Value::Null, 0, out);
        self.message("", false);
    }

    fn room_expired(&mut self, out: &mut Update) { self.room_finish(out); self.message("好友房已失效，已退出房间并恢复个人播放", true); }

    fn room_plan(&mut self, beat: &Value, now: u64, out: &mut Update) {
        let Ok(mut next) = serde_json::from_value::<crate::protocol::Playback>(beat.clone()) else { return; };
        if self.room.resume_pending && !next.song_id.is_empty() {
            next.force = true;
            self.room.resume_pending = false;
        }
        let input = crate::reconcile::SyncInput { previous:self.room.previous.clone(), next:Some(next.clone()), now, current_key:self.room.current_key.clone(), player_position:self.player.position, held:self.room.held, player_active:self.player.active };
        let plan = crate::reconcile::plan(&input);
        if !plan.accept { return; }
        self.room.previous = Some(next.clone());
        self.room.snapshot["playback"] = json!(next);
        match plan.action {
            "stop" => { self.cancel("roomSong", out); self.room.current_key.clear(); self.room.pending_key.clear(); self.player_event("playerRoomStop", &Value::Null, now, out); }
            "load" => { self.room.pending_key = plan.key; self.room.pending_position = plan.position;        self.request("roomSong", "song", json!({"id":next.song_id,"room":true}), 0, out); }
            "align" => self.player_event("playerRoomAlign", &json!({"position":plan.position,"play":!self.room.held}), now, out),
            "hold" => self.player_event("playerRoomHold", &Value::Null, now, out),
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn request(out: &Update, method: &str) -> Value {
        out.effects.iter().find(|e| e["method"] == method).unwrap().clone()
    }

    fn playback(version: u64, song: &str, at: u64) -> Value {
        json!({"songId":song,"bizId":"61","version":version,"position":3000,"duration":60000,
            "sampledAt":at,"force":false,"count":0,"likes":0})
    }

    #[test]
    fn a_stopped_room_does_not_start_an_in_flight_track() {
        let mut app = App::default();
        let mut entered = Update::default();
        app.room_accept(&json!({"roomId":"room-test","playback":playback(1,"24",1000)}), 1000, &mut entered);
        let track = request(&entered, "song");
        let beat = request(&entered, "roomHeartbeat");
        let stopped = app.event("work", &json!({"tag":beat["tag"],"result":{"playback":playback(2,"",2000)}}), 2000);
        assert!(stopped["effects"].as_array().unwrap().iter().any(|e| e["kind"] == "cancel" && e["slot"] == "roomSong"), "{stopped}");
        let late = app.event("work", &json!({"tag":track["tag"],"result":{"song":{"id":"24","duration":60000}}}), 2500);
        assert_eq!(late["effects"], json!([]));
        assert!(app.event("snapshot", &Value::Null, 2600)["player"]["song"].is_null());
    }

    #[test]
    fn room_entry_schedules_heartbeat_and_expiry_restores_personal_playback() {
        let mut app = App::default();
        app.event("playerPlayTracks", &json!({"songs":[{"id":"12","duration":60000},{"id":"15","duration":60000}],"at":1}), 900);
        app.event("playerSeek", &json!({"position":7000}), 950);
        let mut entered = Update::default();
        app.room_accept(&json!({"roomId":"room-test"}), 1_000, &mut entered);
        let first = entered.effects.iter().find(|e| e["method"] == "roomHeartbeat").expect("heartbeat on entry");
        assert_eq!(first["delay"], 0);
        let after_beat = app.event("work", &json!({"tag":first["tag"],"result":{"interval":5}}), 1_080);
        let next = after_beat["effects"].as_array().unwrap().iter()
            .find(|e| e["method"] == "roomHeartbeat").expect("next heartbeat");
        assert_eq!(next["delay"], 5_000);
        let expired = app.event("work", &json!({"tag":next["tag"],"result":{"code":488,"error":"expired"}}), 6_100);
        assert!(expired["ui"]["room"].is_null());
        assert!(expired["ui"]["error"].as_str().unwrap().contains("已失效"));
        assert_eq!(expired["player"]["song"]["id"], "15");
        assert_eq!(expired["player"]["queue"][0]["id"], "12");
        assert_eq!(expired["progress"]["position"], 7000);
        assert!(expired["effects"].as_array().unwrap().iter().all(|e| e["method"] != "roomHeartbeat"));
    }
}
