use serde_json::{json, Value};

#[derive(Clone, Default)]
pub(super) struct Track {
    pub song: Value,
    pub songs: Vec<Value>,
    pub at: usize,
    pub details: bool,
}

#[derive(Default)]
pub(super) struct Presentation {
    pub full_player: bool,
    pub lyrics: bool,
    pub queue: bool,
    pub track: Option<Track>,
    pub invite: bool,
    pub invite_text: String,
    pub leave_confirmation: bool,
}

impl Presentation {
    pub fn back(&mut self, collection: bool, page: &str, has_song: bool) -> bool {
        if self.leave_confirmation { self.leave_confirmation = false; return true; }
        if let Some(track) = &mut self.track {
            if track.details { track.details = false; }
            else { self.track = None; }
            return true;
        }
        if self.queue { self.queue = false; return true; }
        if self.invite { self.invite = false; self.invite_text.clear(); return true; }
        if self.lyrics { self.lyrics = false; return true; }
        if self.full_player && has_song { self.full_player = false; return true; }
        if collection { return false; }
        if page == "SEARCH" { return false; }
        page != "LIBRARY"
    }

    pub fn dismiss(&mut self) {
        if self.leave_confirmation { self.leave_confirmation = false; }
        else if self.track.is_some() { self.track = None; }
        else if self.queue { self.queue = false; }
        else if self.invite { self.invite = false; self.invite_text.clear(); }
        else if self.lyrics { self.lyrics = false; }
        else if self.full_player { self.full_player = false; }
    }

    pub fn view(&self, player: &Value) -> Value {
        let track = self.track.as_ref().map(|track| json!({"song":track.song,"details":track.details,"at":track.at,
            "songs":track.songs.iter().map(|song| song["id"].clone()).collect::<Vec<_>>() }));
        json!({
            "fullPlayer":self.full_player && player["song"].is_object(),
            "lyrics":self.lyrics && player["song"].is_object(),
            "queue":self.queue,
            "track":track,
            "invite":self.invite,
            "inviteText":self.invite_text,
            "leaveConfirmation":self.leave_confirmation,
        })
    }
}