"""Derivation passes: Zhuyin (Bopomofo) and Mandarin heteronym supplement via pypinyin."""

from pypinyin import pinyin as _pinyin, Style

from . import Reading, Source, System
from .pinyin_util import to_numeric

_ZY_TONE = {"ˊ": 2, "ˇ": 3, "ˋ": 4, "˙": 0}


def _strip_zhuyin_tone(z):
    if not z:
        return z, 0
    if z[-1] in _ZY_TONE:
        return z[:-1], _ZY_TONE[z[-1]]
    if z[0] == "˙":
        return z[1:], 0
    return z, 1  # no mark = first tone


def derive_zhuyin(ds):
    """Add Zhuyin (Bopomofo) readings for every character with a Mandarin reading."""
    n_chars = 0
    n_readings = 0
    for cp, ce in ds.chars.items():
        if System.MANDARIN not in ce.readings:
            continue
        ch = chr(cp)
        try:
            result = _pinyin(ch, style=Style.BOPOMOFO, heteronym=True)
        except Exception:  # noqa: BLE001
            continue
        if not result or not result[0]:
            continue
        added = False
        for variants in result:
            for z in variants:
                if not z:
                    continue
                syl, tone = _strip_zhuyin_tone(z)
                ce.add_reading(Reading(System.ZHUYIN, z, z, tone, syl))
                added = True
                n_readings += 1
        if added:
            ce.sources.add(Source.PYPINYIN)
            n_chars += 1
    print(f"  zhuyin: derived for {n_chars} chars ({n_readings} readings) via pypinyin")


def supplement_mandarin(ds):
    """Add any Mandarin heteronym readings pypinyin knows that Unihan lacked."""
    added = 0
    for cp, ce in ds.chars.items():
        if System.MANDARIN not in ce.readings:
            continue
        ch = chr(cp)
        try:
            result = _pinyin(ch, heteronym=True)
        except Exception:  # noqa: BLE001
            continue
        before = len(ce.readings[System.MANDARIN])
        for variants in result:
            for d in variants:
                if not d:
                    continue
                numeric, syl, tone = to_numeric(d)
                if not syl:
                    continue
                ce.add_reading(Reading(System.MANDARIN, d, numeric, tone, syl))
        after = len(ce.readings[System.MANDARIN])
        if after > before:
            ce.sources.add(Source.PYPINYIN)
            added += after - before
    print(f"  pypinyin supplement: +{added} Mandarin readings")
