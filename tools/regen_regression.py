#!/usr/bin/env python3
"""Regenerate ONLY the committed regression/ datasets.

Skips the raw tables, keymaps, MANIFEST, and the gitignored generated/ corpus -- and by default
skips JMDict (--skip-jmdict), which is Japanese and unused by the Mandarin-only regression
generators. Use this to iterate fast on the regression generators without a full
`build_dataset.py` run (~2-3 min vs ~5 min).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from phonin_data import DataSet  # noqa: E402
from phonin_data import cases, derive, sources  # noqa: E402


def _load_ds(cache_dir, unihan_version, mozillazg_commit, skip_jmdict):
    ds = DataSet()
    sources.parse_unihan(ds, cache_dir, unihan_version)
    sources.parse_mozillazg(cache_dir, mozillazg_commit)
    sources.parse_cedict(ds, cache_dir)
    sources.parse_wordshk_chars(ds, cache_dir)
    sources.parse_wordshk_words(ds, cache_dir)
    sources.parse_kanjidic2(ds, cache_dir)
    if not skip_jmdict:
        sources.parse_jmdict(ds, cache_dir)
    sources.add_korean(ds)
    derive.supplement_mandarin(ds)
    derive.derive_zhuyin(ds)
    return ds


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default="phonin-data/src/main/resources/phonin")
    ap.add_argument("--cache", default="build/downloads")
    ap.add_argument("--unihan-version", default="16.0.0")
    ap.add_argument("--mozillazg-commit", default="923b108dc5d45dee061324c011b478fb649f8b73")
    ap.add_argument("--skip-jmdict", action="store_true")
    args = ap.parse_args()
    ds = _load_ds(args.cache, args.unihan_version, args.mozillazg_commit, args.skip_jmdict)
    cases.gen_jianpin_mix_cases(ds, args.out)
    cases.gen_long_text_cases(ds, args.out)
    cases.gen_mixed_script_cases(ds, args.out)
    cases.gen_fuzzy_jianpin_cases(ds, args.out)
    cases.gen_modes_cases(ds, args.out)
    cases.gen_negative_scenario_cases(ds, args.out)
    print("regression datasets regenerated.")


if __name__ == "__main__":
    main()
