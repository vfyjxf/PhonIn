#!/usr/bin/env python3
"""PhonIn dataset generator (Python pipeline).

Fetches all upstream sources, normalizes them, derives Zhuyin, and emits the raw
tables + MANIFEST + reports under --out. Matching-case generation runs as a second
phase (see gen_cases.py); this script focuses on the raw dataset.

Usage:
    python3 tools/build_dataset.py --out=<dir> --cache=<dir> \
        --unihan-version=16.0.0 --mozillazg-commit=<sha>
"""

import argparse
import os
import sys

# make `tools/` importable when run as a script
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from phonin_data import DataSet, System  # noqa: E402
from phonin_data import cases, derive, emit, sources  # noqa: E402

SOURCES_META = {
    "unihan": {"url": "https://www.unicode.org/Public/zipped/16.0.0/Unihan.zip",
               "license": "Unicode Data Files agreement"},
    "mozillazg-pinyin-data": {"url": "https://github.com/mozillazg/pinyin-data",
                              "license": "MIT"},
    "cc-cedict": {"url": "https://www.mdbg.net/chinese/dictionary?page=cedict",
                  "license": "CC-BY-SA-4.0"},
    "kanjidic2": {"url": "https://www.edrdg.org/kanjidic/", "license": "EDRDG"},
    "jmdict": {"url": "https://www.edrdg.org/jmdict/j_jmdict.html", "license": "EDRDG"},
    "wordshk-tools": {"url": "https://github.com/AlienKevin/wordshk-tools", "license": "MIT"},
    "words-hk": {"url": "https://words.hk/faiman/request_data/", "license": "public domain"},
    "pypinyin": {"url": "https://github.com/mozillazg/python-pinyin", "license": "MIT"},
}


def main():
    ap = argparse.ArgumentParser(description="Build the PhonIn dataset.")
    ap.add_argument("--out", required=True)
    ap.add_argument("--cache", required=True)
    ap.add_argument("--unihan-version", default="16.0.0")
    ap.add_argument("--mozillazg-commit", default="923b108dc5d45dee061324c011b478fb649f8b73")
    ap.add_argument("--max-disagreement", type=float, default=0.02)
    ap.add_argument("--build-timestamp", default="unknown")
    ap.add_argument("--skip-jmdict", action="store_true", help="skip JMDict (large download)")
    args = ap.parse_args()

    out_dir = os.path.abspath(args.out)
    cache_dir = os.path.abspath(args.cache)
    os.makedirs(out_dir, exist_ok=True)
    print(f"PhonIn dataset generator (out={out_dir})")

    ds = DataSet()

    print("[unihan]")
    sources.parse_unihan(ds, cache_dir, args.unihan_version)
    print("[mozillazg]")
    mozillazg = sources.parse_mozillazg(cache_dir, args.mozillazg_commit)
    print("[cc-cedict]")
    sources.parse_cedict(ds, cache_dir)
    print("[words.hk chars]")
    sources.parse_wordshk_chars(ds, cache_dir)
    print("[words.hk words]")
    sources.parse_wordshk_words(ds, cache_dir)
    print("[kanjidic2]")
    sources.parse_kanjidic2(ds, cache_dir)
    if not args.skip_jmdict:
        print("[jmdict]")
        sources.parse_jmdict(ds, cache_dir)
    print("[korean]")
    sources.add_korean(ds)
    sources.add_korean_words(ds)

    print("[derive]")
    derive.supplement_mandarin(ds)
    derive.derive_zhuyin(ds)

    # cross-check (Unihan mandarin vs mozillazg)
    print("[cross-check]")
    in_both = disagree = 0
    samples = []
    for cp, moz in mozillazg.items():
        ce = ds.chars.get(cp)
        if not ce or System.MANDARIN not in ce.readings:
            continue
        our = {r.normalized for r in ce.readings[System.MANDARIN]}
        in_both += 1
        if not (our & moz):
            disagree += 1
            if len(samples) < 100:
                samples.append(f"U+{cp:X}  ours={sorted(our)}  mozillazg={sorted(moz)}")
    rate = (disagree / in_both) if in_both else 0.0
    emit.write_json(out_dir, "reports/cross-check-mandarin.json", {
        "comparison": "unihan-mandarin vs mozillazg/pinyin-data",
        "codepointsInBoth": in_both,
        "disagreements": disagree,
        "disagreementRate": rate,
        "maxAllowedRate": args.max_disagreement,
        "sampleDisagreements": samples,
    })
    print(f"  disagreement rate: {rate * 100:.4f}%")
    if rate > args.max_disagreement:
        raise SystemExit(f"cross-check disagreement {rate * 100:.4f}% exceeds {args.max_disagreement * 100}%")

    print("[emit raw]")
    files = []
    files.append(emit.write_char_table(ds, out_dir, System.MANDARIN, "raw/mandarin-char.tsv"))
    files.append(emit.write_char_table(ds, out_dir, System.CANTONESE, "raw/cantonese-char.tsv"))
    files.append(emit.write_char_table(ds, out_dir, System.ZHUYIN, "raw/zhuyin-char.tsv"))
    files.append(emit.write_char_table(ds, out_dir, System.JAPANESE, "raw/japanese-char.tsv"))
    files.append(emit.write_char_table(ds, out_dir, System.KOREAN, "raw/korean-char.tsv"))
    files.append(emit.write_word_table(ds, out_dir, System.MANDARIN, "raw/mandarin-word.tsv"))
    files.append(emit.write_word_table(ds, out_dir, System.CANTONESE, "raw/cantonese-word.tsv"))
    files.append(emit.write_word_table(ds, out_dir, System.JAPANESE, "raw/japanese-word.tsv"))
    files.append(emit.write_word_table(ds, out_dir, System.KOREAN, "raw/korean-word.tsv"))

    stats = {
        "chars": {s.value: ds.count_chars(s) for s in System},
        "words": {s.value: sum(1 for w in ds.words if w.system == s) for s in System},
        "totalChars": len(ds.chars),
        "totalWords": len(ds.words),
    }

    print("[generate cases]")
    files += cases.gen_all(ds, out_dir)
    print("[emit keymaps]")
    files += emit.write_shuangpin_keymaps(ds, out_dir)
    stats["caseFiles"] = len([f for f in files if "/generated/" in f[0] or f[0].startswith("generated/")])
    stats["totalCases"] = sum(f[1] for f in files if "/generated/" in f[0] or f[0].startswith("generated/"))

    emit.write_json(out_dir, "reports/stats.json", stats)
    emit.write_manifest(out_dir, files, stats, {
        "generatedAt": args.build_timestamp,
        "unihanVersion": args.unihan_version,
        "mozillazgCommit": args.mozillazg_commit,
        "sources": SOURCES_META,
    })
    print("done.")


if __name__ == "__main__":
    main()
