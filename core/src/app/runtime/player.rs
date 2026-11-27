use super::{array, number, text, App, Update};
use crate::protocol::online_song;
use crate::reconcile::neighbor;
use rand::seq::SliceRandom;
use rand::Rng;
use serde_json::{json, Value};

#[derive(Default)]
pub(super) struct Player {
    queue: Vec<Value>,
    order: Vec<usize>,
    at: Option<usize>,
    repeat: i32,
    shuffle: bool,
    pub(super) song: Value,
    token: u64,
    pub(super) position: u64,
    duration: u64,
    pub(super) playing: bool,
    pub(super) active: bool,
    buffering: bool,
    attached: bool,
    phase: String,
    in_room: bool,
    room_key: String,
    saved: Option<(Vec<Value>, Option<usize>, Value, u64, i32, bool, bool)>,
}

impl Player {
    pub fn tracks(&self) -> &[Value] { &self.queue }

    fn previous(&self) -> bool {
        !self.in_room && (self.target(-1, false).is_some() || (!self.song.is_null() && self.position > 3000))
    }

    pub fn presentation_song(&self, id: &str, index: usize) -> Option<&Value> {
        self.tracks().iter().find(|song| text(&song["id"]) == id).or_else(|| self.tracks().get(index))
    }

    pub fn view(&self, room: bool) -> Value {
        let usable = !room && !self.in_room;
        let previous = usable && self.previous();
        let next = usable && self.target(1, false).is_some();
        json!({"song":self.song,"queue":if usable {self.queue.clone()}else{Vec::<Value>::new()},"at":if usable {self.at}else{None},
            "active":self.active,"playing":self.playing,"buffering":self.buffering,"duration":self.duration,
            "position":self.position,"shuffle":usable && self.shuffle,"repeat":if usable {if self.repeat==1 {2}else if self.repeat==2 {1}else{0}}else{0},
            "roomMode":room || self.in_room,"previous":previous,"next":next,"token":self.token})
    }

    fn target(&self, direction: i32, ended: bool) -> Option<usize> {
        let at=self.at?;
        if self.shuffle {
            let i=self.order.iter().position(|&v|v==at)?;
            let n=neighbor(self.order.len(),i,direction,self.repeat,ended)?;
            self.order.get(n).copied()
        } else { neighbor(self.queue.len(),at,direction,self.repeat,ended) }
    }

    fn rebuild_shuffle(&mut self) {
        self.order.clear();
        if !self.shuffle { return; }
        if let Some(at)=self.at { self.order.push(at); }
        let mut rest:Vec<usize>=(0..self.queue.len()).filter(|i|Some(*i)!=self.at).collect();
        rest.shuffle(&mut rand::thread_rng());
        self.order.extend(rest);
    }

    fn step(&mut self,direction:i32,ended:bool)->bool {
        if let Some(next)=self.target(direction,ended) { self.at=Some(next); true } else { false }
    }

    pub fn load(&mut self,song:Value,position:u64,play:bool)->Value {
        self.room_key.clear();
        self.token=self.token.wrapping_add(1);
        self.song=song;
        self.position=position;
        self.duration=number(&self.song["duration"]);
        self.active=play && !self.song.is_null();
        self.playing=false;
        self.buffering=self.active && self.attached;
        self.phase.clear();
        self.audio(if self.song.is_null(){"clear"}else{"load"},json!({"song":self.song,
            "queue":if self.in_room {vec![self.song.clone()]}else{self.queue.clone()},
            "at":if self.in_room {0}else{self.at.unwrap_or(0)},"position":position,"play":self.active,"room":self.in_room}))
    }

    pub fn audio(&self,op:&str,data:Value)->Value {
        let mut e=json!({"kind":"audio","op":op,"token":self.token,"room":self.in_room});
        if let (Some(dst),Some(src))=(e.as_object_mut(),data.as_object()) { dst.extend(src.clone()); }
        if self.in_room && !op.eq("clear") { e["roomKey"]=json!(self.room_key); }
        e
    }

    fn observe(&mut self,a:&Value)->bool {
        if number(&a["token"])!=self.token || !self.attached || self.song.is_null(){return false;}
        self.position=number(&a["position"]);
        self.duration=number(&a["duration"]).max(number(&self.song["duration"]));
        self.playing=a["playing"]==true;
        self.active=a["active"]==true;
        self.buffering=a["buffering"]==true;
        self.phase=text(&a["phase"]).to_owned();
        true
    }

    fn enter_room(&mut self) {
        if self.in_room{return;}
        self.saved=Some((self.queue.clone(),self.at,self.song.clone(),self.position,self.repeat,self.shuffle,self.playing));
        self.in_room=true;
        self.load(Value::Null,0,false);
    }

    fn leave_room(&mut self)->Value {
        if !self.in_room{return Value::Null;}
        self.in_room=false;
        self.room_key.clear();
        if let Some((queue,at,song,position,repeat,shuffle,playing))=self.saved.take(){
            self.queue=queue;self.at=at;self.repeat=repeat;self.shuffle=shuffle;self.rebuild_shuffle();
            self.load(song,position,playing)
        }else{self.load(Value::Null,0,false)}
    }
}

impl App {
    pub(super) fn player_event(&mut self,name:&str,a:&Value,now:u64,out:&mut Update) {
        let room=self.room.active();
        let before = text(&self.player.song["id"]).to_owned();
        let mut effect=Value::Null;
        match name {
            "audioAttached"=>{
                self.player.attached=a["attached"]==true;
                if self.player.attached && !self.player.song.is_null() {
                    effect=self.player.audio("load",json!({"song":self.player.song,"queue":if room {vec![self.player.song.clone()]}else{self.player.queue.clone()},"at":if room {0}else{self.player.at.unwrap_or(0)},"position":self.player.position,"play":self.player.active,"roomKey":self.player.room_key}));
                }
                out.ui=true;out.player=true;
            }
            "audioPosition" => {
                if number(&a["token"]) == self.player.token && self.player.attached && !self.player.song.is_null() {
                    let previous = self.player.previous();
                    self.player.position = number(&a["position"]);
                    out.progress = true;
                    out.player |= previous != self.player.previous();
                }
            }
            "audioError" => {
                if number(&a["token"]) == self.player.token && self.player.attached && !self.player.song.is_null() {
                    self.player.active = false;
                    self.player.playing = false;
                    self.player.buffering = false;
                    self.message(text(&a["message"]), true);
                    out.ui = true;
                    out.player = true;
                }
            }
            "audioObserved"=>{
                if self.player.observe(a){
                    out.player=true;out.progress=true;

                    if !room && self.player.phase=="ended" {
                        self.player.phase.clear();
                        if self.player.step(1,true){effect=self.player.load(self.player.queue[self.player.at.unwrap()].clone(),0,true);}
                        else {self.player.active=false;self.player.playing=false;effect=self.player.audio("pause",Value::Null);}
                        out.player=true;
                    } else if room && self.player.phase=="ended" {
                        self.player.phase.clear();
                        self.room_event("roomBeatNow", &Value::Null, now, out);
                    }
                }
            }
            "playerPlayTracks" if !room=>{
                let songs = array(&a["songs"]);
                let at = (number(&a["at"]) as usize).min(songs.len().saturating_sub(1));
                if !songs.get(at).is_some_and(online_song) { return; }
                self.player.at = Some(songs[..at].iter().filter(|song| online_song(song)).count());
                self.player.queue = songs.into_iter().filter(online_song).collect();
                self.player.shuffle=a["shuffle"]==true;
                if self.player.shuffle && a["randomStart"] == true && self.player.at.is_some() {
                    self.player.at = Some(rand::thread_rng().gen_range(0..self.player.queue.len()));
                }
                self.player.rebuild_shuffle();
                effect=self.player.load(self.player.queue[self.player.at.unwrap()].clone(),0,true);
            }
            "playerToggle" | "playerTransport" => {
                let action = if name == "playerToggle" { "toggle" } else { text(&a["action"]) };
                let active = match action { "play" => true, "pause" => false, "toggle" => !self.player.active, _ => return };
                if room {
                    self.room_event("roomTransport", &json!({"playing":active}), now, out);
                } else if !self.player.song.is_null() {
                    self.player.active = active;
                    if !active { self.player.playing = false; self.player.buffering = false; }
                    effect = self.player.audio(if active { "play" } else { "pause" }, Value::Null);
                }
            }
            "playerNext" if !room=>if self.player.step(1,false){effect=self.player.load(self.player.queue[self.player.at.unwrap()].clone(),0,true)},
            "playerPrevious" if !room=>if self.player.position>3000 {self.player.position=0;effect=self.player.audio("seek",json!({"position":0}));}else if self.player.step(-1,false){effect=self.player.load(self.player.queue[self.player.at.unwrap()].clone(),0,true)},
            "playerSelect" | "uiPlay" if !room=>{
                let id=text(&a["id"]);
                let index=if id.is_empty(){Some(number(&a["index"]) as usize)}else{self.player.queue.iter().position(|s|text(&s["id"])==id)};
                if let Some(i)=index.filter(|&i|i<self.player.queue.len()){self.player.at=Some(i);if self.player.shuffle{self.player.rebuild_shuffle();}effect=self.player.load(self.player.queue[i].clone(),0,true);}
            }
            "playerEnqueue" if !room=>{
                let song=a["song"].clone();
                if online_song(&song){let old=self.player.at.unwrap_or(0);let insert=if a["next"]==true {old.saturating_add(1).min(self.player.queue.len())}else{self.player.queue.len()};
                    self.player.queue.insert(insert,song.clone());
                    if self.player.shuffle {for index in &mut self.player.order {if *index>=insert{*index+=1;}}let i=if a["next"]==true {self.player.order.iter().position(|&v|v==old).map_or(self.player.order.len(),|v|v+1)}else{self.player.order.len()};self.player.order.insert(i,insert);}
                    if self.player.song.is_null(){self.player.at=Some(insert);effect=self.player.load(song,0,false);}
                    out.player = true;
                }
            }
            "playerRemove" if !room=>{
                let i = number(&a["index"]) as usize;
                if i < self.player.queue.len() {
                    let current = self.player.at == Some(i);
                    self.player.queue.remove(i);self.player.order.retain(|&x|x!=i);for x in &mut self.player.order{if *x>i{*x-=1;}}
                    if self.player.queue.is_empty(){self.player.at=None;effect=self.player.load(Value::Null,0,false)}
                    else if current||self.player.song.is_null(){let at=i.min(self.player.queue.len()-1);self.player.at=Some(at);self.player.rebuild_shuffle();effect=self.player.load(self.player.queue[at].clone(),0,self.player.active);}
                    else if self.player.at.is_some_and(|at|at>i){self.player.at=self.player.at.map(|at|at-1);}
                    out.player = true;
                }
            }
            "playerSeek" if !room=>{self.player.position=number(&a["position"]).min(self.player.duration);self.player.token=self.player.token.wrapping_add(1);effect=self.player.audio("seek",json!({"position":self.player.position}));}
            "playerShuffle" | "playerShuffleToggle" if !room => {
                let enabled = if name == "playerShuffleToggle" { !self.player.shuffle } else { a["enabled"] == true };
                if self.player.shuffle != enabled { self.player.shuffle = enabled; self.player.rebuild_shuffle(); out.player = true; }
            }
            "playerRepeat" | "playerRepeatCycle" if !room => {
                self.player.repeat = if name == "playerRepeatCycle" { (self.player.repeat + 1) % 3 }
                    else { match number(&a["mode"]) { 1 => 2, 2 => 1, _ => 0 } };
                out.player = true;
            }
            "playerRoomLoad"=>{
                let song=a["song"].clone();
                if online_song(&song){
                    self.player.song=song;
                    self.player.in_room=true;
                    self.player.room_key=text(&a["key"]).to_owned();
                    self.player.position=number(&a["position"]);
                    self.player.duration=number(&self.player.song["duration"]);
                    self.player.phase.clear();
                    self.player.active=a["held"]!=true;
                    self.player.playing=false;
                    self.player.buffering=self.player.attached && self.player.active;
                    self.player.token=self.player.token.wrapping_add(1);
                    effect=self.player.audio("load",json!({"song":self.player.song,"queue":[self.player.song.clone()],"at":0,"position":self.player.position,"play":self.player.active,"room":true,"roomKey":a["key"]}));
                }
            }
            "playerRoomStop"=>{self.player.in_room=true;effect=self.player.load(Value::Null,0,false);}
            "playerRoomAlign"=>{self.player.position=number(&a["position"]);self.player.active=a["play"]==true;effect=self.player.audio("align",json!({"position":self.player.position,"play":self.player.active,"room":true}));}
            "playerRoomHold"=>{self.player.active=false;self.player.playing=false;self.player.buffering=false;effect=self.player.audio("pause",json!({"room":true}));}
            "playerEnterRoom"=>{self.player.enter_room();effect=self.player.audio("clear",Value::Null);}
            "playerExitRoom"=>{effect=self.player.leave_room();}
            _=>return,
        }
        if !effect.is_null(){out.effects.push(effect);out.player=true;}
        if before != text(&self.player.song["id"]) { self.player_song_changed(out); }
    }
}
