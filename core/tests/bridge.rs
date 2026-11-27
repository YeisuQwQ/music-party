use serde_json::{json, Value};
use yiqidi_core::native::dispatch;

#[test]
fn android_bridge_requests_load_and_ignores_completion_from_previous_track() {
    fn event(name: &str, data: Value) -> Value {
        let raw = json!({"event":name,"data":data}).to_string();
        serde_json::from_str(&dispatch("appEvent", &raw)).unwrap()
    }

    event("audioAttached", json!({"attached":true}));
    let queued = event("playerPlayTracks", json!({"songs":[{"id":"1"},{"id":"2"},{"id":"3"}],"at":0}));
    assert_eq!(queued["effects"][0]["song"]["id"], "1");
    let token = queued["effects"][0]["token"].clone();
    let advanced = event("playerNext", Value::Null);
    assert_eq!(advanced["player"]["song"]["id"], "2");
    let observed = event("audioObserved", json!({"token":token,"phase":"ended"}));
    assert!(observed["effects"].as_array().is_none_or(|a| a.is_empty()));
    assert_eq!(event("snapshot", Value::Null)["player"]["song"]["id"], "2");
    let current = advanced["effects"][0]["token"].clone();
    let ended = event("audioObserved", json!({"token":current,"phase":"ended"}));
    assert_eq!(ended["player"]["song"]["id"], "3");
}