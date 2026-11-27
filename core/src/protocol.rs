use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use std::collections::HashSet;
use url::Url;

const SHARE: &str = "https://st.music.163.com/listen-together/multishare/index.html";

#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Invite {
    pub room_id: String,
    pub inviter_uid: String,
}

pub fn parse_invite(input: &str) -> Result<Invite, String> {
    let normalized = input.replace("\\&", "&").replace("&amp;", "&").replace("&#38;", "&").replace("&#x26;", "&");
    let source = normalized.find("https://").map(|i| &normalized[i..]).ok_or("请粘贴完整的网易云多人邀请链接")?;
    let candidate = source.split(|c: char| c.is_whitespace() || "<>()[]\"“”".contains(c)).next().unwrap_or("");
    let url = Url::parse(candidate).map_err(|_| "邀请链接格式不正确")?;
    if url.scheme() != "https" || url.host_str() != Some("st.music.163.com") || !url.username().is_empty() || url.password().is_some() || url.port().is_some() || url.path().trim_end_matches('/') != "/listen-together/multishare/index.html" {
        return Err("只支持网易云官方多人一起听邀请，不支持双人或跟听链接".into());
    }
    let mut args = std::collections::HashMap::new();
    for (k,v) in url.query_pairs() {
        if matches!(k.as_ref(), "roomId" | "inviterUid" | "isFLT") && args.insert(k.into_owned(), v.into_owned()).is_some() {
            return Err("邀请参数重复".into());
        }
    }
    if args.get("isFLT").is_some_and(|v| v != "false") {
        return Err("不支持跟听模式".into());
    }
    let room_id = args.remove("roomId").filter(|x| valid_room(x)).ok_or("邀请缺少有效房间 ID")?;
    let inviter_uid = args.remove("inviterUid").filter(|x| valid_id(x)).ok_or("邀请缺少有效邀请人 ID")?;
    Ok(Invite { room_id, inviter_uid })
}

pub fn share_url(room: &str, inviter: &str) -> String {
    format!("{SHARE}?roomId={room}&inviterUid={inviter}&isFLT=false")
}

pub fn valid_room(s: &str) -> bool {
    !s.is_empty() && s.len() <= 128 && s.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}

pub fn valid_id(s: &str) -> bool {
    !s.is_empty() && s.len() <= 24 && s.bytes().all(|b| b.is_ascii_digit())
}

pub(crate) fn online_id(s: &str) -> bool {
    valid_id(s) && !s.starts_with('0')
}

pub(crate) fn online_song(v: &Value) -> bool {
    online_id(text(&v["id"])) && text(&v["localUri"]).is_empty()
}

pub fn id(v: &Value) -> String {
    let s = match v { Value::String(s) => s.to_owned(), Value::Number(n) => n.to_string(), _ => String::new() };
    if valid_id(&s) { s } else { String::new() }
}

pub fn text(v: &Value) -> &str { v.as_str().unwrap_or_default() }

pub fn number(v: &Value) -> u64 { v.as_u64().or_else(|| text(v).parse().ok()).unwrap_or(0) }

pub fn media_url(v: &Value) -> String {
    let Ok(mut u) = Url::parse(text(v)) else { return String::new() };
    let trusted = u.host_str().is_some_and(|h| ["music.126.net", "music.163.com", "126.net", "127.net", "netease.com"].iter().any(|d| h == *d || h.strip_suffix(d).is_some_and(|p| p.ends_with('.'))));
    if !trusted || !u.username().is_empty() || u.password().is_some() || u.port().is_some() { return String::new() }
    if u.scheme() == "http" { let _ = u.set_scheme("https"); }
    if u.scheme() == "https" { u.into() } else { String::new() }
}

pub fn song(v: &Value) -> Option<Value> {
    let song_id = id(&v["id"]);
    if song_id.is_empty() || song_id == "0" { return None; }
    let al = v.get("al").or_else(|| v.get("album")).unwrap_or(&Value::Null);
    let artists = v.get("ar").or_else(|| v.get("artists")).and_then(Value::as_array);
    let names = artists.into_iter().flatten().filter_map(|a| a["name"].as_str()).collect::<Vec<_>>().join(" / ");
    let cover = [ &al["picUrl"], &v["picUrl"] ].into_iter().map(media_url).find(|x| !x.is_empty()).unwrap_or_default();
    Some(json!({"id":song_id,"title":text(&v["name"]),"artist":names,"album":text(&al["name"]),"cover":cover,"duration":number(v.get("dt").unwrap_or(&v["duration"])),"albumId":id(&al["id"]),"artistId":artists.and_then(|a| a.first()).map(|a| id(&a["id"])).unwrap_or_default()}))
}

pub fn collection(v: &Value) -> Option<Value> {
    let i = id(&v["id"]);
    if !online_id(&i) { return None; }
    let cover = v.get("coverImgUrl").or_else(|| v.get("picUrl")).or_else(|| v.get("img1v1Url")).unwrap_or(&Value::Null);
    let owner = v["creator"].get("userId").unwrap_or(&v["userId"]);
    Some(json!({"id":i,"title":text(&v["name"]),"cover":media_url(cover),"count":number(&v["trackCount"]),"owner":id(owner),"detail":text(&v["creator"]["nickname"]),"specialType":number(&v["specialType"]),"kind":"playlist"}))
}

pub fn album(v: &Value) -> Option<Value> {
    let i = id(&v["id"]);
    if !online_id(&i) { return None; }
    let artists = v["artists"].as_array().into_iter().flatten().filter_map(|a| a["name"].as_str()).collect::<Vec<_>>().join(" / ");
    let cover = [&v["picUrl"], &v["coverImgUrl"]].into_iter().map(media_url).find(|url| !url.is_empty()).unwrap_or_default();
    Some(json!({"id":i,"title":text(&v["name"]),"cover":cover,"count":number(&v["size"]),
        "detail":if artists.is_empty() { text(&v["artist"]["name"]) } else { &artists },"kind":"album"}))
}

pub fn artist(v: &Value) -> Option<Value> {
    let i = id(&v["id"]);
    if !online_id(&i) { return None; }
    let cover = [&v["picUrl"], &v["img1v1Url"]].into_iter().map(media_url).find(|url| !url.is_empty()).unwrap_or_default();
    Some(json!({"id":i,"title":text(&v["name"]),"cover":cover,"kind":"artist"}))
}

pub fn map_array<T>(v: &Value, mapper: impl Fn(&Value) -> Option<T>) -> Vec<T> {
    v.as_array().into_iter().flatten().filter_map(mapper).collect()
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all="camelCase")]
pub struct Playback {
    pub song_id: String,
    pub biz_id: String,
    pub version: u64,
    pub position: u64,
    pub duration: u64,
    pub sampled_at: u64,
    pub force: bool,
    pub count: u64,
    pub likes: u64,
}

pub fn playback(v: &Value, sampled_at: u64) -> Result<Option<Playback>, String> {
    if v.get("playSong").is_none() { return Ok(None); }
    let version = v["version"].as_u64().ok_or("房间快照版本无效")?;
    let position = v["playedTime"].as_u64().ok_or("房间进度无效")?;
    let item = &v["playSong"];
    let song_id = id(&item["songId"]);
    let biz_id = id(&item["songBizId"]);
    if !item.is_null() && (song_id.is_empty() || biz_id.is_empty()) { return Err("房间歌曲标识无效".into()); }
    Ok(Some(Playback { song_id, biz_id, version, position, duration:number(&v["songDuration"]), sampled_at, force:v["forceSync"] == true, count:number(&v["waitSongCount"]), likes:number(&v["playingSongZanCnt"]) }))
}

pub fn snapshot(v: &Value, sampled_at: u64, fallback_uid: &str) -> Result<Value, String> {
    let room = text(&v["roomId"]);
    if !valid_room(room) { return Err("响应缺少有效的多人房间".into()); }
    let info = v.get("multiRoomInfoDTO").or_else(|| v.get("roomInfo")).unwrap_or(&Value::Null);
    let users = v.get("multiLtRoomUserAgg").or_else(|| v.get("roomUserList")).unwrap_or(&Value::Null);
    let members = users.get("onlineUserInfos").unwrap_or(&info["roomUsers"]);
    let mut seen = HashSet::new();
    let list: Vec<Value> = members.as_array().into_iter().flatten().filter_map(|m| {
        let uid = id(m.get("uid").unwrap_or(&m["userId"]));
        if uid.is_empty() || !seen.insert(uid.clone()) { return None; }
        Some(json!({"id":uid,"title":m["nickname"].as_str().unwrap_or("听友"),"cover":media_url(m.get("avatar").unwrap_or(&m["avatarUrl"]))}))
    }).collect();
    let owner = [ &v["inviterUid"], &info["creatorUid"], &info["hostUid"] ].into_iter().map(id).find(|x| !x.is_empty()).unwrap_or_else(|| fallback_uid.into());
    let online = users["onlineNums"].as_u64().or_else(|| members.is_array().then_some(list.len() as u64));
    let kind = match number(&info["roomBizType"]) { 1 => "私密好友房", 2 => "公开好友房", 3 => "公开匹配房", _ => "官方多人房间" };
    Ok(json!({"roomId": room,"kind":kind,"invite":if valid_id(&owner) {share_url(room,&owner)} else {String::new()},"chatId":id(&info["chatRoomId"]),"members":list,"online":online,"playback":playback(&v["roomPlaySongInfo"],sampled_at)? }))
}

pub fn heartbeat_seconds(v: &Value) -> u64 {
    match number(v) { 0 => 5, n => n.clamp(1,60) }
}

pub fn confirmed(v: &Value, operation: bool) -> Result<(), String> {
    let d = &v["data"];
    let accepted = if operation { d["failedCode"].as_i64() == Some(0) && d["result"] != false } else { d["success"] == true };
    if accepted { Ok(()) } else if d["failedType"] == "MULTI_SONG_NOT_SATISFIED" { Err("这首歌不符合官方开房条件，请换一首可完整播放的歌曲".into()) } else { Err("网易云没有确认这项房间操作，请刷新房间状态".into()) }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn invites_preserve_large_id_and_never_accept_other_modes() {
        let p = parse_invite("来听歌 [链接](https://st.music.163.com/listen-together/multishare/index.html?roomId=ab_2\\&amp;inviterUid=9223372036854775810&amp;isFLT=false)").unwrap();
        assert_eq!(p.room_id, "ab_2");
        assert_eq!(p.inviter_uid, "9223372036854775810");
        for invalid in [
            "https://st.music.163.com.evil.example/listen-together/multishare/index.html?roomId=x&inviterUid=2",
            "https://st.music.163.com/listen-together/multishare/index.html?roomId=x&roomId=y&inviterUid=2",
            "https://st.music.163.com/listen-together/multishare/index.html?roomId=x&inviterUid=2&isFLT=true",
            "https://st.music.163.com/listen-together/share?roomId=x&inviterUid=2",
        ] {
            assert!(parse_invite(invalid).is_err(), "{invalid}");
        }
    }
}