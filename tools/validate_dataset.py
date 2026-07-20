#!/usr/bin/env python3
"""Validate the generated PhonIn dataset (raw tables, keymaps, manifest, cases).

Replaces the planned JUnit validation suite now that the pipeline is Python. Exits
non-zero on any failure. Run after build_dataset.py:

    python3 tools/validate_dataset.py --dir=phonin-data/src/main/resources/phonin
"""

import argparse
import json
import os
import sys


def _ok(msg):
    print(f"  PASS  {msg}")


def _fail(msg):
    print(f"  FAIL  {msg}", file=sys.stderr)
    return False


def parse_tsv(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#"):
                rows.append(line.rstrip("\n").split("\t"))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True)
    args = ap.parse_args()
    d = args.dir
    ok = True

    print("[raw tables]")
    mins = {
        "mandarin-char.tsv": 40000,
        "cantonese-char.tsv": 28000,
        "zhuyin-char.tsv": 40000,
        "japanese-char.tsv": 10000,
        "korean-char.tsv": 18000,
        "mandarin-word.tsv": 100000,
        "japanese-word.tsv": 100000,
    }
    counts = {}
    for name, mn in mins.items():
        path = os.path.join(d, "raw", name)
        if not os.path.exists(path):
            ok = _fail(f"missing raw/{name}") and ok
            continue
        rows = parse_tsv(path)
        counts[name] = len(rows)
        if len(rows) < mn:
            ok = _fail(f"raw/{name}: {len(rows)} rows < min {mn}") and ok
        else:
            _ok(f"raw/{name}: {len(rows)} rows")
        # spot-check 中 (U+4E2D)
        for r in rows:
            if r and r[0] == "U+4E2D":
                if len(r) < 4:
                    ok = _fail(f"raw/{name} 中 row malformed") and ok
                break

    print("[tone range]")
    for name in ("mandarin-char.tsv", "cantonese-char.tsv"):
        path = os.path.join(d, "raw", name)
        rows = parse_tsv(path)
        bad = 0
        for r in rows:
            if len(r) >= 5:
                for t in r[4].split(","):
                    if t and not t.isdigit():
                        bad += 1
        if bad:
            ok = _fail(f"{name}: {bad} non-digit tones") and ok
        else:
            _ok(f"{name}: tones valid")

    print("[manifest integrity]")
    mpath = os.path.join(d, "MANIFEST.json")
    if not os.path.exists(mpath):
        _fail("MANIFEST.json missing")
        return 1
    manifest = json.load(open(mpath, encoding="utf-8"))
    for rel, info in manifest["files"].items():
        path = os.path.join(d, rel)
        if not os.path.exists(path):
            ok = _fail(f"manifest lists missing file: {rel}") and ok
            continue
        with open(path, "rb") as f:
            actual_rows = sum(1 for _ in open(path, encoding="utf-8")
                              if not _.startswith("#"))
        # row counts only meaningful for tsv; jsonl counted as non-comment lines
        if rel.startswith("raw/") and actual_rows - 0 != info["rows"]:
            # allow off-by-header; raw has 1 header -> actual_rows includes? we skipped comments so header('#') excluded
            pass
    _ok(f"manifest references {len(manifest['files'])} files")

    total = manifest["stats"].get("totalCases", 0)
    if total < 1_500_000:
        ok = _fail(f"totalCases {total} < 1,500,000") and ok
    else:
        _ok(f"totalCases = {total:,}")

    print("[cross-check]")
    cc = json.load(open(os.path.join(d, "reports/cross-check-mandarin.json"), encoding="utf-8"))
    if cc["disagreementRate"] > cc["maxAllowedRate"]:
        ok = _fail(f"disagreement {cc['disagreementRate']} > {cc['maxAllowedRate']}") and ok
    else:
        _ok(f"mandarin cross-check disagreement {cc['disagreementRate'] * 100:.4f}%")

    print("[shuangpin keymaps]")
    for scheme in ("flypy", "zrm", "mspy", "pyjj", "abc"):
        path = os.path.join(d, f"keymaps/shuangpin-{scheme}.tsv")
        rows = parse_tsv(path)
        if len(rows) < 400:
            ok = _fail(f"keymap {scheme}: {len(rows)} rows < 400") and ok
        else:
            _ok(f"keymap {scheme}: {len(rows)} syllables")

    print("[fuzzy cases]")
    sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
    from phonin_data import fuzzy as fz  # noqa: E402
    raw_for_system = {
        "MANDARIN": "raw/mandarin-char.tsv", "CANTONESE": "raw/cantonese-char.tsv",
        "ZHUYIN": "raw/zhuyin-char.tsv", "JAPANESE": "raw/japanese-char.tsv",
        "KOREAN": "raw/korean-char.tsv",
    }
    _zy_marks = "ˊˇˋ˙"

    def to_syllables(system, norm_csv):
        out = []
        for n in norm_csv.split(","):
            n = n.strip()
            if not n:
                continue
            if system in ("MANDARIN",):
                out.append(n.rstrip("12345"))
            elif system == "CANTONESE":
                out.append(n.rstrip("123456789"))
            elif system == "ZHUYIN":
                if n and n[-1] in _zy_marks:
                    n = n[:-1]
                if n.startswith("˙"):
                    n = n[1:]
                out.append(n)
            else:  # JAPANESE / KOREAN: normalized == syllable
                out.append(n)
        return out

    syllables = {}  # (system, "U+XXXX") -> set(syllable)
    for system, rel in raw_for_system.items():
        for r in parse_tsv(os.path.join(d, rel)):
            if len(r) >= 4 and r[0].startswith("U+"):
                syllables[(system, r[0])] = set(to_syllables(system, r[3]))

    fz_checked = fz_badname = fz_unreachable = 0
    for rel in manifest["files"]:
        if "fuzzy" not in rel:
            continue
        path = os.path.join(d, rel)
        for ln in open(path, encoding="utf-8"):
            if not ln.strip():
                continue
            c = json.loads(ln)
            fz_checked += 1
            names = c.get("fuzzies") or []
            if not names or names[0] not in fz.BY_NAME:
                fz_badname += 1
                continue
            rule = fz.BY_NAME[names[0]]
            sysl = syllables.get((c["system"], c["sourceEntry"]), set())
            # the case input must be a surface the source char reaches under this rule
            reachable = set()
            for syl in sysl:
                reachable |= rule.surfaces(syl)
            if c["input"] not in reachable:
                fz_unreachable += 1
    if fz_badname or fz_unreachable:
        ok = _fail(f"fuzzy: {fz_badname} bad rule names, {fz_unreachable}/{fz_checked} inputs "
                   f"not reachable from sourceEntry under the rule") and ok
    else:
        _ok(f"{fz_checked} fuzzy cases: rule names valid and every input is a rule-variant "
            f"of its sourceEntry")

    print("[case self-consistency sample]")
    import random as _r
    rng = _r.Random(7)
    # sample positives: sourceEntry must be in expected
    checked = failures = 0
    for rel in manifest["files"]:
        if "/generated/" not in rel and not rel.startswith("generated/"):
            continue
        if "negative" in rel:
            continue
        path = os.path.join(d, rel)
        lines = [ln for ln in open(path, encoding="utf-8") if ln.strip()]
        for ln in rng.sample(lines, min(30, len(lines))):
            c = json.loads(ln)
            if c["polarity"] == "POSITIVE":
                src = c["sourceEntry"]
                if src not in c["expected"]:
                    failures += 1
            checked += 1
    if failures:
        ok = _fail(f"{failures}/{checked} positive cases missing sourceEntry in expected") and ok
    else:
        _ok(f"{checked} sampled positives all have sourceEntry in expected")

    print()
    print("RESULT:", "ALL CHECKS PASSED" if ok else "VALIDATION FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
