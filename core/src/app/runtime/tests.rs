use super::*;

fn payload(reply: &Value, method: &str) -> Value {
    reply["effects"].as_array().unwrap().iter().find(|e| e["method"] == method).unwrap().clone()
}

fn signed_in_app() -> App {
    let mut app = App::default();
    let restore = payload(&app.event("boot", &json!({"session":"fixture"}), 1), "setSession");
    app.event("navigate", &json!({"page":"SEARCH"}), 1);
    let account = payload(&app.event("work", &json!({"tag":restore["tag"],"result":{}}), 2), "account");
    let room = payload(&app.event("work", &json!({"tag":account["tag"],"result":{"loggedIn":true,"uid":"21"}}), 3), "roomStatus");
    app.event("work", &json!({"tag":room["tag"],"result":{"room":null}}), 4);
    app
}

#[test]
fn rust_material_layout_exposes_search_results_and_the_real_mini_player() {
    let ui = json!({
        "destination":"SEARCH", "query":"春日影", "searchedQuery":"春日影", "searchKind":"songs",
        "account":{"uid":"fixture","name":"听友"}, "liked":["58"], "likesReady":true,
        "searchSongs":[{"id":"58","title":"春日影","artist":"MyGO!!!!!","album":"迷跡波","cover":"https://music.163.com/cover.png","duration":240000}],
        "searchCollections":[], "searching":false, "searchError":"", "canSearchMore":false,
        "libraryKind":"LIKED", "theme":0
    });
    let player = json!({
        "song":{"id":"58","title":"春日影","artist":"MyGO!!!!!","duration":240000},
        "playing":true,"buffering":false,"duration":240000,"shuffle":false,"repeat":0,"roomMode":false
    });
    let tree = crate::ui::render(&ui, &player, 32_000, -1, false, &Value::Null);
    assert_eq!(tree["type"], "app");
    assert_eq!(tree["page"], "SEARCH");
    assert_eq!(tree["content"]["children"][0]["children"][0]["value"], "春日影");
    assert_eq!(tree["content"]["children"][3]["children"][0]["title"], "春日影");
    assert_eq!(tree["miniPlayer"]["song"]["id"], "58");
    let tokens = tree["theme"].as_object().unwrap();
    assert_eq!(tokens["mode"], "light");
    let palette = json!({"light":{"primary":"#123456","onPrimary":"#FFFFFF"}});
    let themed = crate::ui::render(&ui, &player, 0, -1, false, &palette);
    assert_eq!(themed["theme"]["primary"], "#123456");
    assert_eq!(themed["theme"]["onPrimary"], "#FFFFFF");
}

#[test]
fn ui_snapshot_round_trips_existing_player_state_into_the_render_tree() {
    let mut app = App::default();
    app.event("audioAttached", &json!({"attached":true}), 9);
    let loaded = app.event("playerPlayTracks", &json!({"songs":[{"id":"58","title":"春日影","duration":240000}],"at":0}), 10);
    app.event("audioPosition", &json!({"token":loaded["player"]["token"],"position":32000}), 20);
    let snapshot = app.event("uiSnapshot", &Value::Null, 50);
    assert_eq!(snapshot["uiTree"]["page"], "LIBRARY");
    assert_eq!(snapshot["uiTree"]["miniPlayer"]["song"]["id"], "58");
    assert_eq!(snapshot["uiTree"]["miniPlayer"]["position"], 32_000);
    assert_eq!(snapshot["uiTree"]["miniPlayer"]["fraction"], 0.13333333333333333);
}

#[test]
fn music_search_event_updates_the_rendered_ime_text_field() {
    let mut app = App::default();
    app.event("navigate", &json!({"page":"SEARCH"}), 10);
    app.event("query", &json!({"value":"春日影"}), 20);
    let snapshot = app.event("uiSnapshot", &Value::Null, 30);
    assert_eq!(snapshot["uiTree"]["content"]["children"][0]["children"][0]["value"], "春日影");
}

#[test]
fn rust_search_submit_uses_the_current_ime_text_and_selected_category() {
    let mut app = App::default();
    app.event("navigate", &json!({"page":"SEARCH"}), 10);
    app.event("query", &json!({"value":"東京"}), 20);
    let reply = app.event("uiSearch", &json!({"kind":"artists"}), 30);
    let request = payload(&reply, "search");
    assert_eq!(request["input"]["query"], "東京");
    assert_eq!(request["input"]["kind"], "artists");
}

#[test]
fn rust_search_field_change_updates_state_before_search_submit() {
    let mut app = App::default();
    app.event("uiNavigate", &json!({"page":"SEARCH"}), 10);
    app.event("uiQuery", &json!({"value":"夜に駆ける"}), 20);
    let reply = app.event("uiSearch", &json!({"kind":"songs"}), 30);
    let request = payload(&reply, "search");
    assert_eq!(request["input"]["query"], "夜に駆ける");
}

#[test]
fn rust_track_menu_play_uses_the_row_the_user_selected() {
    let mut app = App::default();
    app.event("navigate", &json!({"page":"SEARCH"}), 1);
    let search = payload(&app.event("search", &json!({"query":"test"}), 2), "search");
    app.event("work", &json!({"tag":search["tag"],"result":{"items":[
        {"id":"23","title":"First"},{"id":"24","title":"Selected"}]}}), 3);
    let menu = app.event("uiTrack", &json!({"id":"24"}), 4);
    assert_eq!(menu["uiTree"]["content"]["overlay"]["title"], "Selected");
    let played = app.event("uiPlay", &Value::Null, 5);
    assert_eq!(played["effects"][0]["op"], "load");
    assert_eq!(played["player"]["song"]["id"], "24");
}

#[test]
fn closing_track_actions_does_not_navigate_away_from_search() {
    let mut app = App::default();
    app.event("navigate", &json!({"page":"SEARCH"}), 1);
    let search = payload(&app.event("search", &json!({"query":"Nhato"}), 2), "search");
    app.event("work", &json!({"tag":search["tag"],"result":{"items":[{"id":"23","title":"Fixture"}]}}), 3);
    app.event("uiTrack", &json!({"at":0,"id":"23"}), 4);
    let back = app.event("uiBack", &Value::Null, 5);
    assert_eq!(back["ui"]["destination"], "SEARCH");
    assert_eq!(app.event("uiBack", &Value::Null, 6)["ui"]["destination"], "LIBRARY");
}

#[test]
fn offline_import_from_an_old_client_is_ignored() {
    let mut app = App::default();
    let result = app.event("import", &json!({"songs":[{"localUri":"content://fixture/audio","title":"Old import"}]}), 10);
    assert_eq!(result["effects"], json!([]));
    assert!(app.event("snapshot", &Value::Null, 20)["ui"].get("songs").is_none());
}

#[test]
fn old_file_tracks_do_not_reach_the_audio_adapter() {
    let mut app = App::default();
    let song = json!({"id":"1","localUri":"content://fixture/audio"});
    let reply = app.event("playerPlayTracks", &json!({"songs":[song]}), 10);
    assert_eq!(reply["effects"], json!([]));
    let reply = app.event("playerEnqueue", &json!({"song":song}), 20);
    assert_eq!(reply["effects"], json!([]));
    assert!(app.event("snapshot", &Value::Null, 30)["player"]["song"].is_null());
}

#[test]
fn upgrade_drops_legacy_catalog_without_resetting_preferences() {
    let mut app = App::default();
    let reply = app.event("boot", &json!({"saved":{"songs":[{"id":"local-1"}],"lists":[{"id":"list"}],"theme":2,"lyricPrefs":{"align":1,"shift":400}}}), 10);
    assert_eq!(reply["ui"]["theme"], 2);
    assert_eq!(reply["ui"]["lyricPrefs"]["shift"], 400);
    assert_eq!(reply["effects"][0]["kind"], "persist");
    assert!(reply["effects"][0]["data"].get("songs").is_none());
    assert!(reply["ui"].get("offlineLists").is_none());
}

#[test]
fn a_new_search_replaces_an_in_flight_search() {
    let mut app = App::default();
    let first = payload(&app.event("search", &json!({"query":"first","kind":"songs"}), 10), "search");
    let second = payload(&app.event("search", &json!({"query":"second","kind":"playlists"}), 20), "search");
    assert_eq!(second["input"]["query"], "second");
    assert_ne!(second["tag"], first["tag"]);
    let old = app.event("work", &json!({"tag":first["tag"],"result":{"items":[{"id":"1","title":"Old"}]}}), 30);
    assert!(old["ui"].is_null());
    let view = app.event("work", &json!({"tag":second["tag"],"result":{"items":[{"id":"2","title":"New"}]}}), 40);
    assert_eq!(view["ui"]["searchCollections"][0]["title"], "New");
}

#[test]
fn artist_search_opens_its_songs_and_passes_the_selected_track_to_audio() {
    let mut app = App::default();
    app.event("navigate", &json!({"page":"SEARCH"}), 1);
    let search = payload(&app.event("search", &json!({"query":"Nhato","kind":"artists"}), 2), "search");
    let artist = json!({"id":"23","kind":"artist","title":"Nhato"});
    let found = app.event("work", &json!({"tag":search["tag"],"result":{"items":[artist]}}), 3);
    assert_eq!(found["ui"]["searchKind"], "artists");
    assert_eq!(found["ui"]["searchCollections"][0], artist);
    let detail = payload(&app.event("openCollection", &json!({"item":artist}), 4), "artistTracks");
    assert_eq!(detail["input"]["id"], "23");
    let tracks = json!([{"id":"12","title":"First"},{"id":"9","title":"Second"}]);
    let visible = app.event("work", &json!({"tag":detail["tag"],"result":{"items":tracks,"more":false}}), 5);
    assert_eq!(visible["ui"]["collectionPage"]["items"], tracks);
    let play = app.event("playerPlayTracks", &json!({"songs":tracks,"at":1}), 6);
    assert_eq!(play["player"]["song"]["id"], "9");
    assert_eq!(play["effects"][0]["op"], "load");
}

#[test]
fn created_playlist_search_includes_later_pages_without_duplicates() {
    let mut app = signed_in_app();
    let first = payload(&app.event("selectKind", &json!({"name":"PLAYLISTS"}), 10), "playlists");
    let more = app.event("work", &json!({"tag":first["tag"],"result":{"items":[
        {"id":"31","title":"Bass from a friend","owner":"22"},
        {"id":"32","title":"Ambient","owner":"21"}],"more":true,"next":2}}), 20);
    let next = payload(&more, "playlists");
    assert_eq!(next["input"]["offset"], 2);
    app.event("libraryFilter", &json!({"value":"created"}), 21);
    app.event("libraryQuery", &json!({"value":" BASS "}), 22);
    let final_page = app.event("work", &json!({"tag":next["tag"],"result":{"items":[
        {"id":"33","title":"Bass notes","owner":"21"},{"id":"33","title":"Duplicate","owner":"21"}],"more":false,"next":4}}), 30);
    let page = &final_page["ui"]["playlistsPage"];
    assert_eq!(page["items"].as_array().unwrap().len(), 1);
    assert_eq!(page["items"][0]["id"], "33");
    assert_eq!(page["loaded"], 3);
    assert_eq!(page["loading"], false);
}

#[test]
fn adding_to_a_loaded_queue_publishes_the_new_queue() {
    let mut app = App::default();
    app.event("playerPlayTracks", &json!({"songs":[{"id":"1"},{"id":"2"}]}), 10);
    let reply = app.event("playerEnqueue", &json!({"song":{"id":"3"},"next":true}), 15);
    assert_eq!(reply["player"]["queue"][1]["id"], "3");
    let advanced = app.event("playerNext", &Value::Null, 20);
    assert_eq!(advanced["player"]["song"]["id"], "3");
}

#[test]
fn ui_room_queue_action_recommends_the_selected_song() {
    let mut app = signed_in_app();
    let invite = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=fixture&inviterUid=22"}), 10), "roomPreview");
    app.event("work", &json!({"tag":invite["tag"],"result":{"roomId":"fixture","status":"AVAILABLE"}}), 20);
    let join = payload(&app.event("roomJoin", &Value::Null, 30), "roomJoin");
    let entered = app.event("work", &json!({"tag":join["tag"],"result":{"room":{"roomId":"fixture"}}}), 40);
    let queue = payload(&entered, "roomQueue");
    app.event("work", &json!({"tag":queue["tag"],"result":{"items":[
        {"id":"11","title":"First","bizId":"101"},
        {"id":"22","title":"Selected","bizId":"202"}
    ]}}), 50);
    app.event("uiToggleQueue", &Value::Null, 60);
    let reply = app.event("uiQueuePlay", &json!({"at":1}), 70);
    let request = payload(&reply, "roomOperation");
    assert_eq!(request["input"]["operation"], "next");
    assert_eq!(request["input"]["id"], "22");
    assert_eq!(request["input"]["bizId"], "202");
}

#[test]
fn dismissing_the_leave_confirmation_clears_the_confirmation_state() {
    let mut app = App::default();
    let requested = app.event("uiLeaveRequest", &Value::Null, 10);
    assert_eq!(requested["ui"]["presentation"]["leaveConfirmation"], true);
    let dismissed = app.event("uiDismiss", &Value::Null, 20);
    assert_eq!(dismissed["ui"]["presentation"]["leaveConfirmation"], false);
}

#[test]
fn back_dismisses_leave_confirmation_without_leaving_the_room_page() {
    let mut app = App::default();
    app.event("uiNavigate", &json!({"page":"ROOMS"}), 5);
    app.event("uiLeaveRequest", &Value::Null, 10);
    let dismissed = app.event("uiBack", &Value::Null, 20);
    assert_eq!(dismissed["uiTree"]["page"], "ROOMS");
    assert_eq!(dismissed["uiTree"]["presentation"]["leaveConfirmation"], false);
}

#[test]
fn ui_events_keep_the_system_dark_palette() {
    let mut app = App::default();
    app.event("uiSnapshot", &json!({"dark":true}), 1);
    let navigated = app.event("uiNavigate", &json!({"page":"SEARCH"}), 2);
    assert_eq!(navigated["uiTree"]["theme"]["mode"], "dark");
}

#[test]
fn song_row_payload_can_be_played_when_the_library_has_no_visible_tracks() {
    let mut app = App::default();
    let reply = app.event("uiPlay", &json!({"at":0,"song":{"id":"123","title":"Chosen","artist":"Artist","duration":60000}}), 10);
    assert_eq!(reply["effects"][0]["op"], "load");
    assert_eq!(reply["effects"][0]["song"]["id"], "123");
}

#[test]
fn rendered_song_payload_wins_over_another_song_at_the_same_visible_index() {
    let mut app = App::default();
    app.event("navigate", &json!({"page":"SEARCH"}), 1);
    let search = payload(&app.event("search", &json!({"query":"visible"}), 2), "search");
    app.event("work", &json!({"tag":search["tag"],"result":{"items":[{"id":"456","title":"Visible"}]}}), 3);
    let selected = json!({"id":"123","title":"Chosen","artist":"Artist","duration":60000});
    let reply = app.event("uiTrack", &json!({"id":"123","at":0,"song":selected}), 4);
    assert_eq!(reply["uiTree"]["content"]["overlay"]["title"], "Chosen");
}

#[test]
fn room_choice_song_opens_a_track_menu_from_its_rendered_row() {
    let mut app = App::default();
    app.event("uiNavigate", &json!({"page":"ROOMS"}), 1);
    let reply = app.event("uiTrack", &json!({"id":"123","at":0,"song":{"id":"123","title":"Chosen","artist":"Artist","duration":60000}}), 2);
    assert_eq!(reply["uiTree"]["content"]["overlay"]["type"], "trackActions");
    assert_eq!(reply["uiTree"]["content"]["overlay"]["title"], "Chosen");
}

#[test]
fn media_transport_pauses_via_rust_without_changing_queue() {
    let mut app = App::default();
    app.event("playerPlayTracks", &json!({"songs":[{"id":"1"},{"id":"2"}]}), 10);
    let paused = app.event("playerTransport", &json!({"action":"pause"}), 15);
    assert_eq!(paused["effects"][0]["op"], "pause");
    assert_eq!(paused["player"]["active"], false);
    assert_eq!(paused["player"]["queue"][1]["id"], "2");
}

#[test]
fn liked_music_uses_the_marked_playlist_order() {
    let mut app = signed_in_app();
    let liked = app.jobs["liked"];
    let reply = app.event("selectKind", &json!({"name":"LIKED"}), 10);
    assert!(reply["effects"].as_array().unwrap().iter().any(|e| e["method"] == "playlists"), "opening liked music must look up the marked playlist");
    let playlists = payload(&reply, "playlists");
    app.event("work", &json!({"tag":liked,"result":{"ids":["11","12"]}}), 20);
    let tracks = payload(&app.event("work", &json!({"tag":playlists["tag"],"result":{"items":[
        {"id":"30","owner":"21","title":"Regular"},
        {"id":"31","owner":"21","specialType":5,"title":"Likes"}],"more":false,"next":2}}), 30), "playlistTracks");
    assert_eq!(tracks["slot"], "likedTracks");
    assert_eq!(tracks["input"]["id"], "31");
    let reply = app.event("work", &json!({"tag":tracks["tag"],"result":{"items":[{"id":"12"},{"id":"11"}],"total":2}}), 40);
    assert_eq!(reply["ui"]["likedPage"]["items"], json!([{"id":"12"},{"id":"11"}]));
}

#[test]
fn audio_progress_advances_the_rust_lyric_highlight() {
    let mut app = App::default();
    app.event("audioAttached", &json!({"attached":true}), 5);
    let play = app.event("playerPlayTracks", &json!({"songs":[{"id":"1","duration":5000}]}), 10);
    let request = payload(&app.event("lyricsVisible", &json!({"visible":true}), 12), "lyrics");
    let lines = crate::lyrics::lines("[00:00.00]First\n[00:02.00]Second", "");
    app.event("work", &json!({"tag":request["tag"],"result":{"lines":lines}}), 15);
    let result = app.event("audioPosition", &json!({"token":play["effects"][0]["token"],"position":2100}), 20);
    assert_eq!(result["progress"]["position"], 2100);
    assert_eq!(result["progress"]["lyricLine"], 1);
}

#[test]
fn album_name_filter_can_find_a_later_page_and_open_it() {
    let mut app = signed_in_app();
    let start = app.event("selectKind", &json!({"name":"ALBUMS"}), 10);
    assert!(start["effects"].as_array().unwrap().iter().any(|e| e["method"] == "albums"), "opening collected albums must request the first page");
    let first = payload(&start, "albums");
    let reply = app.event("work", &json!({"tag":first["tag"],"result":{"items":[{"id":"51","kind":"album","title":"Ambient"}],"more":true,"next":1,"total":2}}), 20);
    assert_eq!(reply["ui"]["albumsPage"]["more"], true);
    let next = payload(&app.event("albumQuery", &json!({"value":"bAsS"}), 30), "albums");
    assert_eq!(next["input"]["offset"], 1);
    let album = json!({"id":"52","kind":"album","title":"Bass notes"});
    let reply = app.event("work", &json!({"tag":next["tag"],"result":{"items":[album],"total":2}}), 40);
    assert_eq!(reply["ui"]["albumsPage"]["items"], json!([album]));
    let tracks = payload(&app.event("openCollection", &json!({"item":album}), 50), "albumTracks");
    assert_eq!(tracks["input"]["id"], "52");
}

#[test]
fn a_missing_liked_playlist_falls_back_to_ordered_ids() {
    let mut app = signed_in_app();
    let first = payload(&app.event("selectKind", &json!({"name":"LIKED"}), 10), "playlists");
    let likes = app.jobs["liked"];
    let waiting = app.event("work", &json!({"tag":likes,"result":{"ids":["19","2","19","7"]}}), 20);
    assert!(waiting["effects"].as_array().unwrap().is_empty());
    let fallback = payload(&app.event("work", &json!({"tag":first["tag"],"result":{"items":[{"id":"42","owner":"22","specialType":5}],"more":false}}), 30), "likedTracks");
    assert_eq!(fallback["input"]["ids"], json!(["19","2","7"]));
    assert_eq!(fallback["input"]["total"], 3);
}

#[test]
fn joining_uses_the_previewed_invitation() {
    let mut app = signed_in_app();
    let preview = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=fixture&inviterUid=22"}), 10), "roomPreview");
    app.event("work", &json!({"tag":preview["tag"],"result":{"roomId":"fixture","status":"AVAILABLE"}}), 20);
    let join = payload(&app.event("roomJoin", &Value::Null, 30), "roomJoin");
    assert_eq!(join["input"]["invite"], preview["input"]["invite"]);
}

#[test]
fn an_expired_invite_is_visible_but_cannot_join() {
    let mut app = signed_in_app();
    let request = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=fixture&inviterUid=22"}), 10), "roomPreview");
    let reply = app.event("work", &json!({"tag":request["tag"],"result":{"roomId":"fixture","status":"EXPIRED"}}), 20);
    assert_eq!(reply["ui"]["invitePreview"]["status"], "EXPIRED");
    assert_eq!(app.event("roomJoin", &Value::Null, 30)["effects"], json!([]));
}

#[test]
fn an_invite_from_the_current_account_cannot_join() {
    let mut app = signed_in_app();
    let request = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=fixture&inviterUid=21"}), 10), "roomPreview");
    let reply = app.event("work", &json!({"tag":request["tag"],"result":{"roomId":"fixture","status":"AVAILABLE"}}), 20);
    assert_eq!(reply["ui"]["invitePreview"]["status"], "AVAILABLE");
    assert_eq!(app.event("roomJoin", &Value::Null, 30)["effects"], json!([]));
}

#[test]
fn dismissing_invite_drops_pending_preview() {
    let mut app = App::default();
    let preview = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=fixture&inviterUid=22"}), 10), "roomPreview");
    let dismissed = app.event("roomDismissInvite", &Value::Null, 15);
    assert_eq!(dismissed["ui"]["roomBusy"], false);
    app.event("work", &json!({"tag":preview["tag"],"result":{"roomId":"fixture"}}), 20);
    assert!(app.event("snapshot", &Value::Null, 25)["ui"]["invitePreview"].is_null());
}

#[test]
fn a_replaced_invite_cannot_accept_the_previous_preview() {
    let mut app = signed_in_app();
    let first = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=first&inviterUid=22"}), 10), "roomPreview");
    let second = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=second&inviterUid=23"}), 20), "roomPreview");
    let stale = app.event("work", &json!({"tag":first["tag"],"result":{"roomId":"first"}}), 30);
    assert!(stale["ui"].is_null());
    let preview = app.event("work", &json!({"tag":second["tag"],"result":{"roomId":"second"}}), 40);
    assert_eq!(preview["ui"]["invitePreview"]["roomId"], "second");
}

#[test]
fn a_preview_with_a_different_room_is_not_joinable() {
    let mut app = signed_in_app();
    let preview = payload(&app.event("roomPreview", &json!({"invite":"https://st.music.163.com/listen-together/multishare/index.html?roomId=first&inviterUid=22"}), 10), "roomPreview");
    let reply = app.event("work", &json!({"tag":preview["tag"],"result":{"roomId":"other"}}), 20);
    assert!(reply["ui"]["invitePreview"].is_null());
    let join = app.event("roomJoin", &Value::Null, 25);
    assert_eq!(join["effects"], json!([]));
}

#[test]
fn room_creation_uses_the_rust_owned_song_and_visibility() {
    let mut app = signed_in_app();
    app.event("roomChooseSong", &json!({"song":{"id":"123","title":"Selected"}}), 10);
    app.event("roomPublic", &json!({"public":true}), 15);
    let create = payload(&app.event("roomCreate", &Value::Null, 20), "roomCreate");
    assert_eq!(create["input"]["id"], "123");
    assert_eq!(create["input"]["public"], true);
}