use regex::Regex;
use serde::Serialize;
use std::{collections::BTreeMap, sync::OnceLock};

#[derive(Debug, Serialize)]
pub struct Line { pub at: Option<i64>, pub text: String, pub translation: String }

fn parsed(input: &str) -> (BTreeMap<i64,String>, Vec<String>) {
    static STAMP: OnceLock<Regex> = OnceLock::new();
    let re = STAMP.get_or_init(|| Regex::new(r"^\[(\d{1,4}):(\d{1,2})(?:[.:](\d{1,3}))?\]").unwrap());
    let mut timed = BTreeMap::new();
    let mut plain = vec![];
    let offset = input.lines().find_map(|l| l.trim().strip_prefix("[offset:")?.strip_suffix(']')?.trim().parse::<i64>().ok()).unwrap_or(0);
    for raw in input.trim_start_matches('\u{feff}').lines() {
        let mut rest = raw.trim();
        let mut times = vec![];
        while let Some(c) = re.captures(rest) {
            let min = c[1].parse::<i64>().unwrap_or(0);
            let sec = c[2].parse::<i64>().unwrap_or(0);
            let frac = c.get(3).map(|v| v.as_str().parse::<i64>().unwrap_or(0) * 10_i64.pow((3-v.len()) as u32)).unwrap_or(0);
            times.push((min*60_000+sec*1000+frac).saturating_add(offset).max(0));
            rest = &rest[c[0].len()..];
        }
        let line = rest.trim();
        if line.is_empty() { continue; }
        if times.is_empty() {
            if !(line.starts_with('[') && line.ends_with(']')) { plain.push(line.into()); }
        } else {
            for t in times { timed.entry(t).or_insert_with(|| line.into()); }
        }
    }
    (timed,plain)
}

pub fn lines(original: &str, translation: &str) -> Vec<Line> {
    let (timed,plain) = parsed(original);
    if timed.is_empty() { return plain.into_iter().map(|text| Line { at:None,text,translation:String::new() }).collect(); }
    let (translations,_) = parsed(translation);
    timed.into_iter().map(|(at,text)| Line {at:Some(at),text,translation:translations.get(&at).cloned().unwrap_or_default()}).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lrc_keeps_multiple_timestamps_sorted_and_matches_translation() {
        let l = lines("[offset:-50]\n[00:10.05][00:02.1]同频\n[00:01.001]开始", "[00:02.050]In sync");
        assert_eq!(l.iter().map(|l|l.at).collect::<Vec<_>>(), [Some(951),Some(2050),Some(10000)]);
        assert_eq!(l[1].translation,"In sync");
    }

    #[test]
    fn static_text_has_no_fabricated_timestamps() {
        let l = lines("[ti:曲名]\n第一句\n第二句", "");
        assert_eq!(l.len(),2);
        assert!(l.iter().all(|l| l.at.is_none()));
    }
}