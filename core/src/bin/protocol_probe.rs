use yiqidi_core::{lyrics, protocol::{confirmed, parse_invite, snapshot}, reconcile::{plan, SyncInput}};
use serde_json::{json, Value};
use std::io::{self,BufRead};

fn run(line:&str)->Result<Value,String>{
    let request:Value=serde_json::from_str(line).map_err(|e|e.to_string())?;
    match request["method"].as_str().unwrap_or_default(){
        "invite"=>serde_json::to_value(parse_invite(request["input"].as_str().unwrap_or_default())?).map_err(|e|e.to_string()),
        "snapshot"=>snapshot(&request["input"],request["sampledAt"].as_u64().unwrap_or(0),request["uid"].as_str().unwrap_or_default()),
        "sync"=>{let i:SyncInput=serde_json::from_value(request["input"].clone()).map_err(|e|e.to_string())?;serde_json::to_value(plan(&i)).map_err(|e|e.to_string())},
        "lyrics"=>serde_json::to_value(lyrics::lines(request["lrc"].as_str().unwrap_or_default(),request["translation"].as_str().unwrap_or_default())).map_err(|e|e.to_string()),
        "confirmation"=>confirmed(&request["input"],request["operation"].as_bool().unwrap_or(false)).map(|_|json!({"confirmed":true})),
        "url"=>Ok(json!({"url":yiqidi_core::protocol::media_url(&request["input"])})),
        "native"=>serde_json::from_str(&yiqidi_core::native::dispatch(request["name"].as_str().unwrap_or_default(),&request["input"].to_string())).map_err(|e|e.to_string()),
        _=>Err("unknown method".into()),
    }
}

fn main(){for line in io::stdin().lock().lines(){match line{Ok(s)=>{let out=std::panic::catch_unwind(||run(&s)).unwrap_or_else(|_|Err("invalid request".into()));println!("{}",match out{Ok(v)=>json!({"ok":true,"data":v}),Err(e)=>json!({"ok":false,"error":e})});},Err(_)=>break}}}