use aes::cipher::{generic_array::GenericArray, BlockEncrypt, KeyInit};
use aes::Aes128;
use base64::{engine::general_purpose::STANDARD, Engine};
use md5::{Digest, Md5};
use num_bigint::BigUint;
use rand::{distributions::Alphanumeric, Rng, RngCore};
use serde_json::{json, Value};
use std::{collections::BTreeMap, io::Read, time::Duration};

const WEB_HOST: &str = "https://music.163.com";
const EAPI_HOST: &str = "https://interfacepc.music.163.com";
const WEB_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
const APP_AGENT: &str = "NeteaseMusicDesktop/3.1.29.205117";
const WEB_KEY: &[u8; 16] = b"0CoJUm6Qyw8W8jud";
const EAPI_KEY: &[u8; 16] = b"e82ckenh8dichen8";
const MODULUS: &[u8] = b"00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";

pub type Cookies = BTreeMap<String, String>;

#[derive(Clone, Copy, PartialEq)]
pub enum Wire { Web, Eapi }

#[derive(Debug)]
pub struct Failure { pub code: i64, pub message: String }
impl From<String> for Failure {
    fn from(message: String) -> Self { Self { code: 0, message } }
}
impl From<&str> for Failure {
    fn from(message: &str) -> Self { message.to_owned().into() }
}
pub type Result<T> = std::result::Result<T, Failure>;

pub struct Reply { pub body: Value, pub cookies: Cookies }

pub struct Connection { agent: ureq::Agent, device_id: String }

impl Default for Connection {
    fn default() -> Self {
        let agent = ureq::AgentBuilder::new().redirects(0)
            .timeout_connect(Duration::from_secs(7)).timeout(Duration::from_secs(12));
        #[cfg(not(target_os = "android"))]
        let agent = agent.tls_connector(std::sync::Arc::new(ureq::native_tls::TlsConnector::new().expect("system TLS")));
        let mut device = [0u8; 16];
        rand::thread_rng().fill_bytes(&mut device);
        let generated = hex(&device, false);
        let device_id = DEVICE_ID.get().cloned().unwrap_or(generated);
        Self { agent: agent.build(), device_id }
    }
}

static DEVICE_ID: std::sync::OnceLock<String> = std::sync::OnceLock::new();

pub fn set_device_id(value: &str) {
    let clean: String = value.chars().filter(|c| c.is_ascii_alphanumeric() || *c == '-').take(40).collect();
    if !clean.is_empty() {
        let _ = DEVICE_ID.set(clean);
    }
}

impl Connection {
    pub fn request(&self, path: &str, mut data: Value, mode: Wire, cookies: &Cookies, qr: bool) -> Result<Reply> {
        let suffix = path.strip_prefix("/api/").ok_or("未知网易云接口")?;
        let csrf = cookies.get("__csrf").map(String::as_str).unwrap_or_default();
        let (url, headers, pairs, ua) = if mode == Wire::Web {
            data["csrf_token"] = json!(csrf);
            let random: [u8;16] = std::array::from_fn(|_| rand::thread_rng().sample(Alphanumeric));
            (format!("{WEB_HOST}/weapi/{suffix}"), cookie_string(cookies), web_form(&data.to_string(), &random), WEB_AGENT)
        } else {
            let ms = crate::native::time_ms();
            let mut header = json!({"os":"pc","osver":"Microsoft-Windows-10-Professional-build-19045-64bit",
                "appver":"3.1.17.204416","deviceId":self.device_id,"versioncode":"140","mobilename":"",
                "buildver":(ms / 1000).to_string(),"resolution":"1920x1080","channel":"netease","__csrf":csrf,
                "requestId":format!("{ms}_{:04}",rand::thread_rng().gen_range(0..10000)), "NMTID": cookies.get("NMTID").map(String::as_str).unwrap_or(&self.device_id)});
            for key in ["MUSIC_U", "MUSIC_A"] {
                if let Some(value) = cookies.get(key) { header[key] = json!(value); }
            }
            let headers = header.as_object().unwrap().iter().map(|(k,v)| (k.to_owned(),v.as_str().unwrap_or_default().to_owned())).collect();
            data["header"] = header;
            data["e_r"] = json!(false);
            (format!("{EAPI_HOST}/eapi/{suffix}"), cookie_string(&headers), vec![("params", eapi_params(path, &data.to_string()))], APP_AGENT)
        };
        let form = url::form_urlencoded::Serializer::new(String::new()).extend_pairs(pairs).finish();
        let reply = self.agent.post(&url).set("User-Agent", ua).set("Referer", "https://music.163.com/")
            .set("Accept", "application/json").set("Cookie", &headers)
            .set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").send_string(&form);
        let reply = match reply {
            Ok(response) | Err(ureq::Error::Status(_, response)) => response,
            Err(_) => return Err("连接网易云超时或失败，请检查网络".into()),
        };
        let status = reply.status();
        let response_cookies = read_cookies(reply.all("set-cookie").into_iter().filter_map(|s| s.split(';').next()));
        let mut bytes = Vec::new();
        reply.into_reader().take(8 * 1024 * 1024).read_to_end(&mut bytes).map_err(|_| "网易云响应读取失败")?;
        let body: Value = serde_json::from_slice(&bytes).map_err(|_| Failure {
            code: status.into(), message: format!("网易云返回了无法读取的数据（HTTP {status}）")
        })?;
        let code = body["code"].as_i64().or_else(|| body["code"].as_str().and_then(|x| x.parse().ok())).unwrap_or(status.into());
        if code != 200 && !(qr && (800..=803).contains(&code)) {
            let message = match code {
                301 | 302 => "请先登录网易云音乐".into(),
                488 => "多人房间已失效，请重新加入".into(),
                _ => readable_error(&body, format!("网易云未接受这项请求（{code}）")),
            };
            return Err(Failure { code, message });
        }
        if status >= 400 && !(qr && (800..=803).contains(&code)) {
            return Err(Failure { code: status.into(), message: format!("网易云请求失败（HTTP {status}）") });
        }
        Ok(Reply { body, cookies: response_cookies })
    }

    pub fn check_token(&self) -> Result<String> {
        let response = self.agent.get("https://ac.dun.163yun.com/v3/b?pn=YD00000558929251").call()
            .map_err(|_| "网易云未签发房间请求校验，请稍后重试")?;
        let mut text = String::new();
        response.into_reader().take(16_384).read_to_string(&mut text).map_err(|_| "读取网易云校验失败")?;
        let data = text.trim().trim_end_matches(';').strip_prefix("null(").and_then(|x| x.strip_suffix(')'));
        let fields: Value = data.and_then(|x| serde_json::from_str(x).ok()).unwrap_or(Value::Null);
        fields.get(2).and_then(Value::as_str).filter(|v| fields[0] == 200 && !v.is_empty())
            .map(str::to_owned).ok_or_else(|| "网易云未签发房间请求校验，请稍后重试".into())
    }
}

pub fn readable_error(body: &Value, default: String) -> String {
    ["failedMessage", "failedMsg", "message", "msg"].into_iter().filter_map(|k| body[k].as_str())
        .find(|s| !s.trim().is_empty() && !s.contains("MUSIC_") && !s.contains("checkToken"))
        .map(|s| s.chars().filter(|c| !c.is_control()).take(160).collect()).unwrap_or(default)
}

pub fn read_cookies<'a>(pairs: impl IntoIterator<Item = &'a str>) -> Cookies {
    pairs.into_iter().filter_map(|s| {
        let (key, value) = s.trim_matches(' ').split_once('=')?;
        if !matches!(key, "MUSIC_U" | "MUSIC_A" | "__csrf" | "__remember_me" | "NMTID") || value.len() > 4096
            || !value.bytes().all(|b| b.is_ascii_graphic() && b != b';') || value == "deleted" || value.is_empty() { return None; }
        Some((key.to_owned(), value.to_owned()))
    }).collect()
}

pub fn cookie_string(cookies: &Cookies) -> String {
    cookies.iter().map(|(k,v)| format!("{k}={}",url::form_urlencoded::byte_serialize(v.as_bytes()).collect::<String>())).collect::<Vec<_>>().join("; ")
}

fn hex(bytes: &[u8], upper: bool) -> String {
    if upper { bytes.iter().map(|b| format!("{b:02X}")).collect() }
    else { bytes.iter().map(|b| format!("{b:02x}")).collect() }
}

fn encrypt(plain: &[u8], key: &[u8; 16], mut previous: Option<[u8;16]>) -> Vec<u8> {
    let cipher = Aes128::new(GenericArray::from_slice(key));
    let padding = 16 - plain.len() % 16;
    let mut buf = plain.to_vec();
    buf.extend(std::iter::repeat_n(padding as u8, padding));
    for chunk in buf.chunks_exact_mut(16) {
        if let Some(iv) = previous { for (byte, old) in chunk.iter_mut().zip(iv) { *byte ^= old; } }
        cipher.encrypt_block(GenericArray::from_mut_slice(chunk));
        if let Some(old) = previous.as_mut() { old.copy_from_slice(chunk); }
    }
    buf
}

fn eapi_params(path: &str, plain: &str) -> String {
    let digest = hex(&Md5::digest(format!("nobody{path}use{plain}md5forencrypt").as_bytes()), false);
    hex(&encrypt(format!("{path}-36cd479b6b5-{plain}-36cd479b6b5-{digest}").as_bytes(), EAPI_KEY, None), true)
}

fn web_form(plain: &str, key: &[u8;16]) -> Vec<(&'static str,String)> {
    let iv = Some(*b"0102030405060708");
    let stage = STANDARD.encode(encrypt(plain.as_bytes(), WEB_KEY, iv));
    let encoded = STANDARD.encode(encrypt(stage.as_bytes(), key, iv));
    let mut reversed = *key;
    reversed.reverse();
    let modulus = BigUint::parse_bytes(MODULUS, 16).unwrap();
    let secret = BigUint::from_bytes_be(&reversed).modpow(&BigUint::from(65537u32), &modulus);
    vec![("params", encoded), ("encSecKey", format!("{secret:0>256x}"))]
}

impl Connection {
    pub fn nos_upload(&self, bucket: &str, key: &str, token: &str, mime: &str, offset: usize, bytes: &[u8], complete: bool, context: &str) -> Result<Value> {
        let encoded: String = url::form_urlencoded::byte_serialize(key.as_bytes()).collect();
        let mut url = format!("https://nosup-hz1.127.net/{bucket}/{encoded}?offset={offset}&complete={complete}&version=1.0");
        if !context.is_empty() {
            let encoded_context: String = url::form_urlencoded::byte_serialize(context.as_bytes()).collect();
            url.push_str("&context=");
            url.push_str(&encoded_context);
        }
        let response = self.agent.post(&url)
            .set("x-nos-token", token).set("Content-Type", mime)
            .send_bytes(bytes).map_err(|error| Failure { code: 0, message: format!("附件上传失败：{error}") })?;
        response.into_json::<Value>().map_err(|_| "附件上传返回格式异常".into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn response_cookies_only_keep_named_session_fields() {
        let c = read_cookies(["Path=/", "MUSIC_U=sample", "__csrf=", "NMTID=abc", "Max-Age=3", "MUSIC_A=bad\r\n"]);
        assert_eq!(cookie_string(&c), "MUSIC_U=sample; NMTID=abc");
    }

    #[test]
    fn web_encryption_uses_two_cbc_passes_and_fixed_width_rsa() {
        let form = web_form("{}", b"abcdefghijklmnop");
        assert_eq!(form[0].1.len(), 44);
        assert_eq!(form[1].1.len(), 256);
    }
}