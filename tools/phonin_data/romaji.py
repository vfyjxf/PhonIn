"""Japanese kana -> Hepburn romaji converter.

Handles basic gojūon, dakuten/handakuten, yōon (きゃ etc.), sokuon (っ ッ),
the small vowels (ぁぃぅぇぉ ヴ etc.), and ん/ン. Output is lowercase, no macrons
(standard for input matching). Mirrors the WanaKana BASIC_ROMAJI convention.
"""

_HIRA = {
    "あ": "a", "い": "i", "う": "u", "え": "e", "お": "o",
    "か": "ka", "き": "ki", "く": "ku", "け": "ke", "こ": "ko",
    "さ": "sa", "し": "shi", "す": "su", "せ": "se", "そ": "so",
    "た": "ta", "ち": "chi", "つ": "tsu", "て": "te", "と": "to",
    "な": "na", "に": "ni", "ぬ": "nu", "ね": "ne", "の": "no",
    "は": "ha", "ひ": "hi", "ふ": "fu", "へ": "he", "ほ": "ho",
    "ま": "ma", "み": "mi", "む": "mu", "め": "me", "も": "mo",
    "や": "ya", "ゆ": "yu", "よ": "yo",
    "ら": "ra", "り": "ri", "る": "ru", "れ": "re", "ろ": "ro",
    "わ": "wa", "を": "wo", "ん": "n",
    "が": "ga", "ぎ": "gi", "ぐ": "gu", "げ": "ge", "ご": "go",
    "ざ": "za", "じ": "ji", "ず": "zu", "ぜ": "ze", "ぞ": "zo",
    "だ": "da", "ぢ": "ji", "づ": "zu", "で": "de", "ど": "do",
    "ば": "ba", "び": "bi", "ぶ": "bu", "べ": "be", "ぼ": "bo",
    "ぱ": "pa", "ぴ": "pi", "ぷ": "pu", "ぺ": "pe", "ぽ": "po",
}
# katakana mirrors hiragana via the +0x60 block offset
_KATA = {chr(ord(k) + 0x60): v for k, v in _HIRA.items()}
_TABLE = {**_HIRA, **_KATA}

_SMALL_Y = {"ゃ": "ya", "ゅ": "yu", "ょ": "yo", "ャ": "ya", "ュ": "yu", "ョ": "yo"}
_SMALL_VOWEL = {"ぁ": "a", "ぃ": "i", "ぅ": "u", "ぇ": "e", "ぉ": "o",
                "ァ": "a", "ィ": "i", "ゥ": "u", "ェ": "e", "ォ": "o"}
_VU = {"ヴ": "vu", "ヵ": "ka", "ヶ": "ke"}


def kana_to_romaji(kana: str) -> str:
    out = []
    chars = list(kana)
    i = 0
    while i < len(chars):
        c = chars[i]
        nxt = chars[i + 1] if i + 1 < len(chars) else ""
        # sokuon (small tsu) doubles the next consonant
        if c in ("っ", "ッ"):
            if nxt and nxt in _TABLE and _TABLE[nxt]:
                out.append(_TABLE[nxt][0])
            i += 1
            continue
        # ヴ and special katakana
        if c in _VU:
            out.append(_VU[c])
            i += 1
            continue
        # yōon: <ki/shi/chi/ni/mi/ri/gi/ji/etc.> + small y-vowel
        if nxt in _SMALL_Y and c in _TABLE:
            base = _TABLE[c]
            # drop the trailing 'i' and append the y-glide
            if base.endswith("i"):
                glue = base[:-1]
                y = _SMALL_Y[nxt]
                # shi/chi/ji drop the 'y' (sha/cha/ja), other glides keep it (kya, nya, rya)
                if glue in ("sh", "ch", "j"):
                    out.append(glue + y[1:])
                else:
                    out.append(glue + y)
                i += 2
                continue
        # small vowel on its own / after a kana -> append vowel (approx.)
        if c in _SMALL_VOWEL:
            out.append(_SMALL_VOWEL[c])
            i += 1
            continue
        # long vowel mark repeats previous vowel
        if c == "ー":
            if out and out[-1]:
                out.append(out[-1][-1])
            i += 1
            continue
        out.append(_TABLE.get(c, c))
        i += 1
    return "".join(out)
