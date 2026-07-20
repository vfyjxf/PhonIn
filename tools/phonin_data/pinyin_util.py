"""Pinyin text utilities: diacritic <-> numeric tone conversion.

The vowel ü is represented as 'v' in numeric/syllable forms (keyboard convention;
'v' never occurs otherwise in pinyin). Unihan kMandarin/kHanyuPinyin use diacritics;
CC-CEDICT and matching engines use numeric tones.
"""

# accented vowel -> (base, tone)
_ACCENT = {
    "ā": ("a", 1), "á": ("a", 2), "ǎ": ("a", 3), "à": ("a", 4),
    "ē": ("e", 1), "é": ("e", 2), "ě": ("e", 3), "è": ("e", 4),
    "ī": ("i", 1), "í": ("i", 2), "ǐ": ("i", 3), "ì": ("i", 4),
    "ō": ("o", 1), "ó": ("o", 2), "ǒ": ("o", 3), "ò": ("o", 4),
    "ū": ("u", 1), "ú": ("u", 2), "ǔ": ("u", 3), "ù": ("u", 4),
    "ǖ": ("v", 1), "ǘ": ("v", 2), "ǚ": ("v", 3), "ǜ": ("v", 4), "ü": ("v", 0),
}

# (base, tone) -> accented vowel
_MARK = {v: k for k, v in _ACCENT.items() if v[1] != 0}
_MARK[("v", 0)] = "ü"


def to_numeric(diacritic: str):
    """Return (numeric, syllable, tone), e.g. 'zhōng' -> ('zhong1', 'zhong', 1)."""
    base = []
    tone = 0
    for ch in diacritic:
        if ch in _ACCENT:
            b, t = _ACCENT[ch]
            base.append(b)
            if tone == 0 and t > 0:
                tone = t
        else:
            base.append(ch)
    syllable = "".join(base)
    numeric = syllable + (str(tone) if tone else "")
    return numeric, syllable, tone


def to_diacritic(numeric: str) -> str:
    """Return the diacritic form, e.g. 'nv3' -> 'nǚ'."""
    tone = 0
    syl = numeric
    if syl and syl[-1] in "12345":
        tone = int(syl[-1])
        syl = syl[:-1]
    if tone == 5:
        tone = 0  # neutral tone carries no mark
    if tone == 0:
        return syl.replace("v", "ü")
    idx = _mark_index(syl)
    if idx < 0:
        return syl.replace("v", "ü")
    base = syl[idx]
    marked = _MARK.get((base, tone), base)
    out = []
    for i, ch in enumerate(syl):
        if i == idx:
            out.append(marked)
        elif ch == "v":
            out.append("ü")
        else:
            out.append(ch)
    return "".join(out)


def _mark_index(syl: str) -> int:
    """Index of the vowel that carries the tone mark: a > e > o, else last vowel."""
    for v in ("a", "e", "o"):
        p = syl.find(v)
        if p >= 0:
            return p
    last = -1
    for i, ch in enumerate(syl):
        if ch in "iuv":
            last = i
    return last
