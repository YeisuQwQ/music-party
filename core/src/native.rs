use crate::protocol::*;
use crate::wire::{read_cookies, readable_error, Connection, Cookies, Failure, Result, Wire};
use crate::{lyrics, reconcile};
use md5::{Digest, Md5};
use serde_json::{json, Value};
use std::{collections::HashSet, sync::{Mutex, OnceLock}, time::{Instant, SystemTime, UNIX_EPOCH}};

const MULTI: &str = "/api/listen/together/multi/";

#[derive(Default)]
struct Auth { cookies: Cookies, pending: String, qr_cookies: Cookies, epoch: u64 }

#[derive(Default)]
struct Core { net: Connection, auth: Mutex<Auth>, writes: Mutex<()> }

pub fn time_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis() as u64
}

pub(crate) fn room_time_ms() -> u64 {
    static CLOCK: OnceLock<(Instant, u64)> = OnceLock::new();
    let (started, epoch) = CLOCK.get_or_init(|| (Instant::now(), time_ms()));
    epoch.saturating_add(started.elapsed().as_millis() as u64)
}

pub fn dispatch(method: &str, raw: &str) -> String {
    static CORE: OnceLock<Core> = OnceLock::new();
    let result = std::panic::catch_unwind(|| {
        let limit = if method == "appEvent" { 32 } else { 1 } * 1024 * 1024;
        if raw.len() > limit { return Err("输入内容过长".into()); }
        let args: Value = serde_json::from_str(raw).map_err(|_| "无法读取请求")?;
        match method {
            "appEvent" => Ok(crate::app::dispatch(&args)),
            "parseInvite" => Ok(json!(parse_invite(text(&args["invite"]))?)),
            "parseLyrics" => Ok(json!({"lines":lyrics::lines(text(&args["lrc"]),text(&args["translation"]))})),
            "syncPlan" => {
                let input = serde_json::from_value(args).map_err(|_| "无法读取同步状态")?;
                Ok(json!(reconcile::plan(&input)))
            }
            "neighbor" => Ok(json!({"index":reconcile::neighbor(number(&args["size"]) as usize,
                number(&args["at"]) as usize,args["direction"].as_i64().unwrap_or(1) as i32,
                number(&args["repeat"]) as i32,args["natural"] == true)})),
            _ => CORE.get_or_init(Core::default).execute(method, &args),
        }
    }).unwrap_or_else(|_| Err("处理音乐请求时发生错误".into()));
    match result { Ok(value) => value, Err(e) => json!({"error":e.message,"code":e.code}) }.to_string()
}

impl Core {
    fn api(&self, path: &str, body: Value, wire: Wire, session: &Cookies) -> Result<Value> {
        Ok(self.net.request(path,body,wire,session,false)?.body)
    }

    fn multi(&self, path: &str, payload: Value, session: &Cookies) -> Result<Value> {
        self.api(&format!("{MULTI}{path}"),payload,Wire::Eapi,session)
    }

    fn execute(&self, method: &str, args: &Value) -> Result<Value> {
        let _guard = if matches!(method, "setSession" | "logout" | "qrCreate" | "qrCheck" | "favorite" | "playlistAdd" |
            "roomJoin" | "roomCreate" | "roomMatch" | "roomCancelMatch" | "roomLeave" | "roomOperation" | "roomChatSend" |
            "stickerSend" | "roomStickerSend") {
            Some(self.writes.lock().unwrap())
        } else { None };
        match method {
            "setSession" | "logout" => {
                let mut auth = self.auth.lock().unwrap();
                let epoch = auth.epoch.wrapping_add(1);
                *auth = Auth { epoch, cookies: if method == "logout" { Cookies::new() } else { read_cookies(text(&args["session"]).split(';')) }, ..Auth::default() };
                return Ok(json!({}));
            }
            "qrCreate" => {
                let response = self.net.request("/api/login/qrcode/unikey",json!({"type":3}),Wire::Eapi,&Cookies::new(),false)?;
                let key = response.body["unikey"].as_str().or_else(|| response.body["data"]["unikey"].as_str())
                    .filter(|s| !s.is_empty() && s.len() <= 240).ok_or("未取得登录二维码")?;
                let encoded = url::form_urlencoded::byte_serialize(key.as_bytes()).collect::<String>();
                let mut auth = self.auth.lock().unwrap();
                auth.pending = key.to_owned();
                auth.qr_cookies = response.cookies;
                return Ok(json!({"key":key,"url":format!("https://music.163.com/login?codekey={encoded}")}));
            }
            "qrCheck" => {
                let key = text(&args["key"]);
                let jar = {
                    let auth = self.auth.lock().unwrap();
                    if key.is_empty() || key != auth.pending { return Err("登录二维码已更换，请重新扫描".into()); }
                    auth.qr_cookies.clone()
                };
                let response = self.net.request("/api/login/qrcode/client/login",json!({"key":key,"type":3}),Wire::Eapi,&jar,true)?;
                let code = number(&response.body["code"]);
                let mut auth = self.auth.lock().unwrap();
                auth.qr_cookies.extend(response.cookies);
                if code == 803 {
                    if !auth.qr_cookies.contains_key("MUSIC_U") { return Err("已扫码，但没有取得网易云登录凭据，请刷新后重试".into()); }
                    auth.pending.clear();
                    let session = std::mem::take(&mut auth.qr_cookies).into_iter()
                        .map(|(k,v)| format!("{k}={v}")).collect::<Vec<_>>().join("; ");
                    return Ok(json!({"code":803,"session":session}));
                }
                if code == 800 { auth.pending.clear(); auth.qr_cookies.clear(); }
                return Ok(json!({"code":code}));
            }
            _ => {}
        }
        let (session, epoch) = { let auth = self.auth.lock().unwrap(); (auth.cookies.clone(), auth.epoch) };
        let result = self.music(method, args, &session)?;
        if method != "smsLogin" && self.auth.lock().unwrap().epoch != epoch { return Err("登录状态已变化，请重试".into()); }
        Ok(result)
    }

    fn account_uid(&self, session: &Cookies) -> Result<String> {
        let body = self.api("/api/w/nuser/account/get", json!({}), Wire::Web, session)?;
        let p = body.get("profile").unwrap_or(&body["data"]["profile"]);
        let uid = id(&p["userId"]);
        if uid.is_empty() || uid == "0" { Err("请先登录网易云音乐".into()) } else { Ok(uid) }
    }

    fn music(&self, method: &str, a: &Value, session: &Cookies) -> Result<Value> {
        let logged_in = session.contains_key("MUSIC_U");
        let must_login = matches!(method,"favorite" | "liked" | "playlists" | "playlistAdd" | "nimToken" |
            "privateConversations" | "privateHistory" | "privateSend") || method.starts_with("room");
        if must_login && !logged_in { return Err(Failure {code:302,message:"请先登录网易云音乐".into()}); }
        let web = Wire::Web;
        let eapi = Wire::Eapi;
        match method {
            "smsSend" => {
                let phone = sms_phone(text(&a["phone"]))?;
                let b = self.api("/api/sms/captcha/sent",
                    json!({"ctcode":"86","secrete":"music_middleuser_pclogin","cellphone":phone}), web, session)?;
                if b["code"] != 200 { return Err(readable_error(&b, "网易云未确认验证码发送".into()).into()); }
                Ok(json!({"sent":true}))
            }
            "smsLogin" => {
                let phone = sms_phone(text(&a["phone"]))?;
                let code = text(&a["captcha"]).trim();
                if code.len() < 4 || code.len() > 8 || !code.chars().all(|c| c.is_ascii_digit()) {
                    return Err("验证码格式不正确".into());
                }
                let response = self.net.request("/api/w/login/cellphone",
                    json!({"type":"1","https":"true","phone":phone,"countrycode":"86","captcha":code,
                        "remember":"true","secureCaptcha":""}), Wire::Web, &Cookies::new(), false)?;
                let has_music_cookie = response.cookies.get("MUSIC_U").map(|v| !v.is_empty()).unwrap_or(false);
                let b = &response.body;
                if b["code"] != 200 && !has_music_cookie {
                    return Err(readable_error(b, "验证码登录失败，请检查验证码".into()).into());
                }
                let session = response.cookies.iter().map(|(k,v)| format!("{k}={v}")).collect::<Vec<_>>().join("; ");
                if session.is_empty() { return Err("登录成功但未取得会话".into()); }
                {
                    let mut auth = self.auth.lock().unwrap();
                    auth.epoch = auth.epoch.wrapping_add(1);
                    auth.cookies = response.cookies;
                    auth.pending.clear();
                    auth.qr_cookies.clear();
                }
                Ok(json!({"code":200,"session":session}))
            }
            "deviceId" => {
                crate::wire::set_device_id(text(&a["value"]));
                Ok(json!({"ok":true}))
            }
            "account" => {
                if !logged_in { return Ok(json!({"loggedIn":false})); }
                let body = self.api("/api/w/nuser/account/get",json!({}),web,session)?;
                let p = body.get("profile").unwrap_or(&body["data"]["profile"]);
                let uid = id(&p["userId"]);
                Ok(json!({"loggedIn":!uid.is_empty() && uid!="0","uid":uid,"name":text(&p["nickname"]),"avatar":media_url(&p["avatarUrl"])}))
            }
            "search" => {
                let query = text(&a["query"]).trim();
                if query.is_empty() || query.chars().count() > 160 { return Err("请输入 1 至 160 字的搜索词".into()); }
                let start = number(&a["offset"]).min(10000);
                let (kind,key, mapper): (u16,&str,fn(&Value)->Option<Value>) = match text(&a["kind"]) {
                    "playlists" => (1000,"playlists",collection),
                    "artists" => (100,"artists",artist),
                    _ => (1,"songs",song),
                };
                let b = self.api("/api/cloudsearch/pc",json!({"s":query,"type":kind,"limit":30,"offset":start,"total":true}),eapi,session)?;
                let r = &b["result"];
                Ok(json!({"items":map_array(&r[key],mapper),"more":number(&r[format!("{}Count",key.trim_end_matches('s'))]) > start + 30}))
            }
            "song" => {
                let found = self.songs(&[arg_id(a,"id")?.to_owned()],session)?;
                found.into_iter().next().ok_or_else(|| "未找到歌曲详情".into())
            }
            "stream" => {
                let sid = arg_id(a,"id")?;
                let v = self.api("/api/song/enhance/player/url",json!({"ids":json!([sid]).to_string(),"br":320000}),eapi,session)?;
                let data = &v["data"][0];
                let url = media_url(&data["url"]);
                if url.is_empty() { return Err("网易云未提供可用音源，歌曲可能需要会员或受版权限制".into()); }
                if a["room"] == true && data["freeTrialInfo"].is_object() { return Err("这首歌仅提供试听，不能用于房间同步".into()); }
                Ok(json!({"url":url,"format":text(&data["type"]),"bitrate":number(&data["br"]),"trial":data["freeTrialInfo"].is_object()}))
            }
            "lyrics" => {
                let sid = arg_id(a,"id")?;
                let b = self.api("/api/song/lyric",json!({"id":sid,"lv":-1,"tv":-1,"rv":-1,"kv":-1,"_nmclfl":1}),eapi,session)?;
                Ok(json!({"lines":lyrics::lines(text(&b["lrc"]["lyric"]),text(&b["tlyric"]["lyric"]))}))
            }
            "albums" => {
                let offset = number(&a["offset"]).min(100000);
                let body = self.api("/api/album/sublist",json!({"limit":50,"offset":offset,"total":true}),Wire::Web,session)?;
                let items = map_array(&body["data"],album);
                let more = body["hasMore"].as_bool().or_else(||body["more"].as_bool()).unwrap_or_else(||number(&body["count"])>offset+items.len() as u64);
                Ok(json!({"items":items,"more":more,"total":number(&body["count"])}))
            }
            "artistTracks" => {
                let artist_id = arg_id(a,"id")?;
                let offset = number(&a["offset"]).min(100000);
                let body = self.api("/api/v1/artist/songs",json!({"id":artist_id,"private_cloud":"true","work_type":1,"order":"hot","offset":offset,"limit":100}),eapi,session)?;
                let items = map_array(&body["songs"],song);
                Ok(json!({"items":items,"more":body["more"]==true,"total":number(&body["total"])}))
            }
            "likedTracks" => {
                let ids: Vec<String> = map_array(&a["ids"], |v| v.as_str().filter(|value| online_id(value)).map(str::to_owned));
                let next = number(&a["offset"]).saturating_add(ids.len() as u64);
                let total = number(&a["total"]);
                Ok(json!({"items":self.songs(&ids,session)?,"more":next < total,"next":next,"total":total}))
            }
            "playlists" => {
                let uid = arg_id(a,"uid")?;
                let offset = number(&a["offset"]).min(10000);
                let b = self.api("/api/user/playlist",json!({"uid":uid,"limit":50,"offset":offset,"includeVideo":true}),web,session)?;
                Ok(json!({"items":map_array(&b["playlist"],collection),"more":b["more"] == true,"total":number(&b["count"])}))
            }
            "playlistTracks" => {
                let pid = arg_id(a,"id")?;
                let ids = self.api("/api/v6/playlist/detail",json!({"id":pid,"n":100000,"s":8}),eapi,session)?;
                let rows = &ids["playlist"]["trackIds"];
                let all: Vec<String> = map_array(rows,|x| {let value=id(&x["id"]);(!value.is_empty()).then_some(value)});
                let start=(number(&a["offset"]) as usize).min(all.len());
                let end=(start+100).min(all.len());
                let items=if all.is_empty(){map_array(&ids["playlist"]["tracks"],song)}else{self.songs(&all[start..end],session)?};
                Ok(json!({"items":items,"more":end<all.len(),"total":all.len()}))
            }
            "albumTracks" => {
                let aid = arg_id(a,"id")?;
                let body = self.api(&format!("/api/v1/album/{aid}"),json!({}),Wire::Web,session)?;
                album_track_page(&body, number(&a["offset"]))
            }
            "playlist" => {
                let pid = arg_id(a,"id")?;
                let b = self.api("/api/v6/playlist/detail",json!({"id":pid,"n":100000,"s":8}),eapi,session)?;
                let p = &b["playlist"];
                let ids: Vec<String> = map_array(&p["trackIds"],|x| { let i = id(&x["id"]); (!i.is_empty()).then_some(i) });
                let offset = (number(&a["offset"]) as usize).min(ids.len());
                let items = if ids.is_empty() { map_array(&p["tracks"],song) } else { self.songs(&ids[offset..(offset+100).min(ids.len())],session)? };
                Ok(json!({"playlist":collection(p),"items":items,"more":offset+100<ids.len()}))
            }
            "favorite" => {
                let sid = arg_id(a,"id")?;
                let liked = a["liked"] == true;
                let token = self.net.check_token().unwrap_or_default();
                let risky = |b: &Value| -> bool {
                    let note = readable_error(b, String::new());
                    note.contains("环境") || note.contains("风险") || note.contains("已取消") ||
                        b["data"].as_bool() == Some(false) ||
                        b["data"]["liked"].as_bool().is_some_and(|v| v != liked)
                };
                let mut attempts: Vec<(&str, Wire, Value)> = vec![
                    ("radio/web", web, json!({"trackId":sid,"like":liked,"alg":"itembased","time":"3","checkToken":token})),
                    ("radio/web-plain", web, json!({"trackId":sid,"like":liked,"alg":"itembased","time":"3"})),
                    ("song/eapi", eapi, json!({"trackId":sid,"like":liked,"alg":"itembased","time":"3","checkToken":token})),
                    ("song/eapi-plain", eapi, json!({"trackId":sid,"like":liked,"alg":"itembased","time":"3"})),
                    ("song/web", web, json!({"trackId":sid,"like":liked,"alg":"itembased","time":"3","checkToken":token})),
                    ("radio/eapi", eapi, json!({"trackId":sid,"like":liked,"alg":"itembased","time":"3","checkToken":token})),
                ];
                let mut trace: Vec<String> = Vec::new();
                for (name, mode, body) in attempts.drain(..) {
                    let path = if name.starts_with("radio") { "/api/radio/like" } else { "/api/song/like" };
                    match self.api(path, body, mode, session) {
                        Ok(value) => {
                            let code = number(&value["code"]);
                            trace.push(format!("{name}:{code}"));
                            if code == 200 && !risky(&value) {
                                return Ok(json!({"liked":liked}));
                            }
                        }
                        Err(error) => trace.push(format!("{name}:{}", error.code)),
                    }
                }
                Err(format!("网易云未接受本次收藏（{}）", trace.join(", ")).into())
            }
            "liked" => {
                let uid = arg_id(a,"uid")?;
                let b = self.api("/api/song/like/get",json!({"uid":uid}),eapi,session)?;
                Ok(json!({"ids":map_array(&b["ids"],|x| { let i = id(x); (!i.is_empty()).then_some(i) })}))
            }
            "privateConversations" => {
                let offset = number(&a["offset"]).min(10000);
                let b = self.api("/api/msg/private/users", json!({"limit":30,"offset":offset,"total":true}), eapi, session)?;
                Ok(b)
            }
            "privateHistory" => {
                let uid = arg_id(a,"uid")?;
                let before = number(&a["before"]);
                let first = self.api("/api/msg/private/history",
                    json!({"userId":uid,"limit":30,"time":before,"total":true}), web, session);
                match first {
                    Ok(value) => Ok(value),
                    Err(_) => {
                        let mut alt = json!({"uid":uid,"limit":30,"total":true});
                        if before > 0 { alt["before"] = json!(before); }
                        self.api("/api/msg/private/history", alt, web, session)
                    }
                }
            }
            "privateSend" => {
                let uid = arg_id(a,"uid")?;
                let message = text(&a["text"]).trim();
                if message.is_empty() || message.encode_utf16().count() > 500 {
                    return Err("私信内容须在 500 字以内".into());
                }
                let b = self.api("/api/msg/private/send", json!({"user_ids":uid,"msg":message}), web, session)?;
                if b["code"].as_i64().is_some_and(|code| code != 200) {
                    return Err(readable_error(&b, "网易云未确认私信发送".into()).into());
                }
                Ok(b)
            }
            "playlistAdd" => {
                let (pid,sid) = (arg_id(a,"playlistId")?,arg_id(a,"id")?);
                let b = self.api("/api/playlist/manipulate/tracks",json!({"pid":pid,"trackIds":json!([sid]).to_string(),"op":"add","imme":"true"}),eapi,session)?;
                if b["code"] != 200 || b["data"] == false || b["data"]["result"] == false { return Err("网易云未确认添加歌单".into()); }
                Ok(json!({"added":true}))
            }
            "nimToken" => {
                let room = text(&a["roomId"]);
                if room.is_empty() { return Err("请先进入房间".into()); }
                let status = self.multi("match/status/get",json!({}),session)?;
                let snap = &status["data"]["multiLtRoomSnapshot"];
                if text(&snap["roomId"]) != room { return Err("账号已不在该房间".into()); }
                let info = snap.get("multiRoomInfoDTO").unwrap_or(&snap["roomInfo"]);
                let chat = id(&info["chatRoomId"]);
                if chat.is_empty() || chat == "0" { return Err("房间聊天室尚未建立".into()); }
                let b = self.api("/api/middle/im/token-and-chatroom-addr/get",
                    json!({"bizName":"music_listenTogether","roomId":chat,"appType":"6"}), eapi, session)?;
                let data = &b["data"];
                let account = text(&data["accId"]).to_owned();
                let account = if account.is_empty() { text(&data["account"]).to_owned() } else { account };
                let token = text(&data["token"]).to_owned();
                if b["code"] != 200 || account.is_empty() || token.is_empty() {
                    return Err(readable_error(&b, "云信聊天室凭据暂不可用".into()).into());
                }
                Ok(json!({"account":account,"token":token,"chatRoomId":chat,"addr":data["chatroomAddr"]}))
            }
            "roomStatus" => {
                let start = time_ms(); let timer = Instant::now();
                let b = self.multi("match/status/get",json!({}),session)?;
                let d = &b["data"];
                let r = attached(d,start + timer.elapsed().as_millis() as u64 / 2,text(&a["uid"]))?;
                Ok(json!({"room":r,"status":d["status"]}))
            }
            "roomPreview" => {
                let invite = parse_invite(text(&a["invite"]))?;
                let b = self.multi("landing/info/get",json!({"roomId":invite.room_id,"inviterUid":invite.inviter_uid}),session)?;
                let d = &b["data"];
                if !d.is_object() { return Err("未取得房间预览".into()); }
                Ok(json!({"roomId":invite.room_id,"inviter":text(&d["inviter"]["nickname"]),"song":text(&d["songData"]["name"]),"status":d["roomStatus"]}))
            }
            "roomJoin" | "roomCreate" | "roomMatch" => {
                let current = self.multi("match/status/get",json!({}),session)?;
                if current["data"]["multiLtRoomSnapshot"]["roomId"].is_string() { return Err("账号已在多人房间内，请选择恢复当前房间".into()); }
                let (endpoint, mut payload, expected, uid) = if method == "roomJoin" {
                    let i = parse_invite(text(&a["invite"]))?;
                    ("match/ack",json!({"roomId":i.room_id,"inviterUid":i.inviter_uid,"agree":true}),i.room_id,i.inviter_uid)
                } else {
                    let sid = arg_id(a,"id")?;
                    (if method == "roomMatch" {"match"} else {"room/create"},
                    if method == "roomMatch" {json!({"songId":sid})} else {json!({"songId":sid,"type":if a["public"]==true {2} else {1},"groupIds":"[]","inviteUids":"[]"})},
                    String::new(),text(&a["uid"]).to_owned())
                };
                payload["checkToken"] = json!(self.net.check_token()?);
                let start = time_ms(); let timer = Instant::now();
                let b = self.multi(endpoint,payload,session)?;
                confirmed(&b,false)?;
                let room = attached(&b["data"],start+timer.elapsed().as_millis() as u64/2,&uid)?;
                if method != "roomMatch" && room.is_none() { return Err("网易云未返回新房间，请刷新状态".into()); }
                if !expected.is_empty() && room.as_ref().is_none_or(|v| v["roomId"] != expected) { return Err("服务端返回的房间与邀请不一致，请刷新状态".into()); }
                Ok(json!({"room":room}))
            }
            "roomCancelMatch" => {
                let b = self.multi("match/cancel",json!({}),session)?;
                if b["data"]["success"] == false { return Err("未确认取消匹配，请刷新房间状态".into()); }
                Ok(json!({"cancelled":true}))
            }
            "roomHeartbeat" => {
                let room = room_arg(a)?;
                let start = time_ms(); let timer = Instant::now();
                let b = self.multi("match/heartbeat",json!({"roomId":room}),session)?;
                let d = &b["data"];
                let sampled = start + timer.elapsed().as_millis() as u64 / 2;
                let p = playback(&d["roomPlaySongInfo"],sampled)?;
                let snapshot = attached(d,sampled,text(&a["uid"])).ok().flatten();
                Ok(json!({"playback":p,"snapshot":snapshot,"interval":heartbeat_seconds(&d["heartBeatDuration"])}))
            }
            "roomOperation" => {
                let (room,sid) = (room_arg(a)?,arg_id(a,"id")?);
                let op = match text(&a["operation"]) {"add"=>1,"up"=>2,"like"=>3,"next"=>4,"heart"=>5,_=>return Err("不支持的房间操作".into())};
                let biz = if op == 1 { "0" } else { arg_id(a,"bizId")? };
                let t = self.net.check_token()?;
                let num = |value: &str| value.parse::<u64>().map(Value::from).unwrap_or_else(|_| json!(value));
                let mut last_error = String::new();
                let mut detail = String::new();
                for (song_value, biz_value, use_token) in [
                    (num(sid), num(biz), true),
                    (json!(sid), json!(biz), true),
                    (num(sid), num(biz), false),
                ] {
                    let mut body = json!({"roomId":room,"songId":song_value,"bizId":biz_value,"operate":op});
                    if use_token { body["checkToken"] = json!(t); }
                    let b = self.multi("match/song/operate", body, session)?;
                    let d = &b["data"];
                    let failed = d["failedCode"].as_i64().is_some_and(|code| code != 0)
                        || d["result"] == false
                        || d["success"] == false;
                    let kind = text(&d["failedType"]).to_owned();
                    let accepted = !failed && kind != "MULTI_SONG_NOT_SATISFIED"
                        && (d["result"] == true || d["success"] == true || d.as_bool() == Some(true)
                            || (d["failedCode"].is_null() && d["result"].is_null() && d["success"].is_null() && d.is_object() && d.as_object().is_some_and(|o| o.is_empty())));
                    if accepted { return Ok(json!({"accepted":true})); }
                    last_error = confirmed(&b, true).err().unwrap_or_else(|| "网易云没有确认这项房间操作".into());
                    let code = number(&d["failedCode"]);
                    detail = format!("code={code} type={kind} data={d}");
                    if kind == "MULTI_SONG_NOT_SATISFIED" { break; }
                }
                Err((if last_error.is_empty() { "网易云没有确认这项房间操作".to_owned() } else { format!("{last_error}｜{detail}") }).into())
            }
            "roomLeave" => {
                let b = self.multi("match/exit",json!({"roomId":room_arg(a)?,"exitType":"NORMAL_END"}),session)?;
                confirmed(&b,false)?;
                Ok(json!({"left":true}))
            }
            "roomQueue" => {
                let room = room_arg(a)?;
                let cursor = cursor(a);
                let b = self.multi("match/wait/song/list",json!({"roomId":room,"page":json!({"size":20,"cursor":cursor}).to_string()}),session)?;
                let d = &b["data"];
                let mut items = map_array(&d["songLists"], room_queue_entry);
                let ids: Vec<String> = items.iter().map(|item| text(&item["id"]).to_owned()).collect();
                if !ids.is_empty() {
                    if let Ok(details) = self.songs(&ids, session) {
                        let lookup: std::collections::HashMap<String, Value> = details.into_iter()
                            .map(|song| (text(&song["id"]).to_owned(), song)).collect();
                        for item in &mut items {
                            if let Some(song) = lookup.get(text(&item["id"])) {
                                item["duration"] = song["duration"].clone();
                                if item["artist"].as_str().unwrap_or_default().is_empty() {
                                    item["artist"] = song["artist"].clone();
                                }
                                if item["cover"].as_str().unwrap_or_default().is_empty() {
                                    item["cover"] = song["cover"].clone();
                                }
                                if item["title"].as_str().unwrap_or_default().is_empty() {
                                    item["title"] = song["title"].clone();
                                }
                            }
                        }
                    }
                }
                Ok(json!({"items":items,"cursor":string_or_number(&d["page"]["cursor"]),"more":d["page"]["more"] == true}))
            }
            "stickerGroups" => {
                let resource = if text(&a["scope"]) == "room" { 3 } else { 2 };
                let b = self.api("/api/social/emoji/groups", json!({"resourceType":resource}), eapi, session)?;
                let groups = b["data"]["emojiGroups"].as_array().ok_or("读取表情分组失败")?.iter()
                    .filter(|g| g["edit"] == true)
                    .filter_map(|g| {
                        let i = id(&g["id"]);
                        if i.is_empty() || i.starts_with('-') { return None; }
                        Some(json!({"id":i,"name":text(&g["name"]).chars().take(80).collect::<String>()}))
                    }).collect::<Vec<_>>();
                Ok(json!({"groups":groups}))
            }
            "stickerPage" => {
                let gid = text(&a["groupId"]).chars().take(32).collect::<String>();
                if gid.is_empty() || !gid.bytes().all(|b| b.is_ascii_digit() || b == b'-') { return Err("表情分组无效".into()); }
                let b = self.api("/api/social/emoji/groups/detail/page", json!({"emojiGroupId":gid,"cursor":text(&a["cursor"]).chars().take(512).collect::<String>(),"size":10}), eapi, session)?;
                let items = b["data"]["emojis"].as_array().ok_or("读取表情列表失败")?.iter().filter_map(|raw| {
                    let eid = id(&raw["emojiId"]);
                    if eid.is_empty() || eid == "0" { return None; }
                    let pic = number(&raw["picId"]).to_string();
                    let url = media_url(&raw["emojiImgUrl"]);
                    let url = if url.is_empty() && !pic.is_empty() { image_url_for_pic_id(&pic) } else { url };
                    if url.is_empty() { return None; }
                    let group = if text(&raw["emojiGroupId"]).is_empty() { gid.clone() } else { text(&raw["emojiGroupId"]).to_owned() };
                    let name = {
                        let value = text(&raw["emojiName"]).trim().chars().take(80).collect::<String>();
                        if value.is_empty() { "表情".to_owned() } else { value }
                    };
                    let format = {
                        let value = text(&raw["format"]).chars().take(16).collect::<String>();
                        if value.is_empty() { "png".to_owned() } else { value }
                    };
                    Some(json!({"emojiId":eid,"emojiGroupId":group,"name":name,"url":url,
                        "width":number(&raw["width"]).clamp(1,4096),"height":number(&raw["height"]).clamp(1,4096),
                        "format":format,"disabled":raw["enable"] == false || raw["disabled"] == true}))
                }).collect::<Vec<_>>();
                Ok(json!({"items":items,"cursor":string_or_number(&b["data"]["cursor"]),"more":b["data"]["hasMore"] == true || b["data"]["more"] == true}))
            }
            "stickerCollect" => {
                let eid = id(&a["emojiId"]);
                let gid = text(&a["emojiGroupId"]).chars().take(32).collect::<String>();
                if eid.is_empty() || eid == "0" || gid.is_empty() { return Err("表情内容无效".into()); }
                let b = self.api("/api/social/emoji/collect", json!({"emojiId":eid,"emojiGroupId":gid}), eapi, session)?;
                if b["code"] != 200 || b["data"]["result"] != true {
                    return Err(readable_error(&b, "网易云未确认收藏表情".into()).into());
                }
                Ok(json!({"collected":true}))
            }
            "stickerRemove" => {
                let ids = a["emojiIds"].as_array().ok_or("请选择要删除的表情")?;
                if ids.is_empty() || ids.len() > 100 { return Err("请选择要删除的表情".into()); }
                let ids = ids.iter().map(|v| id(v)).collect::<Vec<_>>();
                if ids.iter().any(|v| v.is_empty() || v == "0") { return Err("表情标识无效".into()); }
                let b = self.api("/api/social/emoji/cancel", json!({"emojiIds":json!(ids).to_string()}), eapi, session)?;
                if b["code"] != 200 || b["data"]["result"] != true {
                    return Err(readable_error(&b, "网易云未确认删除表情".into()).into());
                }
                Ok(json!({"removed":true}))
            }
            "stickerSend" => {
                let uid = arg_id(a,"uid")?;
                let emoji = &a["emoji"];
                let eid = id(&emoji["emojiId"]);
                let gid = text(&emoji["emojiGroupId"]).chars().take(32).collect::<String>();
                let url = media_url(&emoji["url"]);
                if eid.is_empty() || gid.is_empty() || url.is_empty() { return Err("表情内容无效".into()); }
                let token = self.net.check_token()?;
                let sender = text(&a["senderUid"]).to_owned();
                let sender = if sender.is_empty() { self.account_uid(session)? } else { sender };
                let name = {
                    let value = text(&emoji["name"]).chars().take(80).collect::<String>();
                    if value.is_empty() { "表情".to_owned() } else { value }
                };
                let format = {
                    let value = text(&emoji["format"]).chars().take(16).collect::<String>();
                    if value.is_empty() { "png".to_owned() } else { value }
                };
                let msg = json!({"scene":1,"receiverUserIds":uid,"channelId":uid,"symphonyId":"","refMsgBody":{},
                    "msgBody":{"msgType":1,"body":json!({"url":url,"emojiId":eid,"emojiGroupId":gid,
                        "width":number(&emoji["width"]).clamp(1,4096),"height":number(&emoji["height"]).clamp(1,4096),
                        "format":format,"name":name}).to_string(),
                        "unikey":format!("emoji_{}_{}",time_ms(),eid),"msgTime":time_ms(),"status":1,"sendStatus":0,
                        "sender":{"user":{"userId":sender}},"text":{"textBody":"","atBody":[]}}});
                let b = self.api("/api/communication/send/msg", json!({"checkToken":token,"sendMsgBody":msg.to_string()}), eapi, session)?;
                let status = b["data"]["msgBody"]["status"].as_i64();
                if b["code"] != 200 || b["data"]["msgBody"]["msgId"].is_null()
                    || status.is_none() || status == Some(-1) || status == Some(-2) {
                    return Err(readable_error(&b, "网易云未确认表情发送".into()).into());
                }
                Ok(json!({"sent":true}))
            }
            "roomStickerSend" => {
                let room = room_arg(a)?;
                let emoji = &a["emoji"];
                let eid = id(&emoji["emojiId"]);
                let gid = text(&emoji["emojiGroupId"]).chars().take(32).collect::<String>();
                let url = media_url(&emoji["url"]);
                if eid.is_empty() || gid.is_empty() || url.is_empty() { return Err("表情内容无效".into()); }
                let status = self.multi("match/status/get", json!({}), session)?;
                let snap = &status["data"]["multiLtRoomSnapshot"];
                if text(&snap["roomId"]) != room { return Err("账号已不在该房间，请重新加入".into()); }
                let info = snap.get("multiRoomInfoDTO").unwrap_or(&snap["roomInfo"]);
                let chat = id(&info["chatRoomId"]);
                if chat.is_empty() || chat == "0" { return Err("房间暂不可聊天".into()); }
                let name = {
                    let value = text(&emoji["name"]).chars().take(80).collect::<String>();
                    if value.is_empty() { "表情".to_owned() } else { value }
                };
                let format = {
                    let value = text(&emoji["format"]).chars().take(16).collect::<String>();
                    if value.is_empty() { "png".to_owned() } else { value }
                };
                let item = json!({"emojiId":eid,"emojiGroupId":gid,"emojiName":name,"emojiImgUrl":url,
                    "width":number(&emoji["width"]).clamp(1,4096),"height":number(&emoji["height"]).clamp(1,4096),"format":format});
                let b = self.api("/api/middle/im/chatroom/send", json!({"chatroomId":chat,"msgType":0,
                    "clientExt":json!({"bizType":"listenTogether","ltType":"MULTI_MATCH_SONG","roomId":room,"emoji":item}).to_string(),
                    "msgBody":json!({"msg":format!("[{}]",text(&emoji["name"])),"msgType":0}).to_string()}),eapi,session)?;
                if b["code"] != 200 { return Err(readable_error(&b, "网易云未确认表情发送".into()).into()); }
                Ok(json!({"sent":true}))
            }
            "uploadMedia" => {
                let kind = text(&a["kind"]).to_owned();
                if !matches!(kind.as_str(), "image" | "voice" | "video") {
                    return Err("不支持的附件类型".into());
                }
                let path = text(&a["path"]);
                let bytes = std::fs::read(path).map_err(|_| "读取附件失败".to_string())?;
                if bytes.is_empty() { return Err("附件内容为空".into()); }
                if bytes.len() > 80 * 1024 * 1024 { return Err("附件超过 80 MB".into()); }
                let mime = text(&a["mime"]).to_owned();
                let mime = if mime.is_empty() {
                    match kind.as_str() { "image" => "image/jpeg", "voice" => "audio/mp4", _ => "video/mp4" }.to_owned()
                } else { mime };
                let mut name = text(&a["name"]).to_owned();
                if name.is_empty() { name = format!("attachment.{}", if kind == "image" { "jpg" } else if kind == "voice" { "m4a" } else { "mp4" }); }
                let name: String = name.chars().filter(|c| !c.is_control()).take(120).collect();
                let mut hasher = Md5::new();
                hasher.update(&bytes);
                let md5 = format!("{:x}", hasher.finalize());
                let ext = name.rsplit_once('.').map(|(_, e)| e.to_ascii_lowercase())
                    .filter(|e| !e.is_empty() && e.len() <= 8)
                    .unwrap_or_else(|| (if kind == "image" { "jpg" } else if kind == "voice" { "m4a" } else { "mp4" }).to_owned());
                let whale = kind != "image";
                let params = if whale {
                    json!({"filename":name,"type":if kind=="voice"{"audio"}else{"video"},
                        "bucket":if kind=="voice"{"ymusic"}else{"cloudmusic"},
                        "bizKey":if kind=="voice"{"519abfd2"}else{"cb8c016e"},
                        "contentType":mime,"md5":md5,"fileSize":bytes.len()})
                } else {
                    json!({"filename":name,"type":"other","bucket":"yyimgs","nos_product":0,
                        "ext":ext,"local":false,"md5":md5,"fileSize":bytes.len()})
                };
                let alloc_path = if whale { "/api/nos/token/whalealloc" } else { "/api/nos/token/alloc" };
                let alloc = self.api(alloc_path, params, web, session)?;
                let token = if whale { alloc.get("data").unwrap_or(&alloc["result"]) } else { alloc.get("result").unwrap_or(&alloc["data"]) };
                let bucket = text(&token["bucket"]);
                let key = text(&token["objectKey"]).to_owned();
                let key = if key.is_empty() { text(&token["key"]).to_owned() } else { key };
                let upload_token = text(&token["token"]).to_owned();
                let doc = id(&token["docId"]);
                let doc = if doc.is_empty() || doc == "0" { id(&token["resourceId"]) } else { doc };
                let channel = number(&token["channel"]);
                if bucket.is_empty() || key.is_empty() || upload_token.is_empty() || doc.is_empty() {
                    return Err("未取得附件上传凭据".into());
                }
                if channel != 0 && channel != 1 { return Err("当前账号暂不可上传附件".into()); }
                let mut offset = 0usize;
                let mut context = String::new();
                while offset < bytes.len() {
                    let end = (offset + 2 * 1024 * 1024).min(bytes.len());
                    let part = self.net.nos_upload(&bucket, &key, &upload_token, &mime, offset, &bytes[offset..end], end == bytes.len(), &context)?;
                    if part["errCode"].as_i64().unwrap_or(0) != 0 || part["code"].as_i64().is_some_and(|c| c != 200) {
                        return Err("网易云存储未接受附件分片".into());
                    }
                    if let Some(ctx) = part["context"].as_str() { context = ctx.to_owned(); }
                    offset = end;
                }
                let url = text(&token["outerUrl"]).to_owned();
                let url = if url.is_empty() { text(&token["downloadUrl"]).to_owned() } else { url };
                let url = if url.is_empty() {
                    if kind == "image" { format!("https://p1.music.126.net/{key}") }
                    else { format!("https://{bucket}.nos-hz.163yun.com/{key}") }
                } else { url };
                let nos_key = format!("{bucket}/{key}");
                let target = text(&a["target"]);
                let text_label = if kind == "image" { format!("[{name}]") } else if kind == "voice" { "[语音]".to_owned() } else { "[视频]".to_owned() };
                if target == "room" {
                    if kind != "image" { return Err("一起听房间暂只支持图片附件".into()); }
                    let room = text(&a["roomId"]);
                    let status = self.multi("match/status/get",json!({}),session)?;
                    let snap = &status["data"]["multiLtRoomSnapshot"];
                    if text(&snap["roomId"]) != room { return Err("账号已不在该房间，请重新加入".into()); }
                    let info = snap.get("multiRoomInfoDTO").unwrap_or(&snap["roomInfo"]);
                    let chat = id(&info["chatRoomId"]);
                    if chat.is_empty() || chat == "0" { return Err("房间暂不可发送附件".into()); }
                    let emoji = json!({"emojiId":"0","emojiGroupId":"0","emojiName":name.chars().take(80).collect::<String>(),
                        "emojiImgUrl":url,"width":number(&a["width"]),"height":number(&a["height"]),"format":ext});
                    let b = self.api("/api/middle/im/chatroom/send",json!({"chatroomId":chat,"msgType":0,
                        "clientExt":json!({"bizType":"listenTogether","ltType":"MULTI_MATCH_SONG","roomId":room,"emoji":emoji}).to_string(),
                        "msgBody":json!({"msg":text_label,"msgType":0}).to_string()}),eapi,session)?;
                    if b["code"] != 200 || b["data"] == false { return Err(readable_error(&b,"附件发送未确认".into()).into()); }
                } else {
                    let uid = text(&a["uid"]);
                    if uid.is_empty() { return Err("缺少私信接收人".into()); }
                    let check = self.net.check_token().map_err(|_| "未取得消息校验，请稍后重试".to_string())?;
                    if check.is_empty() { return Err("未取得消息校验，请稍后重试".into()); }
                    let msg_type = if kind == "image" { 1 } else if kind == "voice" { 4 } else { 5 };
                    let body = if kind == "image" {
                        json!({"url":url,"width":number(&a["width"]),"height":number(&a["height"]),"name":name,
                            "size":bytes.len(),"md5":md5,"format":ext,"emojiId":0,"emojiGroupId":"0"})
                    } else if kind == "voice" {
                        json!({"voiceKey":doc,"nosKey":nos_key,"voiceUrl":url,
                            "duration":(number(&a["duration"]) / 1000).max(1),"localFile":false})
                    } else {
                        json!({"videoKey":doc,"nosKey":nos_key,"videoUrl":url,
                            "duration":(number(&a["duration"]) / 1000).max(1),"size":bytes.len()})
                    };
                    let msg_time = time_ms();
                    let msg_body = json!({"msgType":msg_type,"body":body.to_string(),
                        "unikey":format!("{msg_time}{:08}",rand::random::<u32>()),
                        "msgTime":msg_time,"status":1,"sendStatus":0,
                        "sender":{"user":{"userId":uid}},"text":{"textBody":"","atBody":[]}});
                    let send = json!({"scene":1,"receiverUserIds":uid,"channelId":uid,"symphonyId":"",
                        "refMsgBody":{},"msgBody":msg_body});
                    let result = self.api("/api/communication/send/msg",json!({"checkToken":check,"sendMsgBody":send.to_string()}),eapi,session)?;
                    if result["data"]["msgBody"]["status"].as_i64().is_some_and(|s| s < 0) || result["code"] != 200 {
                        return Err(readable_error(&result,"网易云未确认附件消息".into()).into());
                    }
                }
                Ok(json!({"url":url,"sent":true,"text":text_label}))
            }
            "roomChatHistory" => {
                let room = room_arg(a)?;
                let uid = text(&a["uid"]);
                let cursor = cursor(a);
                let page = if cursor.is_empty() {json!({"size":50})} else {json!({"size":50,"cursor":cursor})};
                let b = self.multi("match/msg/history",json!({"roomId":room,"direction":0,"page":page.to_string()}),session)?;
                let d = &b["data"];
                let mut seen = HashSet::new();
                let items: Vec<Value> = d["records"].as_array().into_iter().flatten().filter_map(|m| {
                    if m["roomId"].as_str().is_some_and(|r| r != room) || m["onlyCanSeeUserIds"].as_array().is_some_and(|u| !u.is_empty() && !u.iter().any(|v|id(v) == uid)) { return None; }
                    let from = id(&m["sendUid"]);
                    let time = number(&m["sendTime"]);
                    let key = format!("{from}:{time}");
                    let message = text(&m["imChatRoomMsgBody"]["text"]);
                    if message.is_empty() || !seen.insert(key.clone()) { return None; }
                    Some(json!({"key":key,"name":text(&m["nickname"]),"text":message,"time":time}))
                }).collect();
                Ok(json!({"items":items,"cursor":string_or_number(&d["page"]["cursor"]),"more":d["page"]["more"] == true}))
            }
            "roomChatSend" => {
                let room = room_arg(a)?;
                let message = text(&a["text"]).trim();
                if message.is_empty() || message.encode_utf16().count() > 100 { return Err("消息须在 100 字以内".into()); }
                let status = self.multi("match/status/get",json!({}),session)?;
                let snap = &status["data"]["multiLtRoomSnapshot"];
                if text(&snap["roomId"]) != room { return Err("账号已不在该房间，请重新加入".into()); }
                let info = snap.get("multiRoomInfoDTO").unwrap_or(&snap["roomInfo"]);
                let chat = id(&info["chatRoomId"]);
                if chat.is_empty() || chat == "0" { return Err("房间暂不可聊天".into()); }
                let b = self.api("/api/middle/im/chatroom/send",json!({"chatroomId":chat,"msgType":0,
                    "clientExt":json!({"bizType":"listenTogether","ltType":"MULTI_MATCH_SONG","roomId":room}).to_string(),
                    "msgBody":json!({"msg":message,"msgType":0}).to_string()}),eapi,session)?;
                if b["code"] != 200 || b["data"] == false { return Err(readable_error(&b,"未确认发送，请刷新聊天记录".into()).into()); }
                Ok(json!({"sent":true}))
            }
            _ => Err("不支持的音乐操作".into()),
        }
    }

    fn songs(&self, ids: &[String], session: &Cookies) -> Result<Vec<Value>> {
        if ids.is_empty() { return Ok(vec![]); }
        let raw = ids.iter().map(|s|format!("{{\"id\":{s}}}")).collect::<Vec<_>>().join(",");
        let b = self.api("/api/v3/song/detail",json!({"c":format!("[{raw}]")}),Wire::Web,session)?;
        let songs: std::collections::HashMap<String,Value> = map_array(&b["songs"],song).into_iter()
            .map(|s| (text(&s["id"]).to_owned(),s)).collect();
        Ok(ids.iter().filter_map(|sid| songs.get(sid).cloned()).collect())
    }
}

fn album_track_page(body: &Value, offset: u64) -> Result<Value> {
    let rows = body["songs"].as_array().ok_or("网易云返回的专辑曲目格式不正确")?;
    let all: Vec<_> = rows.iter().filter_map(song).collect();
    let start = offset.min(all.len() as u64) as usize;
    let end = (start + 100).min(all.len());
    Ok(json!({"items":&all[start..end],"more":end<all.len(),"total":all.len(),"next":end}))
}

#[cfg(test)]
mod catalog_tests {
    use super::*;

    #[test]
    fn album_tracks_use_the_top_level_songs_in_album_order() {
        let body = json!({"album":{"id":82985925,"size":2},"songs":[
            {"id":13,"name":"First track"}, {"id":7,"name":"Second track"}
        ]});
        let reply = album_track_page(&body, 0).unwrap();
        assert_eq!(reply["items"][0]["id"], "13");
        assert_eq!(reply["items"][1]["id"], "7");
        assert_eq!(reply["total"], 2);
        assert_eq!(reply["more"], false);
    }

    #[test]
    fn room_queue_entry_reads_song_info_and_artist_array() {
        let row = json!({"nickname":"Alice","uped":true,"songInfo":{
            "resourceId":347230,"bizId":9007199254740993u64,"title":"Song",
            "artistName":["A","B"],"coverUrl":"http://p1.music.126.net/x.jpg","upCnt":"3"
        }});
        let entry = room_queue_entry(&row).unwrap();
        assert_eq!(entry["id"], "347230");
        assert_eq!(entry["artist"], "A / B");
        assert_eq!(entry["owner"], "Alice");
        assert_eq!(entry["uped"], "up");
        assert_eq!(entry["upCount"], 3);
        assert_eq!(entry["likeCount"], 0);
        assert_eq!(entry["duration"], 0);
        assert!(entry["cover"].as_str().unwrap_or_default().starts_with("https://"));
    }

    #[test]
    fn room_queue_entry_rejects_rows_without_a_song() {
        assert!(room_queue_entry(&json!({"nickname":"Alice","songInfo":{}})).is_none());
    }
}

fn sms_phone(raw: &str) -> Result<String> {
    let mut digits: String = raw.chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.len() == 13 && digits.starts_with("86") { digits = digits[2..].to_owned(); }
    if digits.len() != 11 || !digits.starts_with('1') {
        return Err("请输入 11 位手机号".into());
    }
    Ok(digits)
}

fn arg_id<'a>(a: &'a Value, key: &str) -> Result<&'a str> {
    let value = text(&a[key]);
    if valid_id(value) && !value.starts_with('0') { Ok(value) } else { Err("歌曲、歌单或用户标识无效".into()) }
}

fn room_arg(a: &Value) -> Result<&str> {
    let room = text(&a["roomId"]);
    if valid_room(room) { Ok(room) } else { Err("房间标识无效".into()) }
}

fn attached(d: &Value, at: u64, uid: &str) -> Result<Option<Value>> {
    let room = &d["multiLtRoomSnapshot"];
    if room.is_null() { return Ok(None); }
    Ok(Some(snapshot(room, at, uid)?))
}

fn string_or_number(v: &Value) -> String {
    v.as_str().map(str::to_owned).or_else(|| v.as_number().map(ToString::to_string)).unwrap_or_default()
}

fn image_url_for_pic_id(value: &str) -> String {
    if value.is_empty() || value.len() > 24 || !value.bytes().all(|b| b.is_ascii_digit()) { return String::new(); }
    let key = b"3go8&$8*3*3h0k(2)2";
    let mut bytes = value.as_bytes().to_vec();
    for i in 0..bytes.len() { bytes[i] ^= key[i % key.len()]; }
    use md5::Digest;
    let digest = md5::Md5::digest(&bytes);
    let encoded = base64::Engine::encode(&base64::engine::general_purpose::STANDARD, digest)
        .replace('/', "_").replace('+', "-");
    format!("https://p1.music.126.net/{encoded}/{value}.jpg")
}

fn cursor(v: &Value) -> &str { text(&v["cursor"]).get(..text(&v["cursor"]).len().min(512)).unwrap_or("") }

fn room_queue_entry(v: &Value) -> Option<Value> {
    let info = &v["songInfo"];
    let sid = id(&info["resourceId"]);
    let biz = {
        let value = id(&info["bizId"]);
        if value.is_empty() { id(&v["bizId"]) } else { value }
    };
    if sid.is_empty() || biz.is_empty() { return None; }
    let artist = match &info["artistName"] {
        Value::Array(list) => list.iter().filter_map(|a| a.as_str()).collect::<Vec<_>>().join(" / "),
        _ => text(&info["artistName"]).to_owned(),
    };
    let up = if info["uped"].as_bool() == Some(true) || v["uped"].as_bool() == Some(true) { "up" } else { "" };
    Some(json!({"id":sid,"bizId":biz,"title":text(&info["title"]),"artist":artist,
        "cover":media_url(&info["coverUrl"]),"owner":text(&v["nickname"]),"uped":up,
        "upCount":number(&info["upCnt"]),"likeCount":number(&info["zanCnt"]),"duration":0}))
}

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "system" fn Java_dev_yiqidi_mobile_NativeBridge_callNative(
    mut env: jni::JNIEnv<'_>, _: jni::objects::JClass<'_>, method: jni::objects::JString<'_>, input: jni::objects::JString<'_>
) -> jni::sys::jstring {
    let method = env.get_string(&method).map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    let input = env.get_string(&input).map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    env.new_string(dispatch(&method,&input)).map(|s|s.into_raw()).unwrap_or(std::ptr::null_mut())
}