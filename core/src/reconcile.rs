use crate::protocol::Playback;
use serde::{Deserialize, Serialize};

#[derive(Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncInput {
    pub previous: Option<Playback>,
    pub next: Option<Playback>,
    pub now: u64,
    pub current_key: String,
    pub player_position: u64,
    pub held: bool,
    pub player_active: bool,
}

#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncPlan {
    pub accept: bool,
    pub action: &'static str,
    pub position: u64,
    pub key: String,
}

pub fn plan(input: &SyncInput) -> SyncPlan {
    let mut result = SyncPlan { accept: false, action:"keep", position:0, key:String::new() };
    let Some(next) = &input.next else { return result; };
    if input.previous.as_ref().is_some_and(|p| p.version > next.version || p.sampled_at > next.sampled_at) { return result; }
    result.accept = true;
    if next.song_id.is_empty() {
        result.action = "stop";
        return result;
    }
    result.key = format!("{}:{}", next.song_id, next.biz_id);
    let position = next.position.saturating_add(input.now.saturating_sub(next.sampled_at));
    result.position = if next.duration > 0 { position.min(next.duration.saturating_sub(100)) } else { position };
    result.action = if result.key != input.current_key { "load" }
    else if input.held { "hold" }
    else if !input.player_active || next.force || input.player_position.abs_diff(result.position) > 1400 { "align" }
    else { "keep" };
    result
}

pub fn neighbor(size: usize, at: usize, direction: i32, repeat: i32, natural: bool) -> Option<usize> {
    if size == 0 || at >= size { return None; }
    if natural && repeat == 2 { return Some(at); }
    if direction < 0 {
        at.checked_sub(1).or_else(|| (repeat == 1).then_some(size - 1))
    } else if at + 1 < size { Some(at + 1) } else { (repeat == 1).then_some(0) }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn beat(version: u64, sampled_at: u64, biz_id: &str) -> Playback {
        Playback { song_id:"21".into(), biz_id:biz_id.into(), version, position:3000, duration:6000, sampled_at, force:false, count:4, likes:0 }
    }

    #[test]
    fn stale_beat_cannot_restart_room_playback() {
        let i = SyncInput { previous:Some(beat(8,500,"5")), next:Some(beat(7,600,"3")), now:700, ..Default::default() };
        assert!(!plan(&i).accept);
    }

    #[test]
    fn repeated_track_is_a_distinct_room_entry_and_clamps_to_end() {
        let i = SyncInput { previous:Some(beat(8,500,"5")), next:Some(beat(9,700,"6")), now:5000, current_key:"21:5".into(), held:true, ..Default::default() };
        let p = plan(&i);
        assert_eq!((p.action,p.position,p.key.as_str()),("load",5900,"21:6"));
    }

    #[test]
    fn local_hold_does_not_turn_into_remote_resume() {
        let i = SyncInput { next:Some(beat(9,1000,"6")), now:1750, current_key:"21:6".into(), held:true, ..Default::default() };
        assert_eq!(plan(&i).action,"hold");
        assert_eq!(plan(&SyncInput { held:false, ..i }).action,"align");
    }

    #[test]
    fn repeat_one_applies_to_natural_completion_not_next_button() {
        assert_eq!(neighbor(5,3,1,2,true),Some(3));
        assert_eq!(neighbor(5,3,1,2,false),Some(4));
        assert_eq!(neighbor(5,4,1,0,true),None);
        assert_eq!(neighbor(5,4,1,1,true),Some(0));
    }
}