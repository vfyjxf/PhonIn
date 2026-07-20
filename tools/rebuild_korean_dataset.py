#!/usr/bin/env python3
"""Rebuild the Korean dataset from scratch with 2-bulsik keyboard input.

Data sources:
  1. Algorithmic: all 11,172 precomposed Hangul syllables (U+AC00..U+D7A3)
  2. hanja_char.csv: 27,848 Hanja→Hangul character mappings
  3. hanjadict PyPI: 53,458 Hanja characters with 훈음 (hun-eum)
  4. hanja_word.csv: 135 Hanja word→Hangul word mappings (irregular)
  5. ko_50k.txt: 50,000 most frequent Korean words (pure Hangul)
  6. dueum.csv: 52 두음법칙 (dueum law) mappings

All Hangul is converted to 2-bulsik keyboard key sequences. Outputs:
  - raw/korean-char.tsv (character-level)
  - raw/korean-word.tsv (word-level, NEW)
  - generated/korean/cases-char-full.jsonl
  - generated/korean/cases-char-partial.jsonl
  - generated/korean/cases-word-full.jsonl (NEW)
  - generated/korean/cases-negative.jsonl
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from phonin_data.korean import syllable_to_keyboard, is_hangul_syllable

BASE_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "phonin-data", "src", "main", "resources", "phonin")
SOURCES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "korean_sources")

EXPECT_CAP = 24


def hangul_to_keyboard(text):
    """Convert a string of Hangul to 2-bulsik keyboard key sequences."""
    out = []
    for ch in text:
        cp = ord(ch)
        if is_hangul_syllable(cp):
            out.append(syllable_to_keyboard(cp))
        else:
            out.append(ch)
    return "".join(out)


def hex_cp(cp):
    return "U+" + format(cp, "X")


# ---- Source 1: Algorithmic Hangul syllables ----------------------------

def load_algorithmic_hangul():
    """All 11,172 precomposed Hangul syllables → (codepoint, hangul, keyboard)."""
    result = []
    for cp in range(0xAC00, 0xD7A4):
        hangul = chr(cp)
        kb = syllable_to_keyboard(cp)
        result.append((cp, hangul, kb))
    return result


# ---- Source 2: hanja_char.csv ------------------------------------------

def load_hanja_char_csv():
    """Parse hanja_char.csv: Hanja char → Hangul syllable."""
    result = {}  # codepoint → Hangul string
    path = os.path.join(SOURCES_DIR, "hanja_char.csv")
    with open(path, encoding="utf-8-sig") as f:
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
            if cp not in result:
                result[cp] = hangul
    return result


# ---- Source 3: hanjadict PyPI package ----------------------------------

def load_hanjadict():
    """Parse hanjadict: 53,458 Hanja chars with 훈음 → extract 음 (Sino-Korean sound)."""
    import hanjadict
    result = {}  # codepoint → Hangul string
    for char, hun_eum in hanjadict.table_data.items():
        cp = ord(char)
        # 훈음 format: "훈 음" — the last token is the Sino-Korean pronunciation
        # e.g. "언덕 구" → 음=구, "기쁠 희" → 음=희
        # Some entries may have multi-syllable 음, e.g. "구별할 항" → 음=항
        parts = hun_eum.strip().split()
        if len(parts) < 2:
            continue
        sound = parts[-1].strip()
        if not sound:
            continue
        # Verify it's valid Hangul
        if all(is_hangul_syllable(ord(ch)) for ch in sound):
            if cp not in result:
                result[cp] = sound
    return result


# ---- Source 4: hanja_word.csv ------------------------------------------

def load_hanja_word_csv():
    """Parse hanja_word.csv: Hanja word → Hangul word (irregular conversions)."""
    result = []  # [(hanja_text, hangul_text), ...]
    path = os.path.join(SOURCES_DIR, "hanja_word.csv")
    with open(path, encoding="utf-8-sig") as f:
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
            result.append((hanja, hangul))
    return result


# ---- Source 5: Korean 50k frequency word list --------------------------

def load_ko_50k():
    """Parse ko_50k.txt: word frequency list → [(word, freq), ...]."""
    result = []
    path = os.path.join(SOURCES_DIR, "ko_50k.txt")
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            word = parts[0].strip()
            try:
                freq = int(parts[1])
            except ValueError:
                continue
            if not word:
                continue
            result.append((word, freq))
    return result


# ---- Build character table ---------------------------------------------

def build_char_table():
    """Build the character-level dataset: codepoint → {hangul_display: hangul_normalized}.

    The normalized form is now Hangul itself (the canonical form). The Java KoreanKeyboard
    re-maps it to the appropriate key sequence at match time.
    """
    # codepoint → {hangul_display: hangul_normalized}
    chars = {}

    # Source 1: algorithmic Hangul syllables
    for cp, hangul, _ in load_algorithmic_hangul():
        chars.setdefault(cp, {}).setdefault(hangul, hangul)

    # Source 2: hanja_char.csv
    hanja_char = load_hanja_char_csv()
    for cp, hangul in hanja_char.items():
        chars.setdefault(cp, {}).setdefault(hangul, hangul)

    # Source 3: hanjadict
    hanjadict_data = load_hanjadict()
    for cp, hangul in hanjadict_data.items():
        chars.setdefault(cp, {}).setdefault(hangul, hangul)

    return chars


def write_char_tsv(chars, path):
    """Write korean-char.tsv."""
    header = "# codepoint\tchar\treadings_display\treadings_normalized\ttones\tfreq\tsources"
    lines = []
    for cp in sorted(chars):
        readings = chars[cp]
        displays = []
        norms = []
        for hangul, norm in readings.items():
            displays.append(hangul)
            norms.append(norm)  # norm == hangul (canonical form)
        lines.append("\t".join([
            hex_cp(cp),
            chr(cp),
            ",".join(displays),
            ",".join(norms),
            ",".join(["0"] * len(displays)),
            ",".join(["0"] * len(displays)),
            "HANGUL",
        ]))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(header + "\n")
        for line in lines:
            f.write(line + "\n")
    print(f"  wrote {path}: {len(lines)} rows")
    return len(lines)


# ---- Build word table --------------------------------------------------

def build_word_table():
    """Build the word-level dataset: [(codepoints, text, hangul_normalized), ...].

    The normalized form is Hangul itself (canonical). The Java KoreanKeyboard re-maps it.
    """
    words = []  # [(codepoints, text, hangul_normalized, original), ...]
    seen = set()

    # Source 4: hanja_word.csv (irregular Hanja word → Hangul word)
    for hanja, hangul in load_hanja_word_csv():
        cps = [ord(ch) for ch in hanja]
        key = (tuple(cps), hangul)
        if key not in seen:
            seen.add(key)
            words.append((cps, hangul, hangul, hanja))

    # Source 5: Korean 50k frequency word list (pure Hangul words)
    for word, freq in load_ko_50k():
        if not all(is_hangul_syllable(ord(ch)) for ch in word):
            continue
        if len(word) < 2:
            continue
        cps = [ord(ch) for ch in word]
        key = (tuple(cps), word)
        if key not in seen:
            seen.add(key)
            words.append((cps, word, word, word))

    return words


def write_word_tsv(words, path):
    """Write korean-word.tsv."""
    header = "# codepoints\ttext\treadings_display\treadings_normalized\tsource"
    lines = []
    for cps, text, norm, orig in words:
        lines.append("\t".join([
            ",".join(hex_cp(cp) for cp in cps),
            text,
            text,  # display = Hangul text
            norm,  # normalized = Hangul (canonical form)
            "KOREAN_CORPUS",
        ]))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(header + "\n")
        for line in lines:
            f.write(line + "\n")
    print(f"  wrote {path}: {len(lines)} rows")
    return len(lines)


# ---- Generate test cases -----------------------------------------------

def _cap(keys, source_key=None):
    keys = sorted(set(keys))
    if source_key in keys:
        keys.remove(source_key)
        keys = [source_key] + keys

    def key_str(k):
        if isinstance(k, int):
            return hex_cp(k)
        if isinstance(k, tuple):
            return ",".join(hex_cp(c) for c in k)
        return k

    return [key_str(k) for k in keys[:EXPECT_CAP]], len(keys)


def gen_char_cases(chars, out_dir, variant):
    """Generate char-level FULL or PARTIAL test cases.

    The test input is the 2-bulsik keyboard key sequence (what the user types), derived from the
    stored Hangul normalized form via syllable_to_keyboard().
    """
    subdir = "generated/korean"
    # Build index: keyboard_input → [codepoint, ...]
    if variant == "FULL":
        transform = lambda items: {hangul_to_keyboard(h) for h, _ in items}
        pick = lambda items: [hangul_to_keyboard(h) for h, _ in items]
    else:  # PARTIAL
        transform = lambda items: {
            (hangul_to_keyboard(h)[:2] if len(hangul_to_keyboard(h)) >= 2 else hangul_to_keyboard(h))
            for h, _ in items}
        pick = lambda items: [
            (hangul_to_keyboard(h)[:2] if len(hangul_to_keyboard(h)) >= 2 else hangul_to_keyboard(h))
            for h, _ in items]

    idx = {}
    for cp, readings in chars.items():
        for inp in transform(readings.items()):
            idx.setdefault(inp, []).append(cp)

    cases = []
    cid = 0
    for cp in sorted(chars):
        readings = chars[cp]
        for inp in pick(readings.items()):
            bucket = idx.get(inp, [])
            exp, total = _cap(bucket, cp)
            cases.append({
                "id": f"KOREAN-CHAR-{variant}-{cid:07d}",
                "system": "KOREAN", "level": "CHAR", "mode": "CONTAINS",
                "keyboard": "BULSIK2",
                "input": inp, "variant": variant, "fuzzies": [],
                "expected": exp, "expectedTotal": total,
                "sourceEntry": hex_cp(cp), "polarity": "POSITIVE",
            })
            cid += 1

    rel = f"{subdir}/cases-char-{variant.lower()}.jsonl"
    path = os.path.join(out_dir, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for c in cases:
            f.write(json.dumps(c, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"  wrote {rel}: {len(cases)} cases")
    return rel, len(cases)


def gen_word_cases(words, out_dir):
    """Generate word-level FULL test cases.

    The test input is the 2-bulsik keyboard key sequence for the whole word. The sourceEntry and
    expected fields use codepoint tuples (U+XXXX,U+YYYY) to match CaseRunnerTest semantics.
    """
    subdir = "generated/korean"
    # Build index: keyboard_input → [codepoint_tuple, ...]
    idx = {}
    for cps, text, norm, orig in words:
        kb = hangul_to_keyboard(norm)
        key = tuple(cps)
        idx.setdefault(kb, []).append(key)

    cases = []
    cid = 0
    for cps, text, norm, orig in words:
        kb = hangul_to_keyboard(norm)
        key = tuple(cps)
        bucket = idx.get(kb, [])
        exp, total = _cap(bucket, key)
        cases.append({
            "id": f"KOREAN-WORD-FULL-{cid:07d}",
            "system": "KOREAN", "level": "WORD", "mode": "EXACT",
            "keyboard": "BULSIK2",
            "input": kb, "variant": "FULL", "fuzzies": [],
            "expected": exp, "expectedTotal": total,
            "sourceEntry": ",".join(hex_cp(c) for c in cps),
            "sourceText": text,
            "polarity": "POSITIVE",
        })
        cid += 1

    rel = f"{subdir}/cases-word-full.jsonl"
    path = os.path.join(out_dir, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for c in cases:
            f.write(json.dumps(c, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"  wrote {rel}: {len(cases)} cases")
    return rel, len(cases)


def gen_negatives(chars, out_dir, rng, count=20000):
    """Generate negative test cases: random keyboard-like strings that don't match."""
    subdir = "generated/korean"
    # The keyboard alphabet: lowercase a-z + uppercase for shifted keys (Q,W,E,R,T,O,P)
    alphabet = "abcdefghijklmnopqrstuvwxyzQWERTOP"

    # Build the universe of all valid keyboard prefixes (apply keyboard encoding to Hangul)
    universe = set()
    for cp, readings in chars.items():
        for hangul, _ in readings.items():
            kb = hangul_to_keyboard(hangul)
            for k in range(1, len(kb) + 1):
                universe.add(kb[:k])

    cases = []
    cid = 0
    tries = 0
    maxlen = 5
    while cid < count and tries < count * 40:
        tries += 1
        length = rng.randint(2, maxlen)
        inp = "".join(rng.choice(alphabet) for _ in range(length))
        if inp in universe:
            continue
        cases.append({
            "id": f"KOREAN-NEG-{cid:07d}",
            "system": "KOREAN", "level": "CHAR", "mode": "CONTAINS",
            "keyboard": "BULSIK2",
            "input": inp, "variant": "FULL", "fuzzies": [],
            "expected": [], "expectedTotal": 0,
            "sourceEntry": "U+AC00", "polarity": "NEGATIVE",
        })
        cid += 1

    rel = f"{subdir}/cases-negative.jsonl"
    path = os.path.join(out_dir, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for c in cases:
            f.write(json.dumps(c, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"  wrote {rel}: {len(cases)} cases ({tries} tries)")
    return rel, len(cases)


# ---- Main ---------------------------------------------------------------

def main():
    print("=== Rebuilding Korean dataset with 2-bulsik keyboard input ===")

    print("\n[1] Build character table")
    chars = build_char_table()
    print(f"  {len(chars)} characters with Korean readings")

    print("\n[2] Write korean-char.tsv")
    write_char_tsv(chars, os.path.join(BASE_DIR, "raw", "korean-char.tsv"))

    print("\n[3] Build word table")
    words = build_word_table()
    print(f"  {len(words)} word entries")

    print("\n[4] Write korean-word.tsv")
    write_word_tsv(words, os.path.join(BASE_DIR, "raw", "korean-word.tsv"))

    print("\n[5] Generate test cases")
    rng = random.Random(20260714)
    gen_char_cases(chars, BASE_DIR, "FULL")
    gen_char_cases(chars, BASE_DIR, "PARTIAL")
    gen_word_cases(words, BASE_DIR)
    gen_negatives(chars, BASE_DIR, rng)

    print("\n=== Done ===")


if __name__ == "__main__":
    main()
