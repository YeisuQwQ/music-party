use crate::protocol::{number, text};
use crate::reconcile::neighbor;
use rand::seq::SliceRandom;
use serde_json::{json, Value};

#[derive(Default)]
pub(super) struct Queue {
    pub songs: Vec<Value>,
    pub at: Option<usize>,
    pub repeat: i32,
    order: Vec<usize>,
    pub shuffled: bool,
}

impl Queue {
    pub fn step(&mut self, direction: i32, ended: bool) -> bool {
        let next = self.target(direction, ended);
        if let Some(next) = next {
            self.at = Some(next);
            true
        } else {
            false
        }
    }

    pub fn target(&self, direction: i32, ended: bool) -> Option<usize> {
        let at = self.at?;
        if self.shuffled {
            let step = neighbor(self.order.len(), self.order.iter().position(|&i| i == at)?, direction, self.repeat, ended)?;
            self.order.get(step).copied()
        } else {
            neighbor(self.songs.len(), at, direction, self.repeat, ended)
        }
    }

    pub fn replace(&mut self, songs: Vec<Value>, index: usize, shuffled: bool) {
        self.songs = songs;
        self.at = (!self.songs.is_empty()).then_some(index.min(self.songs.len().saturating_sub(1)));
        self.set_shuffle(shuffled, false);
    }

    pub fn shuffle(&mut self, enabled: bool) {
        self.set_shuffle(enabled, true);
    }

    fn set_shuffle(&mut self, enabled: bool, reshuffle: bool) {
        if self.shuffled == enabled && !reshuffle { return; }
        self.shuffled = enabled;
        self.order.clear();
        if enabled {
            if let Some(at) = self.at { self.order.push(at); }
            let mut remaining: Vec<usize> = (0..self.songs.len()).filter(|i| Some(*i) != self.at).collect();
            remaining.shuffle(&mut rand::thread_rng());
            self.order.extend(remaining);
        }
    }

    pub fn enqueue(&mut self, song: Value, next: bool) {
        let len = self.songs.len();
        let index = if next && !self.shuffled { self.at.map_or(len, |at| at + 1) } else { len };
        self.songs.insert(index, song);
        if self.shuffled {
            let to = if next { self.at.and_then(|at| self.order.iter().position(|&i| i == at)).map_or(self.order.len(), |i| i + 1) } else { self.order.len() };
            self.order.insert(to, index);
        }
        if self.at.is_none() { self.at = Some(0); }
    }

    pub fn remove(&mut self, at: usize) -> bool {
        if at >= self.songs.len() { return false; }
        let current = self.at == Some(at);
        let successor = if current { self.target(1, false).filter(|&i| i != at) } else { self.at };
        self.songs.remove(at);
        self.order.retain(|&i| i != at);
        self.order.iter_mut().filter(|i| **i > at).for_each(|i| *i -= 1);
        self.at = successor.map(|i| if i > at { i - 1 } else { i })
            .or_else(|| (!self.songs.is_empty()).then_some(at.min(self.songs.len().saturating_sub(1))));
        current
    }

    pub fn song(&self) -> Value { self.at.and_then(|i| self.songs.get(i)).cloned().unwrap_or_default() }
}

#[derive(Default)]
pub(super) struct Player {
    pub queue: Queue,
    pub in_room: bool,
    pub song: Value,
    pub token: u64,
    pub position: u64,
    pub duration: u64,
    pub playing: bool,
    pub active: bool,
    pub buffering: bool,
    pub phase: String,
    pub attached: bool,
    pub saved_position: u64,
    pub saved_song: Value,
    pub saved_queue_at: Option<usize>,
}

impl Player {
    pub fn view(&self) -> Value {
        let q = &self.queue;
        json!({"song":self.song, "queue": if self.in_room { &[][..] } else { &q.songs },
            "at": if self.in_room { None } else { q.at }, "active":self.active, "playing":self.playing,
            "buffering":self.buffering, "duration":self.duration, "position":self.position, "shuffle":!self.in_room && q.shuffled,
            "repeat":if self.in_room {0} else {q.repeat}, "roomMode":self.in_room,
            "previous":!self.in_room && (q.target(-1, false).is_some() || (!self.song.is_null() && self.position > 3000)),
            "next":!self.in_room && q.target(1, false).is_some()})
    }

    pub fn load(&mut self, song: Value, position: u64, play: bool) -> Value {
        self.token += 1;
        self.position = position;
        self.duration = number(&song["duration"]);
        self.playing = false;
        self.active = play && !song.is_null();
        self.buffering = self.active && self.attached;
        self.phase.clear();
        self.song = song;
        json!({"kind":"audio", "op":if self.song.is_null() {"clear"} else {"load"},
            "token":self.token, "song":self.song, "queue":if self.in_room {vec![self.song.clone()]} else {self.queue.songs.clone()},
            "at":if self.in_room {0} else {self.queue.at.unwrap_or(0)}, "position":position, "play":self.active, "room":self.in_room})
    }

    pub fn observe(&mut self, a: &Value) -> bool {
        if number(&a["token"]) != self.token || !self.attached || self.song.is_null() { return false; }
        self.position = number(&a["position"]);
        self.duration = a["duration"].as_u64().filter(|&d| d > 0).unwrap_or_else(|| number(&self.song["duration"]));
        self.playing = a["playing"] == true;
        self.active = a["active"] == true;
        self.buffering = a["buffering"] == true;
        self.phase = text(&a["phase"]).to_owned();
        true
    }

    pub fn at_restore(&mut self) -> Value {
        self.queue.at = self.saved_queue_at;
        self.load(self.saved_song.clone(), self.saved_position, !self.saved_song.is_null())
    }

    pub fn effect(&mut self, op: &str, extra: Value) -> Value {
        let mut value = json!({"kind":"audio","op":op,"token":self.token,"room":self.in_room});
        if let (Some(out), Some(add)) = (value.as_object_mut(), extra.as_object()) {
            out.extend(add.clone());
        }
        value
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn repeat_one_reloads_on_end_but_next_selects_another_track() {
        let mut q = Queue { songs: vec![json!({"id":"1"}), json!({"id":"2"})], at: Some(0), repeat: 2, ..Default::default() };
        assert!(q.step(1, true));
        assert_eq!(q.at, Some(0));
        assert!(q.step(1, false));
        assert_eq!(q.at, Some(1));
        assert!(!q.step(1, false));
    }
}