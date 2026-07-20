"""Generate self-verifying matching test cases (JSONL).

Each positive case records an input string and the COMPLETE correct set of codepoints
that should match it under the stated variant (capped for storage; `expectedTotal`
records the true size). Negatives carry expected=[] and are guaranteed not to occur in
the input universe. All transforms are deterministic, so the dataset is reproducible.
"""

import json
import os
import random

from . import System

EXPECT_CAP = 24


def _hex(cp):
    return "U+" + format(cp, "X")


def _key_str(k):
    if isinstance(k, tuple):
        return ",".join(_hex(c) for c in k)
    return _hex(k)


def _initial(syll):
    # The 简拼 initial of a toneless Mandarin syllable, mirroring Java's PinyinInitials.length
    # exactly (keep in lockstep): zh/ch/sh -> 2-char initial; any other consonant onset -> 1-char;
    # zero-initial (vowel-led: an/ai/ou/er/...) -> the full syllable. Keeps zh/z distinct (low
    # collision); merging them is the fuzzy layer's job. Used by abbrev + the mix transforms.
    if len(syll) >= 2 and syll[:2] in ("zh", "ch", "sh"):
        return syll[:2]
    if syll and syll[0] in "bpmfdtnlgkhjqxrzcsyw":
        return syll[:1]
    return syll


def _cap(keys, source_key=None):
    keys = sorted(set(keys))
    if source_key in keys:
        keys.remove(source_key)
        keys = [source_key] + keys
    return [_key_str(k) for k in keys[:EXPECT_CAP]], len(keys)


def _write(out_dir, rel, cases):
    path = os.path.join(out_dir, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    n = 0
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for c in cases:
            f.write(json.dumps(c, ensure_ascii=False, separators=(",", ":")) + "\n")
            n += 1
    print(f"  wrote {rel}: {n} cases")
    return rel, n


def _build_index(entries, transform):
    idx = {}
    for key, readings in entries:
        for inp in transform(readings):
            idx.setdefault(inp, []).append(key)
    return idx


# --------------------------------------------------------------------------- char cases

def gen_char_cases(ds, out_dir, system, subdir, variants):
    """variants: subset of ['FULL','PARTIAL','TONED']. FULL uses syllable, TONED uses
    normalized, PARTIAL uses a 2-char prefix of the syllable.

    For Korean, the syllable is Hangul (the canonical form). The test input is the 2-bulsik
    keyboard key sequence, derived by applying syllable_to_keyboard() to the Hangul.
    """
    from .korean import syllable_to_keyboard, is_hangul_syllable
    is_korean = system == System.KOREAN

    def _encode(s):
        """Encode a syllable for test input. Korean: Hangul→2-bulsik keys. Others: pass through."""
        if not is_korean or not s:
            return s
        return "".join(
            syllable_to_keyboard(ord(ch)) if is_hangul_syllable(ord(ch)) else ch
            for ch in s)

    chars = [(cp, ce) for cp, ce in ds.chars.items() if system in ce.readings]
    files = []
    for variant in variants:
        if variant == "FULL":
            transform = lambda rs: {_encode(r.syllable) for r in rs}
            pick = lambda rs: [_encode(r.syllable) for r in rs]
        elif variant == "TONED":
            transform = lambda rs: {_encode(r.normalized) for r in rs}
            pick = lambda rs: [_encode(r.normalized) for r in rs]
        else:  # PARTIAL
            transform = lambda rs: {
                (lambda e: e[:2] if len(e) >= 2 else e)(_encode(r.syllable))
                for r in rs}
            pick = lambda rs: [
                (lambda e: e[:2] if len(e) >= 2 else e)(_encode(r.syllable))
                for r in rs]
        idx = _build_index(((cp, ce.readings[system]) for cp, ce in chars), transform)
        cases = []
        cid = 0
        for cp, ce in chars:
            for inp in pick(ce.readings[system]):
                bucket = idx.get(inp, [])
                exp, total = _cap(bucket, cp)
                kb_name = "BULSIK2" if is_korean else ("ZHUYIN" if system == System.ZHUYIN else "QUANPIN")
                cases.append({
                    "id": f"{system.value}-CHAR-{variant}-{cid:07d}",
                    "system": system.value, "level": "CHAR", "mode": "CONTAINS",
                    "keyboard": kb_name,
                    "input": inp, "variant": variant, "fuzzies": [],
                    "expected": exp, "expectedTotal": total,
                    "sourceEntry": _hex(cp), "polarity": "POSITIVE",
                })
                cid += 1
        files.append(_write(out_dir, f"generated/{subdir}/cases-char-{variant.lower()}.jsonl", cases))
    return files


# --------------------------------------------------------------------------- Mandarin word cases

def _word_reproducible(ds, w):
    """True iff the engine's per-char matching can reproduce this word's input: one reading per
    codepoint, and each codepoint actually carries that reading's toneless syllable. Drops
    erhua contractions (沿儿 -> "yanr", N chars but <N syllables) and irregular jukujikun
    (山袴 -> "sanpaku", where 袴 lacks "paku") that no per-char engine can match."""
    system = w.system
    if len(w.codepoints) != len(w.readings):
        return False
    for cp, r in zip(w.codepoints, w.readings):
        ce = ds.chars.get(cp)
        if not ce or system not in ce.readings:
            return False
        if r.syllable not in {cr.syllable for cr in ce.readings[system]}:
            return False
    return True


def _word_toned_reproducible(ds, w):
    """Stricter than _word_reproducible: each syllable's tone-bearing normalized form must be
    one of the char's readings' normalized forms (so STRICT tone matching can reproduce it)."""
    system = w.system
    for cp, r in zip(w.codepoints, w.readings):
        ce = ds.chars.get(cp)
        norms = {cr.normalized for cr in ce.readings[system]}
        if r.normalized not in norms:
            return False
    return True


def gen_mandarin_word_cases(ds, out_dir, shuangpin_schemes=None):
    words = [w for w in ds.words if w.system == System.MANDARIN and _word_reproducible(ds, w)]
    files = []

    def full(rs):
        return ["".join(r.syllable for r in rs)]

    def abbrev(rs):
        return ["".join(_initial(r.syllable) for r in rs)]
    def partial(rs):
        return [rs[0].syllable] if rs else []
    def toned(rs):
        return ["".join(r.normalized for r in rs)]

    for name, fn, variant in (("full", full, "FULL"),
                              ("abbrev", abbrev, "ABBREV"),
                              ("partial", partial, "PARTIAL"),
                              ("toned", toned, "TONED")):
        # The TONED variant matches the normalized (tone-bearing) form in STRICT mode, so it
        # additionally requires every syllable's tone to align with a char reading's tone --
        # e.g. drop 量 in 经济力量 where CEDICT marks it neutral ("liang") but the char only has
        # toned forms ("liang4"/"liang2"). The other variants match toneless and need no such check.
        variant_words = words if variant != "TONED" else [w for w in words if _word_toned_reproducible(ds, w)]
        idx = _build_index(((tuple(w.codepoints), w.readings) for w in variant_words), fn)
        cases = []
        cid = 0
        for w in variant_words:
            for inp in fn(w.readings):
                bucket = idx.get(inp, [])
                exp, total = _cap(bucket, tuple(w.codepoints))
                # represent word membership via codepoint tuple key
                cases.append({
                    "id": f"MANDARIN-WORD-{variant}-{cid:07d}",
                    "system": "MANDARIN", "level": "WORD", "mode": "EXACT" if variant != "PARTIAL" else "BEGINS",
                    "keyboard": "QUANPIN", "input": inp, "variant": variant, "fuzzies": [],
                    "expected": exp, "expectedTotal": total,
                    "sourceEntry": ",".join(_hex(c) for c in w.codepoints),
                    "sourceText": w.text, "polarity": "POSITIVE",
                })
                cid += 1
        files.append(_write(out_dir, f"generated/mandarin/cases-word-{name}.jsonl", cases))
    return files


# --------------------------------------------------------------------------- Japanese word cases (JMDict)

def gen_japanese_word_cases(ds, out_dir):
    # Per-char reproducibility: see _word_reproducible.
    words = [w for w in ds.words if w.system == System.JAPANESE and _word_reproducible(ds, w)]
    fn = lambda rs: ["".join(r.normalized for r in rs)]  # romaji concat
    idx = _build_index(((tuple(w.codepoints), w.readings) for w in words), fn)
    cases = []
    cid = 0
    for w in words:
        for inp in fn(w.readings):
            bucket = idx.get(inp, [])
            exp, total = _cap(bucket, tuple(w.codepoints))
            cases.append({
                "id": f"JAPANESE-WORD-FULL-{cid:07d}",
                "system": "JAPANESE", "level": "WORD", "mode": "EXACT",
                "keyboard": "ROMAJI", "input": inp, "variant": "FULL", "fuzzies": [],
                "expected": exp, "expectedTotal": total,
                "sourceEntry": ",".join(_hex(c) for c in w.codepoints),
                "sourceText": w.text, "polarity": "POSITIVE",
            })
            cid += 1
    return [_write(out_dir, "generated/japanese/cases-word-full.jsonl", cases)]


# --------------------------------------------------------------------------- Korean word cases

def gen_korean_word_cases(ds, out_dir):
    """Korean word-level cases: input = concatenated 2-bulsik keyboard key sequences.

    The stored normalized form is Hangul (canonical). The test input is derived by applying
    syllable_to_keyboard() to each Hangul syllable in the word.
    """
    from .korean import syllable_to_keyboard, is_hangul_syllable

    def _encode_word(hangul):
        return "".join(
            syllable_to_keyboard(ord(ch)) if is_hangul_syllable(ord(ch)) else ch
            for ch in hangul)

    words = [w for w in ds.words if w.system == System.KOREAN]
    fn = lambda rs: [_encode_word(r.normalized) for r in rs]  # Hangul→2-bulsik keys
    idx = _build_index(((tuple(w.codepoints), w.readings) for w in words), fn)
    cases = []
    cid = 0
    for w in words:
        for inp in fn(w.readings):
            bucket = idx.get(inp, [])
            exp, total = _cap(bucket, tuple(w.codepoints))
            cases.append({
                "id": f"KOREAN-WORD-FULL-{cid:07d}",
                "system": "KOREAN", "level": "WORD", "mode": "EXACT",
                "keyboard": "BULSIK2", "input": inp, "variant": "FULL", "fuzzies": [],
                "expected": exp, "expectedTotal": total,
                "sourceEntry": ",".join(_hex(c) for c in w.codepoints),
                "sourceText": w.text, "polarity": "POSITIVE",
            })
            cid += 1
    return [_write(out_dir, "generated/korean/cases-word-full.jsonl", cases)]


# --------------------------------------------------------------------------- shuangpin cases

def gen_shuangpin_cases(ds, out_dir):
    from . import shuangpin as sp
    mandarin_chars = [(cp, ce) for cp, ce in ds.chars.items() if System.MANDARIN in ce.readings]
    mandarin_words = [w for w in ds.words if w.system == System.MANDARIN and _word_reproducible(ds, w)]
    files = []
    for scheme in sp.SCHEMES:
        kb = scheme.upper()
        # char-level
        idx_c = {}
        for cp, ce in mandarin_chars:
            for r in ce.readings[System.MANDARIN]:
                code = sp.encode(r.syllable, scheme)
                if code:
                    idx_c.setdefault(code, []).append(cp)
        cases_c = []
        cid = 0
        for cp, ce in mandarin_chars:
            for r in ce.readings[System.MANDARIN][:2]:
                code = sp.encode(r.syllable, scheme)
                if not code:
                    continue
                exp, total = _cap(idx_c.get(code, []), cp)
                cases_c.append({
                    "id": f"MANDARIN-CHAR-SHUANGPIN-{kb}-{cid:07d}",
                    "system": "MANDARIN", "level": "CHAR", "mode": "CONTAINS",
                    "keyboard": kb, "input": code, "variant": "SHUANGPIN_" + kb,
                    "fuzzies": [], "expected": exp, "expectedTotal": total,
                    "sourceEntry": _hex(cp), "polarity": "POSITIVE",
                })
                cid += 1
        files.append(_write(out_dir, f"generated/mandarin/cases-char-shuangpin-{scheme}.jsonl", cases_c))

        # word-level
        def wcode(w):
            parts = [sp.encode(r.syllable, scheme) for r in w.readings]
            if not parts or any(p is None for p in parts):
                return None
            return "".join(parts)

        idx_w = {}
        for w in mandarin_words:
            c = wcode(w)
            if c:
                idx_w.setdefault(c, []).append(tuple(w.codepoints))
        cases_w = []
        cid = 0
        for w in mandarin_words:
            c = wcode(w)
            if not c:
                continue
            exp, total = _cap(idx_w.get(c, []), tuple(w.codepoints))
            cases_w.append({
                "id": f"MANDARIN-WORD-SHUANGPIN-{kb}-{cid:07d}",
                "system": "MANDARIN", "level": "WORD", "mode": "EXACT",
                "keyboard": kb, "input": c, "variant": "SHUANGPIN_" + kb,
                "fuzzies": [], "expected": exp, "expectedTotal": total,
                "sourceEntry": ",".join(_hex(x) for x in w.codepoints),
                "sourceText": w.text, "polarity": "POSITIVE",
            })
            cid += 1
        files.append(_write(out_dir, f"generated/mandarin/cases-word-shuangpin-{scheme}.jsonl", cases_w))
    return files


# --------------------------------------------------------------------------- cross-system

def gen_mandarin_zhuyin_equiv(ds, out_dir):
    """For each char with both Mandarin and Zhuyin: the codepoint appears in both
    FULL indices for the corresponding inputs."""
    chars = [(cp, ce) for cp, ce in ds.chars.items()
             if System.MANDARIN in ce.readings and System.ZHUYIN in ce.readings]
    cases = []
    cid = 0
    for cp, ce in chars:
        for mr in ce.readings[System.MANDARIN][:2]:
            cases.append({
                "id": f"CROSS-MZ-{cid:07d}",
                "system": "MANDARIN+ZHUYIN", "level": "CHAR", "mode": "CONTAINS",
                "keyboard": "QUANPIN", "input": mr.syllable, "variant": "FULL_TONELESS",
                "fuzzies": [], "expected": [_hex(cp)], "expectedTotal": 1,
                "sourceEntry": _hex(cp), "polarity": "POSITIVE",
            })
            cid += 1
    return [_write(out_dir, "generated/cross-system/cases-mandarin-zhuyin-equiv.jsonl", cases)]


# --------------------------------------------------------------------------- fuzzy char cases

def gen_fuzzy_char_cases(ds, out_dir):
    """For each fuzzy rule (see fuzzy.py), for each char with an affected reading, emit a
    CONTAINS case whose `input` is a variant surface and whose `expected` set is the full
    bucket of chars whose canonical-or-fuzzy surface equals that variant. `fuzzies:[name]`
    records the rule. The Java FuzzyRules mirror fuzzy.py exactly, so these cases are the
    cross-implementation regression net."""
    from . import fuzzy as fz
    files = []
    by_system = {}
    for rule in fz.RULES:
        by_system.setdefault(rule.system, []).append(rule)
    for system, rules in by_system.items():
        chars = [(cp, ce) for cp, ce in ds.chars.items() if system in ce.readings]
        subdir = system.value.lower()
        kb = "QUANPIN" if system != System.ZHUYIN else "ZHUYIN"
        for rule in rules:
            # index over ALL readings: surface -> [codepoint, ...]
            idx = {}
            for cp, ce in chars:
                for r in ce.readings[system]:
                    base = r.syllable
                    if not base:
                        continue
                    for surf in rule.surfaces(base):
                        idx.setdefault(surf, []).append(cp)
            cases = []
            cid = 0
            for cp, ce in chars:
                emitted = set()
                for r in ce.readings[system][:2]:
                    base = r.syllable
                    if not base:
                        continue
                    for var in rule.variants(base):
                        if not var or var == base or var in emitted:
                            continue
                        bucket = idx.get(var, [])
                        if cp not in bucket:
                            continue
                        exp, total = _cap(bucket, cp)
                        emitted.add(var)
                        cases.append({
                            "id": f"{system.value}-CHAR-FUZZY-{rule.name}-{cid:07d}",
                            "system": system.value, "level": "CHAR", "mode": "CONTAINS",
                            "keyboard": kb, "input": var, "variant": "FUZZY",
                            "fuzzies": [rule.name], "expected": exp, "expectedTotal": total,
                            "sourceEntry": _hex(cp), "polarity": "POSITIVE",
                        })
                        cid += 1
            files.append(_write(out_dir, f"generated/{subdir}/cases-char-fuzzy-{rule.name.lower()}.jsonl", cases))
    return files


# --------------------------------------------------------------------------- negatives

_ALPHABETS = {
    System.MANDARIN: "abcdefghijklmnopqrstuvwxyz",
    System.CANTONESE: "abcdefghijklmnopqrstuvwxyz",
    System.ZHUYIN: "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ",
    System.JAPANESE: "abcdefghijklmnopqrstuvwxyz",
    System.KOREAN: "abcdefghijklmnopqrstuvwxyzQWERTOP",  # 2-bulsik: a-z + shifted keys
}


def gen_negatives(ds, out_dir, rng, count_per=20000):
    files = []
    # gather the universe of char-level FULL inputs per system
    for system, alphabet in _ALPHABETS.items():
        chars = [(cp, ce) for cp, ce in ds.chars.items() if system in ce.readings]
        # The matcher is prefix-based in CONTAINS mode (a query matches a single char iff it
        # is a prefix of one of the char's surfaces). So a sound negative must avoid being a
        # prefix of ANY char's syllable or normalized form -- not just a full syllable.
        #
        # For Korean, the stored normalized/syllable is Hangul, but the actual user-facing surface
        # is the 2-bulsik key sequence, so the universe must be built from encoded keys.
        from .korean import syllable_to_keyboard, is_hangul_syllable
        is_korean = system == System.KOREAN
        universe = set()
        for cp, ce in chars:
            for r in ce.readings[system]:
                for s in (r.syllable, r.normalized):
                    if not s:
                        continue
                    if is_korean:
                        # Encode each Hangul syllable to 2-bulsik keys and take prefixes
                        encoded = "".join(
                            syllable_to_keyboard(ord(ch)) if is_hangul_syllable(ord(ch)) else ch
                            for ch in s)
                        for k in range(1, len(encoded) + 1):
                            universe.add(encoded[:k])
                    else:
                        for k in range(1, len(s) + 1):
                            universe.add(s[:k])
        cases = []
        cid = 0
        tries = 0
        maxlen = 4 if system != System.ZHUYIN else 3
        while cid < count_per and tries < count_per * 40:
            tries += 1
            length = rng.randint(2, maxlen)
            inp = "".join(rng.choice(alphabet) for _ in range(length))
            if inp in universe:
                continue
            kb_name = "BULSIK2" if is_korean else ("ZHUYIN" if system == System.ZHUYIN else "QUANPIN")
            cases.append({
                "id": f"{system.value}-NEG-{cid:07d}",
                "system": system.value, "level": "CHAR", "mode": "CONTAINS",
                "keyboard": kb_name,
                "input": inp, "variant": "FULL", "fuzzies": [],
                "expected": [], "expectedTotal": 0,
                "sourceEntry": "U+AC00" if is_korean else None,
                "polarity": "NEGATIVE",
            })
            cid += 1
        files.append(_write(out_dir, f"generated/{system.value.lower()}/cases-negative.jsonl", cases))
    return files


# --------------------------------------------------------------------------- fixed regression scenarios
# Committed under regression/ (NOT gitignored like generated/): curated scenario coverage for
# 简拼/full mixes, CJK+latin mixed text, and long text -- gaps the main corpus doesn't pin. All are
# POSITIVE recall cases (the test asserts the source matches the input under the stated Options);
# `expected` carries only the source. `variant` selects Options ("ABBREV" -> 简拼 initials,
# "FULL" -> plain quanpin); the id prefix names the scenario.

_JIANPIN_MIX_SAMPLE = 20000
_LONG_MIN_CODEPOINTS = 5
_MIXED_WORD_SAMPLE = 400
_FUZZY_JIANPIN_SAMPLE = 10000
_LATIN_TOKENS = ["a", "x", "ab", "xy", "test", "item", "abc", "config"]


def _mix_first_full(rs):
    # First syllable full, rest as 简拼 initials: 中国 -> zhongg, 安山岩 -> anshy.
    if not rs:
        return []
    return [rs[0].syllable + "".join(_initial(r.syllable) for r in rs[1:])]


def _mix_last_full(rs):
    # Rest as 简拼 initials, last syllable full: 中国 -> zhguo, 安山岩 -> anshyan.
    if not rs:
        return []
    return ["".join(_initial(r.syllable) for r in rs[:-1]) + rs[-1].syllable]


def _dentalized_init(syll):
    # The initial under FUZZY_ZH_Z (zh->z, ch->c, sh->s): the dental a retroflex collapses to.
    # 中国 -> zg (中's zong variant -> 'z'). Builds fuzzy+简拼 inputs.
    if len(syll) >= 2 and syll[:2] in ("zh", "ch", "sh"):
        return syll[0]
    return _initial(syll)


def _mixed_positions(token, w):
    # The 3 placements of a latin token around/inside a CJK word: prefix, suffix, infix (split at
    # mid). Yields (mixed_text, codepoints, position). Infix splits the CJK word, so only the token
    # itself is contiguous there (word-spanning inputs won't match -- handled by the caller).
    text = w.text
    mid = len(w.codepoints) // 2

    def cps(s):
        return [ord(ch) for ch in s]

    yield token + text, cps(token + text), "prefix"
    yield text + token, cps(text + token), "suffix"
    if mid > 0:
        yield text[:mid] + token + text[mid:], cps(text[:mid] + token + text[mid:]), "infix"


def _positive_case(prefix, cid, w, inp, variant, fuzzies=None, mode="CONTAINS", polarity="POSITIVE"):
    return {
        "id": f"{prefix}-{cid:07d}",
        "system": "MANDARIN",
        "level": "WORD",
        "mode": mode,
        "keyboard": "QUANPIN",
        "input": inp,
        "variant": variant,
        "fuzzies": fuzzies or [],
        "expected": [_key_str(tuple(w.codepoints))],
        "expectedTotal": 1,
        "sourceEntry": ",".join(_hex(c) for c in w.codepoints),
        "sourceText": w.text,
        "polarity": polarity,
    }


def gen_jianpin_mix_cases(ds, out_dir):
    """简拼/full mix inputs -- a single-position-full sweep: for each syllable position i, that
    syllable is full and the rest are 简拼 initials (covers first/last AND middle, for every sampled
    word). All need abbrev(INITIALS) -> variant ABBREV. POSITIVE, CONTAINS."""
    rng = random.Random(20260712)
    words = [w for w in ds.words if w.system == System.MANDARIN and _word_reproducible(ds, w)]
    sample = rng.sample(words, min(_JIANPIN_MIX_SAMPLE, len(words)))
    cases = []
    cid = 0
    for w in sample:
        rs = w.readings
        for i in range(len(rs)):
            parts = [_initial(r.syllable) for r in rs]
            parts[i] = rs[i].syllable
            cases.append(_positive_case("MANDARIN-JIANPIN-MIX", cid, w, "".join(parts), "ABBREV"))
            cid += 1
    return [_write(out_dir, "regression/jianpin-mix-mandarin.jsonl", cases)]


def gen_long_text_cases(ds, out_dir):
    """Long-text (>=5 char) CEDICT phrases across many transforms: full pinyin, 简拼 all-initials,
    full+initial mixes (first/last), a 2-syllable prefix, and contiguous 2-char sub-phrase windows
    at several offsets (CONTAINS-over-long-text segmentation). POSITIVE."""
    words = [
        w
        for w in ds.words
        if w.system == System.MANDARIN
        and _word_reproducible(ds, w)
        and len(w.codepoints) >= _LONG_MIN_CODEPOINTS
    ]
    cases = []
    cid = 0
    for w in words:
        rs = w.readings
        k = len(rs)
        transforms = [
            ("FULL", "".join(r.syllable for r in rs)),
            ("ABBREV", "".join(_initial(r.syllable) for r in rs)),
            ("ABBREV", _mix_first_full(rs)[0]),
            ("ABBREV", _mix_last_full(rs)[0]),
            ("FULL", "".join(r.syllable for r in rs[:2])),  # 2-syllable prefix
        ]
        # contiguous 2-char windows at start, ~1/3, ~2/3, end (dedup offsets)
        for off in sorted({0, k // 3, (2 * k) // 3, max(0, k - 2)}):
            win = rs[off:off + 2]
            if win:
                transforms.append(("FULL", "".join(r.syllable for r in win)))
        for variant, inp in transforms:
            cases.append(_positive_case("MANDARIN-LONG-TEXT", cid, w, inp, variant))
            cid += 1
    return [_write(out_dir, "regression/long-text-mandarin.jsonl", cases)]


def gen_mixed_script_cases(ds, out_dir):
    """CJK+latin mixed text -- synthesized entries with the latin token as prefix, suffix, or infix
    of a CJK word. Single-system mandarin matches latin literally (CharNode.literal fallback) + CJK
    phonetically. Inputs: literal token, full pinyin, token+简拼, token+full pinyin. POSITIVE,
    CONTAINS."""
    rng = random.Random(20260713)
    words = [w for w in ds.words if w.system == System.MANDARIN and _word_reproducible(ds, w)]
    sample = rng.sample(words, min(_MIXED_WORD_SAMPLE, len(words)))
    cases = []
    cid = 0
    for token in _LATIN_TOKENS:
        for w in sample:
            full = "".join(r.syllable for r in w.readings)
            init = "".join(_initial(r.syllable) for r in w.readings)
            for mixed_text, mixed_cps, pos in _mixed_positions(token, w):
                # Only prefix/suffix keep the CJK word contiguous -> word-spanning inputs valid
                # (token+word for prefix, word+token for suffix). Infix splits the word -> only the
                # literal token matches.
                if pos == "infix":
                    inputs = (("FULL", token),)
                elif pos == "prefix":
                    inputs = (
                        ("FULL", token),
                        ("FULL", full),
                        ("ABBREV", token + init),
                        ("FULL", token + full),
                    )
                else:  # suffix
                    inputs = (
                        ("FULL", token),
                        ("FULL", full),
                        ("ABBREV", init + token),
                        ("FULL", full + token),
                    )
                for variant, inp in inputs:
                    cases.append(
                        _mixed_case(
                            "MANDARIN-MIXED-SCRIPT", cid, mixed_text, mixed_cps, inp, variant))
                    cid += 1
    return [_write(out_dir, "regression/mixed-script-mandarin.jsonl", cases)]


def gen_fuzzy_jianpin_cases(ds, out_dir):
    """Retroflex fuzzy + 简拼 composition: with FUZZY_ZH_Z/CH_C/SH_S on, a zh/ch/sh-initial
    syllable matches its dental via the fuzzy variant's initial (中 -> zong -> 'z', 山 -> san ->
    's'), so 'zg' reaches 中国 ONLY with the rules on. Input = each syllable's dentalized initial.
    variant ABBREV + the 3 retroflex fuzzies. POSITIVE, CONTAINS."""
    rng = random.Random(20260714)
    words = [
        w
        for w in ds.words
        if w.system == System.MANDARIN
        and _word_reproducible(ds, w)
        and any(r.syllable[:2] in ("zh", "ch", "sh") for r in w.readings)
    ]
    sample = rng.sample(words, min(_FUZZY_JIANPIN_SAMPLE, len(words)))
    fuzzies = ["FUZZY_ZH_Z", "FUZZY_CH_C", "FUZZY_SH_S"]
    cases = []
    cid = 0
    for w in sample:
        inp = "".join(_dentalized_init(r.syllable) for r in w.readings)
        cases.append(
            _positive_case("MANDARIN-FUZZY-JIANPIN", cid, w, inp, "ABBREV", fuzzies))
        cid += 1
    return [_write(out_dir, "regression/fuzzy-jianpin-mandarin.jsonl", cases)]


def _mixed_case(prefix, cid, text, cps, inp, variant, polarity="POSITIVE"):
    return {
        "id": f"{prefix}-{cid:07d}",
        "system": "MANDARIN",
        "level": "WORD",
        "mode": "CONTAINS",
        "keyboard": "QUANPIN",
        "input": inp,
        "variant": variant,
        "fuzzies": [],
        "expected": [_key_str(tuple(cps))],
        "expectedTotal": 1,
        "sourceEntry": ",".join(_hex(c) for c in cps),
        "sourceText": text,
        "polarity": polarity,
    }


_MODES_SAMPLE = 6000
_NEG_SAMPLE = 6000


def gen_modes_cases(ds, out_dir):
    """BEGINS + EQUAL (EXACT) match modes -- the other regression files are CONTAINS-only; this
    covers the begins (proper prefix, partial tail allowed) and matches (consume-all) matcher
    paths. POSITIVE, on 2+ char words."""
    rng = random.Random(20260715)
    words = [
        w
        for w in ds.words
        if w.system == System.MANDARIN and _word_reproducible(ds, w) and len(w.codepoints) >= 2
    ]
    sample = rng.sample(words, min(_MODES_SAMPLE, len(words)))
    cases = []
    cid = 0
    for w in sample:
        rs = w.readings
        full = "".join(r.syllable for r in rs)
        init = "".join(_initial(r.syllable) for r in rs)
        # EQUAL: whole-word pinyin / 简拼 must consume the entire text exactly.
        cases.append(_positive_case("MANDARIN-MODES", cid, w, full, "FULL", mode="EXACT"))
        cid += 1
        cases.append(_positive_case("MANDARIN-MODES", cid, w, init, "ABBREV", mode="EXACT"))
        cid += 1
        # BEGINS: a proper prefix (first syllable only) -- partial tail allowed.
        cases.append(_positive_case("MANDARIN-MODES", cid, w, rs[0].syllable, "FULL", mode="BEGINS"))
        cid += 1
        cases.append(
            _positive_case(
                "MANDARIN-MODES", cid, w, _initial(rs[0].syllable), "ABBREV", mode="BEGINS"))
        cid += 1
    return [_write(out_dir, "regression/modes-mandarin.jsonl", cases)]


def gen_negative_scenario_cases(ds, out_dir):
    """Precision (NEGATIVE) cases -- inputs that must NOT match (sound regardless of polyphones):
    (a) a single first syllable under EXACT on a 2+ char word (the rest is unconsumed, so not an
    exact match); (b) an absent latin token in a synthesized mixed entry (literal mismatch -> no
    CONTAINS match)."""
    rng = random.Random(20260716)
    words = [w for w in ds.words if w.system == System.MANDARIN and _word_reproducible(ds, w)]
    multi = [w for w in words if len(w.codepoints) >= 2]
    cases = []
    cid = 0
    # (a) EXACT + first syllable on a multi-char word: the remaining chars are unconsumed.
    for w in rng.sample(multi, min(_NEG_SAMPLE, len(multi))):
        cases.append(
            _positive_case(
                "MANDARIN-NEG", cid, w, w.readings[0].syllable, "FULL",
                mode="EXACT", polarity="NEGATIVE"))
        cid += 1
    # (b) an absent latin token in a mixed entry: no literal nor phonetic match.
    for w in rng.sample(words, min(_NEG_SAMPLE, len(words))):
        mixed_text = "x" + w.text
        mixed_cps = [ord(ch) for ch in mixed_text]
        cases.append(
            _mixed_case(
                "MANDARIN-NEG", cid, mixed_text, mixed_cps, "zzz", "FULL", polarity="NEGATIVE"))
        cid += 1
    return [_write(out_dir, "regression/negative-scenarios-mandarin.jsonl", cases)]


def gen_all(ds, out_dir, seed=20260711):
    rng = random.Random(seed)
    files = []
    files += gen_char_cases(ds, out_dir, System.MANDARIN, "mandarin", ["FULL", "PARTIAL", "TONED"])
    files += gen_mandarin_word_cases(ds, out_dir)
    # Variants per system: TONED is only meaningful where `normalized` carries tone info the
    # engine's STRICT policy can check -- Cantonese (jyutping digit) and Zhuyin (Bopomofo marks).
    # Japanese/Korean use ToneConvention.NONE and have normalized == syllable, so TONED would
    # duplicate FULL; they get PARTIAL (keyboard key prefix) instead. Mandarin has all three.
    files += gen_char_cases(ds, out_dir, System.CANTONESE, "cantonese", ["FULL", "PARTIAL", "TONED"])
    files += gen_char_cases(ds, out_dir, System.ZHUYIN, "zhuyin", ["FULL", "PARTIAL", "TONED"])
    files += gen_char_cases(ds, out_dir, System.JAPANESE, "japanese", ["FULL", "PARTIAL"])
    files += gen_japanese_word_cases(ds, out_dir)
    files += gen_char_cases(ds, out_dir, System.KOREAN, "korean", ["FULL", "PARTIAL"])
    files += gen_korean_word_cases(ds, out_dir)
    files += gen_shuangpin_cases(ds, out_dir)
    files += gen_mandarin_zhuyin_equiv(ds, out_dir)
    files += gen_fuzzy_char_cases(ds, out_dir)
    files += gen_negatives(ds, out_dir, rng)
    files += gen_jianpin_mix_cases(ds, out_dir)
    files += gen_long_text_cases(ds, out_dir)
    files += gen_mixed_script_cases(ds, out_dir)
    files += gen_fuzzy_jianpin_cases(ds, out_dir)
    files += gen_modes_cases(ds, out_dir)
    files += gen_negative_scenario_cases(ds, out_dir)
    return files
