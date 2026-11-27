use super::{failure, number, text, App, Update};
use serde_json::{json, Map, Value};

#[derive(Default)]
pub(super) struct AccountState {
    value: Value,
    qr_url: String,
    qr_key: String,
    qr_scanned: bool,
    qr_deadline: u64,
    busy: bool,
    pending_storage: Option<(u64, String)>,
    signing_out: bool,
}

impl AccountState {
    pub fn uid(&self) -> &str { text(&self.value["uid"]) }
    pub fn has_qr(&self) -> bool { !self.qr_url.is_empty() }

    pub fn view(&self) -> Map<String, Value> {
        json!({"account":self.value,"accountBusy":self.busy,"qrKey":self.qr_key,"qrUrl":self.qr_url,"qrScanned":self.qr_scanned})
            .as_object().unwrap().clone()
    }

    fn clear_qr(&mut self) {
        self.qr_key.clear();
        self.qr_url.clear();
        self.qr_scanned = false;
        self.qr_deadline = 0;
    }
}

impl App {
    pub(super) fn restore_account(&mut self, saved: &str, out: &mut Update) {
        if !saved.is_empty() {
            self.account.busy = true;
            self.request("session", "setSession", json!({"session":saved}), 0, out);
        }
    }

    pub(super) fn account_event(&mut self, event: &str, out: &mut Update) {
        match event {
            "login" if !self.account.busy && self.account.uid().is_empty() => {
                self.message("", false);
                self.account.clear_qr();
                self.account.busy = true;
                self.request("qr", "qrCreate", Value::Null, 0, out);
            }
            "cancelLogin" if !self.account.qr_url.is_empty() || self.jobs.contains_key("qr") => {
                self.cancel("qr", out);
                self.account.clear_qr();
                self.account.busy = false;
            }
            "logout" if !self.account.signing_out => {
                for key in ["qr", "session", "account", "liked", "likedTracks", "favorite", "playlists", "albums", "collection", "search", "lyrics"] { self.cancel(key, out); }
                self.room_finish(out);
                self.library.reset_online();
                self.account = AccountState { busy:true, signing_out:true, ..AccountState::default() };
                let tag = self.next_tag();
                self.account.pending_storage = Some((tag, String::new()));
                out.effects.push(json!({"kind":"session","tag":tag,"value":""}));
                self.request("session", "logout", Value::Null, 0, out);
            }
            _ => return,
        }
        out.ui = true;
    }

    pub(super) fn session_saved(&mut self, a: &Value, out: &mut Update) {
        let Some((tag, _)) = self.account.pending_storage.as_ref() else { return; };
        if number(&a["tag"]) != *tag { return; }
        let (_, session) = self.account.pending_storage.take().unwrap();
        if a["ok"] != true {
            self.account.busy = false;
            self.message(if session.is_empty() { "未能清除已保存的登录，请重试退出" }
                else { "系统密钥库未能安全保存登录状态，请重新扫码" }, true);
            out.ui = true;
        } else if !session.is_empty() {
            self.request("session", "setSession", json!({"session":session}), 0, out);
        }
    }

    pub(super) fn account_reply(&mut self, slot: &str, r: &Value, now: u64, out: &mut Update) {
        out.ui = true;
        let err = failure(r);
        match slot {
            "session" if self.account.signing_out => {
                self.account.signing_out = false;
                self.account.busy = false;
                if let Some(err) = err { self.message(err, true); }
            }
            "session" => match err {
                Some(err) => { self.account.busy = false; self.message(err, true); }
                None => self.request("account", "account", Value::Null, 0, out),
            },
            "account" => {
                self.account.busy = false;
                if let Some(err) = err { self.message(err, true); return; }
                if r["loggedIn"] == true && !text(&r["uid"]).is_empty() {
                    self.account.value = r.clone();
                    self.refresh_likes(out);
                    self.activate_library(out);
                    self.room_event("roomRefresh", &Value::Null, now, out);
                } else {
                    self.account.value = Value::Null;
                    self.message("网易云登录已失效，请重新扫码", true);
                }
            }
            "liked" => self.likes_reply(r, out),
            "qr" if self.account.qr_key.is_empty() => {
                self.account.busy = false;
                if let Some(err) = err { self.message(err, true); return; }
                let key = text(&r["key"]);
                let url = text(&r["url"]);
                if key.is_empty() || url.is_empty() { self.message("未取得登录二维码", true); return; }
                self.account.qr_key = key.to_owned();
                self.account.qr_url = url.to_owned();
                self.account.qr_deadline = now + 300_000;
                self.qr_poll(out);
            }
            "qr" => match number(&r["code"]) {
                803 if !text(&r["session"]).is_empty() => {
                    self.account.clear_qr();
                    self.account.busy = true;
                    let tag = self.next_tag();
                    let session = text(&r["session"]).to_owned();
                    self.account.pending_storage = Some((tag, session.clone()));
                    out.effects.push(json!({"kind":"session","tag":tag,"value":session}));
                }
                800 => { self.account.clear_qr(); self.message("登录二维码已过期，请重新获取", true); }
                _ if now >= self.account.qr_deadline => {
                    self.account.clear_qr();
                    self.message("二维码已超时，请重新获取", true);
                }
                802 => { self.account.qr_scanned = true; self.qr_poll(out); }
                _ => self.qr_poll(out),
            },
            _ => {}
        }
    }

    fn qr_poll(&mut self, out: &mut Update) {
        self.request("qr", "qrCheck", json!({"key":self.account.qr_key}), 2000, out);
    }
}