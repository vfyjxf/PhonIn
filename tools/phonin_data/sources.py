"""Source parsers. Each function fetches (via the cached downloader) and parses one
upstream source into the shared DataSet.
"""

import gzip
import io
import json
import os
import re
import xml.etree.ElementTree as ET
import zipfile

from . import Source, System, Reading, WordEntry
from .download import fetch, cache_path
from .korean import all_syllables, hangul_to_keyboard, syllable_to_keyboard, is_hangul_syllable
from .pinyin_util import to_numeric, to_diacritic
from .romaji import kana_to_romaji


# --------------------------------------------------------------------------- helpers

def _parse_cp(s):
    if not s.startswith("U+"):
        return None
    try:
        return int(s[2:], 16)
    except ValueError:
        return None


def _extract_readings(value):
    """Split a locational Unihan field into its reading strings.

    Handles kMandarin ('zhōng'), kHanyuPinyin ('10028.100:zhōng,zhòng'),
    kTGHZ2013/kXHC1983 ('480.020:zhōng 481.040:zhòng').
    """
    out = []
    for token in value.strip().split():
        if ":" in token:
            token = token.split(":", 1)[1]
        for r in token.split(","):
            r = r.strip()
            if r:
                out.append(r)
    return out


def _parse_pinlu(value):
    """kHanyuPinlu 'zhōng(4875) zhòng(66)' -> {numeric: freq}."""
    result = {}
    for token in value.strip().split():
        m = re.match(r"^(.+?)\((\d+)\)$", token)
        if m:
            numeric, _, _ = to_numeric(m.group(1))
            result[numeric] = int(m.group(2))
    return result


def _cantonese_reading(jp):
    tone = 0
    syl = jp
    if jp and jp[-1] in "123456789":
        tone = int(jp[-1])
        syl = jp[:-1]
    return Reading(System.CANTONESE, jp, jp, tone, syl)


def _mandarin_from_numeric(num):
    tone = 0
    syl = num
    if syl and syl[-1] in "12345":
        tone = int(syl[-1])
        syl = syl[:-1]
    if tone == 5:
        # Neutral tone: normalize to tone 0 / no trailing digit, matching the Unihan and
        # pypinyin convention so char and word tables agree (e.g. CEDICT "de5" -> "de").
        tone = 0
        num = syl
    return Reading(System.MANDARIN, to_diacritic(num), num, tone, syl)


# --------------------------------------------------------------------------- Unihan

def parse_unihan(ds, cache_dir, version):
    url = f"https://www.unicode.org/Public/zipped/{version}/Unihan.zip"
    zpath = fetch(url, f"unihan/{version}/Unihan.zip", cache_dir)
    raw = {}
    with zipfile.ZipFile(zpath) as zf:
        with zf.open("Unihan_Readings.txt") as f:
            text = io.TextIOWrapper(f, encoding="utf-8")
            for line in text:
                if not line or line[0] == "#":
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                cp = _parse_cp(parts[0])
                if cp is None:
                    continue
                raw.setdefault(cp, {})[parts[1]] = parts[2]

    mandarin_fields = ("kMandarin", "kHanyuPinyin", "kTGHZ2013", "kXHC1983")
    for cp, fields in raw.items():
        pinlu = _parse_pinlu(fields.get("kHanyuPinlu", ""))
        touched = False
        for field in mandarin_fields:
            v = fields.get(field)
            if not v:
                continue
            for diac in _extract_readings(v):
                numeric, syl, tone = to_numeric(diac)
                if not syl:
                    continue
                r = Reading(System.MANDARIN, diac, numeric, tone, syl, pinlu.get(numeric, 0))
                ds.char(cp).add_reading(r)
                touched = True
        for jp in fields.get("kCantonese", "").split():
            ds.char(cp).add_reading(_cantonese_reading(jp))
            touched = True
        for hg in _extract_khangul(fields.get("kHangul", "")):
            ds.char(cp).add_reading(Reading(System.KOREAN, hg, hg, 0, hg))
            touched = True
        if touched:
            ds.char(cp).sources.add(Source.UNIHAN)

    print(f"  unihan: {len(raw)} codepoints; "
          f"mandarin={ds.count_chars(System.MANDARIN)}, "
          f"cantonese={ds.count_chars(System.CANTONESE)}, "
          f"korean={ds.count_chars(System.KOREAN)}")


def _extract_khangul(value):
    out = []
    for token in value.strip().split():
        if ":" in token:
            token = token.split(":", 1)[0]
        if token:
            out.append(token)
    return out


# --------------------------------------------------------------------------- mozillazg (cross-check)

def parse_mozillazg(cache_dir, commit):
    base = f"https://raw.githubusercontent.com/mozillazg/pinyin-data/{commit}/"
    path = fetch(base + "pinyin.txt", f"mozillazg/{commit}/pinyin.txt", cache_dir)
    result = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line or line[0] == "#":
                continue
            if ":" not in line:
                continue
            left, rest = line.split(":", 1)
            left = left.strip()
            if not left.startswith("U+"):
                continue
            cp = _parse_cp(left)
            if cp is None:
                continue
            hashpos = rest.find("#")
            readings = (rest[:hashpos] if hashpos >= 0 else rest).strip()
            s = set()
            for d in readings.split(","):
                d = d.strip()
                if d:
                    s.add(to_numeric(d)[0])
            if s:
                result[cp] = s
    print(f"  mozillazg: {len(result)} codepoints")
    return result


# --------------------------------------------------------------------------- CC-CEDICT (Mandarin words)

def parse_cedict(ds, cache_dir):
    url = "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.zip"
    zpath = fetch(url, "cedict/cedict.zip", cache_dir)
    count = 0
    with zipfile.ZipFile(zpath) as zf:
        name = next((n for n in zf.namelist() if n.lower().endswith((".u8", ".txt"))), None)
        if name is None:
            return
        with zf.open(name) as f:
            text = io.TextIOWrapper(f, encoding="utf-8")
            for line in text:
                if not line or line[0] == "#":
                    continue
                bo = line.find(" [")
                bc = line.find("] /", bo)
                if bo < 0 or bc < 0:
                    continue
                head = line[:bo]
                sp = head.find(" ")
                if sp < 0:
                    continue
                trad = head[:sp]
                simp = head[sp + 1:]
                pinyin = line[bo + 2:bc]
                readings = []
                for tok in pinyin.split(" "):
                    num = tok.strip().lower().replace("u:", "v").replace("v:", "v")
                    if num:
                        readings.append(_mandarin_from_numeric(num))
                if not readings:
                    continue
                cps = [ord(c) for c in simp]
                ds.words.append(WordEntry(cps, simp, System.MANDARIN, readings, Source.CEDICT))
                count += 1
    print(f"  cedict: {count} Mandarin words")


# --------------------------------------------------------------------------- words.hk

def parse_wordshk_chars(ds, cache_dir):
    """Char-level Cantonese from AlienKevin/wordshk-tools (MIT), supplementing Unihan."""
    url = "https://raw.githubusercontent.com/AlienKevin/wordshk-tools/main/data/char_jyutpings/charlist_processed.json"
    try:
        path = fetch(url, "wordshk-tools/charlist_processed.json", cache_dir)
    except Exception as e:  # noqa: BLE001
        print(f"  wordshk-tools chars: unavailable ({e}); skipping")
        return
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:  # noqa: BLE001
        print(f"  wordshk-tools chars: failed to parse JSON ({e}); skipping")
        return
    count = 0
    items = data.items() if isinstance(data, dict) else (
        ((it.get("char") or it.get("entry"), it) for it in data) if isinstance(data, list) else []
    )
    for ch, info in items:
        if not ch or len(ch) != 1:
            continue
        # wordshk-tools format is {char: {jyutping: frequency}}
        if isinstance(info, dict) and info and not any(k in info for k in ("jyutping", "jyut", "reading")):
            jps = list(info.keys())
            freq_map = {k: v for k, v in info.items() if isinstance(v, int)}
        else:
            jps = _extract_jyutping(info)
            freq_map = {}
        for jp in jps:
            jp = jp.strip()
            if not jp:
                continue
            r = _cantonese_reading(jp)
            if jp in freq_map:
                r.freq = freq_map[jp]
            ds.char(ord(ch)).add_reading(r)
            ds.char(ord(ch)).sources.add(Source.WORDSHK_CHARS)
            count += 1
    print(f"  wordshk-tools chars: {count} readings added")


def _extract_jyutping(info):
    if isinstance(info, str):
        return [info]
    if isinstance(info, dict):
        for key in ("jyutping", "jyut", "reading", "readings"):
            v = info.get(key)
            if isinstance(v, str):
                return v.replace(",", " ").split()
            if isinstance(v, list):
                out = []
                for x in v:
                    if isinstance(x, str):
                        out.extend(x.replace(",", " ").split())
                    elif isinstance(x, dict):
                        out.extend(_extract_jyutping(x))
                return out
        # last resort: first list value
        for v in info.values():
            if isinstance(v, list):
                return [str(x) for x in v]
    return []


def parse_wordshk_words(ds, cache_dir):
    """Word-level Cantonese from a user-supplied words.hk TSV (drop-in)."""
    for rel in ("wordshk/words.hk.tsv", "wordshk/words.hk.csv", "wordshk/wordshk.tsv"):
        path = cache_path(rel, cache_dir)
        if os.path.exists(path) and os.path.getsize(path) > 0:
            break
    else:
        print("  words.hk: no user-supplied data under <cache>/wordshk/ "
              "(request CSV via https://words.hk/faiman/request_data/). Skipping word-level "
              "Cantonese. Char-level Cantonese remains available.")
        return
    count = 0
    first = True
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            parts = line.split("\t")
            if first:
                first = False
                if parts[0].lower().startswith("headword") or parts[0].lower() == "entry":
                    continue
            if len(parts) < 2:
                continue
            head, jp = parts[0].strip(), parts[1].strip()
            if not head or not jp:
                continue
            readings = [_cantonese_reading(s) for s in jp.split() if s]
            if not readings:
                continue
            ds.words.append(WordEntry([ord(c) for c in head], head, System.CANTONESE, readings, Source.WORDSHK))
            count += 1
    print(f"  words.hk: {count} Cantonese words (from {os.path.basename(path)})")


# --------------------------------------------------------------------------- KANJIDIC2 (Japanese char)

def parse_kanjidic2(ds, cache_dir):
    url = "https://www.edrdg.org/kanjidic/kanjidic2.xml.gz"
    path = fetch(url, "kanjidic2/kanjidic2.xml.gz", cache_dir)
    count = 0
    with gzip.open(path, "rb") as gf:
        for _ev, el in ET.iterparse(gf, events=("end",)):
            if el.tag != "character":
                continue
            lit = el.findtext("literal")
            if not lit:
                el.clear()
                continue
            cp = ord(lit[0])
            ce = ds.char(cp)
            ce.sources.add(Source.KANJIDIC)
            rm = el.find("reading_meaning")
            if rm is not None:
                for rmgroup in rm.findall("rmgroup"):
                    for r in rmgroup.findall("reading"):
                        rtype = r.get("r_type")
                        if rtype in ("ja_on", "ja_kun", "nanori"):
                            kana = (r.text or "").replace(".", "").replace("-", "")
                            if not kana:
                                continue
                            romaji = kana_to_romaji(kana)
                            ce.add_reading(Reading(System.JAPANESE, kana, romaji, 0, romaji))
                            count += 1
            el.clear()
    print(f"  kanjidic2: {ds.count_chars(System.JAPANESE)} chars, {count} readings")


# --------------------------------------------------------------------------- JMDict (Japanese words)

def parse_jmdict(ds, cache_dir):
    url = "https://www.edrdg.org/pub/Nihongo/JMdict_e.gz"
    try:
        path = fetch(url, "jmdict/JMdict_e.gz", cache_dir)
    except Exception as e:  # noqa: BLE001
        print(f"  jmdict: unavailable ({e}); skipping Japanese word-level")
        return
    count = 0
    keb = reb = None
    with gzip.open(path, "rb") as gf:
        for _ev, el in ET.iterparse(gf, events=("end",)):
            if el.tag == "k_ele":
                t = el.findtext("keb")
                if t and keb is None:
                    keb = t
                el.clear()
            elif el.tag == "r_ele":
                t = el.findtext("reb")
                if t and reb is None:
                    reb = t
                el.clear()
            elif el.tag == "entry":
                if keb and reb:
                    romaji = kana_to_romaji(reb.replace(".", ""))
                    readings = [Reading(System.JAPANESE, reb, romaji, 0, romaji)]
                    ds.words.append(WordEntry([ord(c) for c in keb], keb,
                                              System.JAPANESE, readings, Source.JMDICT))
                    count += 1
                keb = reb = None
                el.clear()
    print(f"  jmdict: {count} Japanese words")


# --------------------------------------------------------------------------- Korean (algorithmic Hangul)

def add_korean(ds):
    """Add Korean character-level readings:
    1. All 11,172 precomposed Hangul syllables (algorithmic)
    2. Hanja→Hangul from hanja_char.csv (27,848 mappings)
    3. Hanja→Hangul from hanjadict PyPI package (53,458 chars, 훈음)
    All Hangul is converted to 2-bulsik keyboard key sequences.
    """
    import os
    count = 0

    # 1. Algorithmic Hangul syllables
    for cp, hangul, norm in all_syllables():
        ds.char(cp).add_reading(Reading(System.KOREAN, hangul, norm, 0, norm))
        ds.char(cp).sources.add(Source.HANGUL)
        count += 1

    sources_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                               "korean_sources")

    # 2. hanja_char.csv: Hanja → Hangul character mapping
    hanja_char_path = os.path.join(sources_dir, "hanja_char.csv")
    hanja_char_count = 0
    if os.path.exists(hanja_char_path):
        with open(hanja_char_path, encoding="utf-8-sig") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split(",")
                if len(parts) < 2:
                    continue
                hanja = parts[0].strip()
                hangul = parts[1].strip()
                if not hanja or not hangul:
                    continue
                cp = ord(hanja)
                ds.char(cp).add_reading(Reading(System.KOREAN, hangul, hangul, 0, hangul))
                ds.char(cp).sources.add(Source.HANGUL)
                hanja_char_count += 1
                count += 1

    # 3. hanjadict PyPI package: 53,458 Hanja chars with 훈음
    hanjadict_count = 0
    try:
        import hanjadict as _hanjadict
        for char, hun_eum in _hanjadict.table_data.items():
            cp = ord(char)
            # 훈음 format: "훈 음" — last token is the Sino-Korean pronunciation
            hun_eum_parts = hun_eum.strip().split()
            if len(hun_eum_parts) < 2:
                continue
            sound = hun_eum_parts[-1].strip()
            if not sound or not all(is_hangul_syllable(ord(ch)) for ch in sound):
                continue
            ds.char(cp).add_reading(Reading(System.KOREAN, sound, sound, 0, sound))
            ds.char(cp).sources.add(Source.HANGUL)
            hanjadict_count += 1
            count += 1
    except ImportError:
        print("  warning: hanjadict package not installed; skipping Hanja char data")

    print(f"  korean (hangul): {count} syllables/chars "
          f"(algorithmic=11172, hanja_char.csv={hanja_char_count}, hanjadict={hanjadict_count})")


def add_korean_words(ds):
    """Add Korean word-level entries from hanja_word.csv and the 50k frequency word list."""
    import os
    sources_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                               "korean_sources")
    word_count = 0

    # 1. hanja_word.csv: irregular Hanja word → Hangul word mappings
    hanja_word_path = os.path.join(sources_dir, "hanja_word.csv")
    if os.path.exists(hanja_word_path):
        with open(hanja_word_path, encoding="utf-8-sig") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split(",")
                if len(parts) < 2:
                    continue
                hanja = parts[0].strip()
                hangul = parts[1].strip()
                if not hanja or not hangul:
                    continue
                cps = [ord(ch) for ch in hanja]
                readings = [Reading(System.KOREAN, hangul, hangul, 0, hangul)]
                ds.words.append(WordEntry(cps, hangul, System.KOREAN, readings, Source.HANGUL))
                word_count += 1

    # 2. ko_50k.txt: 50k most frequent Korean words (pure Hangul)
    freq_path = os.path.join(sources_dir, "ko_50k.txt")
    if os.path.exists(freq_path):
        with open(freq_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split()
                if len(parts) < 2:
                    continue
                word = parts[0].strip()
                if not word or len(word) < 2:
                    continue
                if not all(is_hangul_syllable(ord(ch)) for ch in word):
                    continue
                cps = [ord(ch) for ch in word]
                readings = [Reading(System.KOREAN, word, word, 0, word)]
                ds.words.append(WordEntry(cps, word, System.KOREAN, readings, Source.HANGUL))
                word_count += 1

    print(f"  korean (words): {word_count} entries")
