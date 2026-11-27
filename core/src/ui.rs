use serde_json::{json, Value};

pub fn render(ui: &Value, player: &Value, position: u64, active_lyric: i64, system_dark: bool, native_palette: &Value) -> Value {
    let page = string(ui, "destination", "LIBRARY");
    let theme = tokens(number(ui, "theme", 0), system_dark, native_palette);
    let mut content = match page.as_str() {
        "SEARCH" => search(ui),
        "ROOMS" => rooms(ui),
        "SETTINGS" => settings(ui),
        _ => library(ui),
    };
    if object(ui, "collection").is_some() { content = collection_page(ui); }
    if let Some(screen) = object(ui, "presentation") {
        if screen["track"].is_object() {
            content["overlay"] = track_overlay(&screen["track"], ui);
        } else if screen["queue"] == true {
            content["overlay"] = queue_overlay(player, ui);
        } else if screen["fullPlayer"] == true {
            content["overlay"] = player_overlay(player, position, screen["lyrics"] == true, active_lyric, ui);
        }
        if screen["invite"] == true { content["dialog"] = invite_dialog(screen, ui); }
        if screen["leaveConfirmation"] == true { content["dialog"] = leave_dialog(); }
    }
    if content.get("dialog").is_none() && !string(ui, "qrUrl", "").is_empty() { content["dialog"] = qr_dialog(ui); }
    let mini = mini_player(player, position);
    json!({
        "type":"app",
        "page":page,
        "theme":theme,
        "content":content,
        "presentation":object(ui,"presentation").cloned().unwrap_or_else(||json!({})),
        "miniPlayer":mini,
        "activeLyric":active_lyric,
        "navigation":navigation(&page),
    })
}

fn search(ui: &Value) -> Value {
    let query = string(ui, "query", "");
    let kind = string(ui, "searchKind", "songs");
    let results = array(ui, "searchSongs").iter().enumerate().map(|(i,song)| song_row(song,i,ui,"searchSongs")).collect::<Vec<_>>();
    let collections = array(ui, "searchCollections").iter().enumerate().map(|(i,item)| collection_row(item,i)).collect::<Vec<_>>();
    let result_children = if kind == "songs" { results } else { collections };
    let error = string(ui,"searchError","");
    let footer = if !error.is_empty() { Some(action("uiSearch", "重试搜索", json!({"kind":kind}))) }
        else if bool_value(ui,"canSearchMore") { Some(action("uiSearchMore","加载更多",json!({"kind":kind}))) } else { None };
    json!({
        "type":"search",
        "children":[
            {"type":"row","children":[field(&query,"搜索歌曲、歌单或艺人","uiQuery"),action("uiSearch","搜索",json!({"kind":kind}))]},
            {"type":"segments","value":kind,"action":"uiSearchKind","options":[{"value":"songs","label":"歌曲"},{"value":"playlists","label":"歌单"},{"value":"artists","label":"艺人"}]},
            {"type":"listHeader","text":if query.is_empty(){"搜索网易云音乐"}else if kind=="songs"{"歌曲"}else if kind=="artists"{"艺人"}else{"歌单"}},
            {"type":"list","children":result_children,"loading":bool_value(ui,"searching"),"error":error,"empty":!bool_value(ui,"searching")&&result_children_empty(&kind,ui),"footer":footer}
        ]
    })
}

fn library(ui: &Value) -> Value {
    let kind = string(ui, "libraryKind", "LIKED");
    let page_key = match kind.as_str() {
        "PLAYLISTS" => "playlistsPage",
        "ALBUMS" => "albumsPage",
        _ => "likedPage",
    };
    let items = object(ui, page_key).and_then(|page| page.get("items")).and_then(Value::as_array).cloned().unwrap_or_default();
    let mut children: Vec<Value> = if kind == "LIKED" { items.iter().enumerate().map(|(i,song)| song_row(song,i,ui,"likedPage")).collect() } else { items.iter().enumerate().map(|(i,item)| collection_row(item,i)).collect() };
    if kind != "LIKED" {
        children.insert(0,field(&string(ui,if kind=="ALBUMS"{"albumQuery"}else{"libraryQuery"},""),if kind=="ALBUMS"{"搜索专辑"}else{"搜索歌单"},if kind=="ALBUMS"{"uiAlbumQuery"}else{"uiPlaylistQuery"}));
    }
    if kind == "PLAYLISTS" {
        let filters = json!([{"value":"all","label":"全部"},{"value":"created","label":"我创建的"},{"value":"collected","label":"我收藏的"}]);
        children.insert(1, json!({"type":"segments","value":string(ui,"playlistFilter","all"),"action":"uiPlaylistFilter","options":filters}));
    }
    if kind == "LIKED" && !children.is_empty() { children.insert(0,action("uiPlayAll","播放全部",json!({"shuffle":false}))); }
    if children.is_empty() && !object(ui,page_key).is_some_and(|page| bool_value(page,"loading")) {
        children.push(text_node(if kind=="ALBUMS"{"还没有收藏专辑"}else if kind=="PLAYLISTS"{"还没有歌单"}else{"还没有喜欢的歌曲"}));
    }
    let page = object(ui,page_key);
    if page.is_some_and(|page| bool_value(page,"loading")) { children.push(json!({"type":"loading","label":"加载中"})); }
    else if page.is_some_and(|page| !string(page,"error","").is_empty()) { children.push(action("uiRefreshLibrary","重试",Value::Null)); }
    else if page.is_some_and(|page| bool_value(page,"more")) { children.push(action(if kind=="LIKED"{"uiMoreLiked"}else{"uiMoreAlbums"},"加载更多",Value::Null)); }
    json!({
        "type":"library",
        "kind":kind,
        "children":[
            {"type":"libraryTabs","value":kind,"action":"uiLibraryKind","options":[{"value":"LIKED","label":"我喜欢的音乐"},{"value":"PLAYLISTS","label":"我的歌单"},{"value":"ALBUMS","label":"收藏的专辑"}]},
            {"type":"listHeader","text":if kind=="LIKED"{"我喜欢的音乐"}else if kind=="PLAYLISTS"{"我的歌单"}else{"收藏的专辑"}},
            {"type":"list","children":children}
        ]
    })
}

fn collection_page(ui: &Value) -> Value {
    let collection = object(ui, "collection").unwrap();
    let page = object(ui, "collectionPage");
    let items = page.and_then(|value| value.get("items")).and_then(Value::as_array).cloned().unwrap_or_default();
    let songs = items.iter().enumerate().map(|(i, song)| song_row(song, i, ui, "collectionPage")).collect::<Vec<_>>();
    let footer = if page.is_some_and(|value| !string(value, "error", "").is_empty()) {
        Some(action("uiMoreCollection", "重试", Value::Null))
    } else if page.is_some_and(|value| bool_value(value, "more")) {
        Some(action("uiMoreCollection", "加载更多", Value::Null))
    } else { None };
    json!({"type":"collection","title":string(collection,"title",""),"kind":string(collection,"kind","playlist"),
        "children":[
            action("uiCloseCollection","返回合集列表",Value::Null),
            {"type":"row","children":[action("uiPlayAll","播放全部",json!({"shuffle":false})),action("uiPlayAll","随机播放",json!({"shuffle":true}))]},
            {"type":"listHeader","text":string(collection,"detail","")},
            {"type":"list","children":songs,"loading":page.is_some_and(|value|bool_value(value,"loading")),
                "error":page.map(|value|string(value,"error","")).unwrap_or_default(),
                "empty":items.is_empty()&&page.is_some_and(|value|bool_value(value,"ready")),"footer":footer}
        ]})
}

fn rooms(ui: &Value) -> Value {
    let room = object(ui, "room");
    let queue = array(ui, "roomQueue").iter().enumerate().map(|(i, row)| {
        let song = row.get("song").unwrap_or(row);
        let mut item = song_row(song,i,ui,"roomQueue");
        if let Some(map) = item.as_object_mut() {
            map.insert("bizId".into(), row.get("bizId").cloned().unwrap_or(Value::Null));
            map.insert("owner".into(), row.get("owner").cloned().unwrap_or(Value::Null));
        }
        item
    }).collect::<Vec<_>>();
    let mut children = Vec::new();
    if let Some(room) = room {
        children.push(json!({"type":"roomSummary","value":room,"actions":[action("uiRoomRefresh","同步房间",Value::Null),action("uiShareRoom","邀请好友",Value::Null),action("uiToggleQueue","房间队列",Value::Null)]}));
        children.push(json!({"type":"memberList","children":array(room,"members")}));
        children.push(json!({"type":"section","title":"房间队列","children":queue}));
        children.extend(array(ui,"chat").iter().map(|item|json!({"type":"chatRow","name":string(item,"name","听友"),"text":string(item,"text",""),"time":number(item,"time",0)})));
        children.push(field(&string(ui,"chatDraft",""),"说点什么","uiRoomDraft"));
        children.push(action("uiRoomSend","发送",json!({"enabled":!bool_value(ui,"roomBusy")})));
        children.push(action("uiLeaveRequest","离开房间",json!({"enabled":!bool_value(ui,"roomBusy")})));
    } else if object(ui,"account").is_none() {
        children.push(text_node("登录后使用好友房"));
        children.push(action("uiNavigate","前往登录",json!({"page":"SETTINGS"})));
    } else {
        children.push(action("uiRoomInvite","粘贴好友邀请链接",json!({"enabled":!bool_value(ui,"roomBusy")})));
        children.push(action("uiRestoreRoom","恢复当前房间",Value::Null));
        children.push(action("uiNavigate","搜索开房歌曲",json!({"page":"SEARCH"})));
        if let Some(song) = object(ui,"roomChoiceSong") {
            children.push(song_row(song,0,ui,"roomChoiceSong"));
            children.push(json!({"type":"switch","label":"公开好友房","value":bool_value(ui,"publicRoom"),"action":"uiRoomPublic"}));
            children.push(action("uiCreateRoom","创建好友房",json!({"enabled":!bool_value(ui,"roomBusy")})));
        }
    }
    json!({"type":"rooms","children":children})
}

fn settings(ui: &Value) -> Value {
    let lyric = object(ui, "lyricPrefs").cloned().unwrap_or_else(|| json!({"align":0,"shift":0,"awake":false}));
    let mut children = Vec::new();
    let account = object(ui,"account");
    children.push(json!({"type":"section","title":"网易云账号","children":[json!({"type":"account","value":account.cloned().unwrap_or(Value::Null),"actions":[action(if account.is_some(){"uiLogout"}else{"uiLogin"},if account.is_some(){"退出登录"}else{"登录"},Value::Null)]})]}));
    let theme = number(ui,"theme",0);
    children.push(json!({"type":"section","title":"外观","children":[json!({"type":"segments","value":theme,"action":"uiTheme","options":[{"value":0,"label":"跟随系统"},{"value":1,"label":"浅色"},{"value":2,"label":"深色"}]})]}));
    children.push(json!({"type":"section","title":"歌词","children":[
        {"type":"segments","value":number(&lyric,"align",0),"action":"uiLyricPrefsAlign","options":[{"value":0,"label":"左对齐"},{"value":1,"label":"居中"}]},
        {"type":"slider","value":(number(&lyric,"shift",5000) as f64+5000.0)/10000.0,"label":format!("同步微调 · {} ms",lyric["shift"]),"action":"uiLyricPrefsShift"},
        {"type":"switch","label":"歌词页播放时保持屏幕常亮","value":bool_value(&lyric,"awake"),"action":"uiLyricPrefsAwake"}
    ]}));
    json!({
        "type":"settings","children":children
    })
}

fn song_row(song: &Value, at: usize, ui: &Value, list: &str) -> Value {
    json!({
        "type":"songRow",
        "at":at,
        "id":string(song,"id",""),
        "title":string(song,"title","未知歌曲"),
        "artist":string(song,"artist", "未知歌手"),
        "album":string(song,"album",""),
        "cover":string(song,"cover",""),
        "duration":number(song,"duration",0),
        "subtitle":format!("{}{}",string(song,"artist","未知歌手"),if string(song,"album","").is_empty(){String::new()}else{format!(" · {}",string(song,"album",""))}),
        "selected":ui["presentation"]["track"]["song"]["id"]==song["id"],
        "liked":array(ui,"liked").iter().any(|id|id==&song["id"]),
        "list":list,
        "song":song,
        "actions":[action("uiPlay","播放",json!({"list":list,"at":at,"song":song})),action("uiTrack","更多操作",json!({"list":list,"at":at,"id":string(song,"id",""),"song":song}))],
    })
}

fn collection_row(value: &Value, at: usize) -> Value {
    json!({
        "type":"collectionRow",
        "at":at,
        "id":string(value,"id",""),
        "kind":string(value,"kind","playlist"),
        "title":string(value,"title","未命名歌单"),
        "detail":string(value,"detail",""),
        "cover":string(value,"cover",""),
        "count":number(value,"count",0),
        "actions":[action("uiOpenCollection","打开",json!({"item":value}))],
    })
}

fn mini_player(player: &Value, position: u64) -> Value {
    let song = object(player, "song");
    json!({
        "visible":song.is_some(),
        "song":song.cloned().unwrap_or(Value::Null),
        "position":position,
        "duration":number(player,"duration",0),
        "playing":bool_value(player,"playing"),
        "buffering":bool_value(player,"buffering"),
        "roomMode":bool_value(player,"roomMode"),
        "fraction":fraction(position,number(player,"duration",0)),
        "actions":[action(if bool_value(player,"playing"){"playerPause"}else{"playerPlay"},if bool_value(player,"playing"){"暂停"}else{"播放"},Value::Null),action("uiToggleQueue","播放队列",Value::Null),action("uiToggleFullPlayer","展开播放器",Value::Null)],
    })
}

fn navigation(page: &str) -> Value {
    let values = [
        ("LIBRARY", "曲库"),
        ("SEARCH", "搜索"),
        ("ROOMS", "房间"),
        ("SETTINGS", "设置"),
    ];
    Value::Array(values.into_iter().map(|(value,label)| json!({"value":value,"label":label,"selected":value == page})).collect())
}

fn result_children_empty(kind: &str, ui: &Value) -> bool {
    if kind == "songs" { array(ui,"searchSongs").is_empty() } else { array(ui,"searchCollections").is_empty() }
}

fn action(event: &str, label: &str, data: Value) -> Value {
    json!({"type":"action","event":event,"label":label,"data":data})
}

fn field(value: &str, placeholder: &str, event: &str) -> Value {
    let ime_action = match event {"uiQuery" => "search", "uiRoomDraft" => "send", _ => "done"};
    json!({"type":"textField","value":value,"placeholder":placeholder,"change":event,"imeAction":ime_action})
}

fn text_node(value: &str) -> Value { json!({"type":"text","text":value}) }

fn track_overlay(track: &Value, ui: &Value) -> Value {
    let song = &track["song"];
    if track["details"] == true {
        return json!({"type":"trackDetails","title":song["title"],"subtitle":song["artist"],"cover":song["cover"],"rows":[
            {"label":"专辑","value":song["album"]},{"label":"时长","value":song["duration"]},
            {"label":"来源","value":"网易云音乐"},{"label":"音频格式","value":song["format"]},
            {"label":"采样率","value":song["sampleRate"]},{"label":"码率","value":song["bitrate"]}
        ]});
    }
    let mut actions = vec![action("uiPlay","立即播放",Value::Null)];
    let room_active = object(ui,"room").is_some();
    if room_active { actions.clear(); }
    if object(ui,"room").is_none() { actions.push(action("uiEnqueue","添加到播放队列",Value::Null)); actions.push(action("uiPlayNext","下一首播放",Value::Null)); actions.push(action("uiChooseRoom","选为开房歌曲",Value::Null)); }
    else {
        actions.push(action("uiAddToPlaylist","添加到歌单",json!({"song":song})));
        actions.push(action("uiRoomSkip","切歌",Value::Null));
        actions.push(action("uiRoomLike","点赞",Value::Null));
        actions.push(action("uiRoomAdd","推荐到房间",Value::Null));
    }
    actions.push(action("uiFavorite","收藏",json!({"song":song})));
    actions.push(action("uiShareTrack","分享",Value::Null));
    actions.push(action("uiTrackDetails","歌曲信息",Value::Null));
    json!({"type":"trackActions","title":song["title"],"subtitle":song["artist"],"cover":song["cover"],"details":track["details"],"actions":actions})
}

fn queue_overlay(player: &Value, ui: &Value) -> Value {
    let room = object(ui,"room").is_some();
    let values = if room { array(ui,"roomQueue") } else { array(player,"queue") };
    let children = values.iter().enumerate().map(|(i,item)| {
        let song = item.get("song").unwrap_or(item);
        json!({"type":"queueRow","at":i,"id":string(song,"id",""),"title":string(song,"title","未知歌曲"),"subtitle":string(song,"artist",""),"cover":string(song,"cover",""),"selected":player["song"]["id"]==song["id"],"room":room,"bizId":item["bizId"],"song":song,
            "actions":if room {vec![action("uiQueuePlay","推荐下一首",json!({"at":i,"bizId":item["bizId"],"song":song}))]} else {vec![action("uiQueuePlay","播放",json!({"at":i,"id":song["id"]})),action("uiQueueRemove","从队列移除",json!({"at":i}))]}})
    }).collect::<Vec<_>>();
    json!({"type":"queue","title":if object(ui,"room").is_some(){"房间队列"}else{"播放队列"},"children":children})
}

fn player_overlay(player: &Value, position: u64, lyrics: bool, active: i64, ui: &Value) -> Value {
    let song = &player["song"];
    let actions = [action("playerPrevious","上一首",Value::Null),action(if bool_value(player,"playing"){"playerPause"}else{"playerPlay"},if bool_value(player,"playing"){"暂停"}else{"播放"},Value::Null),action("playerNext","下一首",Value::Null),action("uiToggleQueue","播放队列",Value::Null),action("uiToggleLyrics",if lyrics{"歌曲"}else{"歌词"},Value::Null)];
    let lines = array(ui,"lyrics").iter().enumerate().map(|(i,line)|json!({"type":"lyric","text":line["text"],"translation":line["translation"],"active":i as i64==active})).collect::<Vec<_>>();
    json!({"type":"fullPlayer","lyrics":lyrics,"song":{"title":song["title"],"artist":song["artist"],"cover":song["cover"]},
        "position":position,"duration":player["duration"],"fraction":fraction(position,number(player,"duration",0)),
        "actions":actions,"lyricChildren":lines,"lyricMode":bool_value(ui,"lyricMode"),"roomMode":bool_value(player,"roomMode")})
}

fn invite_dialog(screen: &Value, ui: &Value) -> Value {
    let preview = object(ui,"invitePreview");
    json!({"type":"inviteDialog","title":"加入好友房","field":field(&string(screen,"inviteText",""),"粘贴网易云邀请链接","uiRoomInviteDraft"),
        "preview":preview.cloned().unwrap_or(Value::Null),"actions":[action("uiRoomPreview","预览邀请",json!({"enabled":!bool_value(ui,"roomBusy")})),action("uiRoomJoin","加入房间",json!({"enabled":preview.is_some_and(|v|bool_value(v,"joinable"))}))]})
}

fn leave_dialog() -> Value { json!({"type":"confirmDialog","title":"离开一起听房间？","text":"离开后将恢复个人播放队列。","actions":[action("uiLeaveConfirm","离开",Value::Null),action("uiDismiss","继续听",Value::Null)]}) }

fn qr_dialog(ui: &Value) -> Value {
    json!({"type":"qrDialog","title":"使用网易云音乐扫码","url":string(ui,"qrUrl",""),"scanned":bool_value(ui,"qrScanned"),
        "actions":[action("uiCancelLogin","取消登录",Value::Null)]})
}

fn tokens(theme: u64, system_dark: bool, native_palette: &Value) -> Value {
    let dark = match theme { 1 => false, 2 => true, _ => system_dark };
    let mut result = json!({
        "mode":if dark {"dark"} else {"light"},
        "primary":if dark {"#82D5C4"} else {"#006A60"},
        "onPrimary":if dark {"#003730"} else {"#FFFFFF"},
        "primaryContainer":if dark {"#005047"} else {"#9FF2E0"},
        "onPrimaryContainer":if dark {"#9FF2E0"} else {"#00201C"},
        "secondary":if dark {"#B1CCC3"} else {"#4A635E"},
        "onSecondary":if dark {"#1C352F"} else {"#FFFFFF"},
        "secondaryContainer":if dark {"#334B45"} else {"#CDE8DF"},
        "onSecondaryContainer":if dark {"#CDE8DF"} else {"#06201C"},
        "tertiary":if dark {"#E0BDEB"} else {"#73567F"},
        "onTertiary":if dark {"#41294C"} else {"#FFFFFF"},
        "tertiaryContainer":if dark {"#5A3F65"} else {"#F5D9FF"},
        "onTertiaryContainer":if dark {"#F5D9FF"} else {"#2B1238"},
        "background":if dark {"#101513"} else {"#F6FBF8"},
        "onBackground":if dark {"#DEE4E0"} else {"#171D1B"},
        "surface":if dark {"#101513"} else {"#F6FBF8"},
        "surfaceVariant":if dark {"#3E4945"} else {"#DAE5E0"},
        "surfaceContainer":if dark {"#1C2220"} else {"#EAF0EC"},
        "surfaceContainerLow":if dark {"#181E1B"} else {"#F0F6F2"},
        "surfaceContainerHigh":if dark {"#272D2B"} else {"#E4EAE6"},
        "surfaceContainerHighest":if dark {"#323835"} else {"#DEE4E0"},
        "onSurface":if dark {"#DEE4E0"} else {"#171D1B"},
        "onSurfaceVariant":if dark {"#BEC9C3"} else {"#3E4945"},
        "outline":if dark {"#89948F"} else {"#6E7974"},
        "outlineVariant":if dark {"#3E4945"} else {"#BEC9C3"},
        "cornerLarge":28,
        "cornerMedium":16,
        "cornerSmall":12,
        "spacing":[4,8,12,16,24,32]
    });
    let palette = if dark {&native_palette["dark"]} else {&native_palette["light"]};
    for key in ["primary","onPrimary","primaryContainer","onPrimaryContainer","secondary","onSecondary","secondaryContainer","onSecondaryContainer","tertiary","onTertiary","tertiaryContainer","onTertiaryContainer","background","onBackground","surface","surfaceVariant","surfaceContainer","surfaceContainerLow","surfaceContainerHigh","surfaceContainerHighest","onSurface","onSurfaceVariant","outline","outlineVariant"] {
        if let Some(value) = palette.get(key).and_then(Value::as_str).filter(|value| value.len() == 7 && value.starts_with('#') && value[1..].bytes().all(|byte| byte.is_ascii_hexdigit())) {
            result[key] = json!(value);
        }
    }
    result
}

fn fraction(position: u64, duration: u64) -> f64 {
    if duration == 0 { 0.0 } else { (position as f64 / duration as f64).clamp(0.0, 1.0) }
}

fn object<'a>(value: &'a Value, key: &str) -> Option<&'a Value> {
    value.get(key).filter(|item| item.is_object())
}

fn array(value: &Value, key: &str) -> Vec<Value> {
    value.get(key).and_then(Value::as_array).cloned().unwrap_or_default()
}

fn string(value: &Value, key: &str, fallback: &str) -> String {
    value.get(key).and_then(Value::as_str).unwrap_or(fallback).to_owned()
}

fn number(value: &Value, key: &str, fallback: u64) -> u64 {
    value.get(key).and_then(Value::as_u64).unwrap_or(fallback)
}

fn bool_value(value: &Value, key: &str) -> bool {
    value.get(key).and_then(Value::as_bool).unwrap_or(false)
}
