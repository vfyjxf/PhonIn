#!/usr/bin/env python3
"""Emit per-rule golden parity tables (surface -> variants) for the Java
``FuzzyRulesParityTest``.

``fuzzy.py`` is the independent spec the Java engine must reproduce. This script
enumerates exactly the surfaces the engine matches — the toneless syllables
already written to each system's ``cases-char-full.jsonl`` (whose ``input`` IS
``Reading.syllable``) — and records what ``fuzzy.py``'s ``variants`` returns for
each, including the empty case. The Java test then asserts equality on every
surface, which catches both directions of divergence (a missing variant AND an
extra one) — the latter is what the positive fuzzy cases cannot detect on their
own.

Usage:
    python3 tools/emit_fuzzy_golden.py \\
        --res phonin-data/src/main/resources/phonin \\
        --out phonin-data/src/main/resources/phonin
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from phonin_data import fuzzy as fz  # noqa: E402
from phonin_data import System  # noqa: E402


def system_surfaces(res_dir, system):
    """Toneless syllables for one system, read from its generated FULL char cases."""
    path = os.path.join(
        res_dir, "generated", system.value.lower(), "cases-char-full.jsonl"
    )
    surfaces = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                c = json.loads(line)
            except ValueError:
                continue
            inp = c.get("input")
            if inp:
                surfaces.add(inp)
    return surfaces


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--res",
        required=True,
        help="phonin resource dir (containing generated/<sys>/cases-char-full.jsonl)",
    )
    ap.add_argument(
        "--out",
        required=True,
        help="output dir (golden files written under <out>/fuzzy/)",
    )
    args = ap.parse_args()

    by_system = {s: system_surfaces(args.res, s) for s in System}
    print("surfaces per system:")
    for s in System:
        print(f"  {s.value}: {len(by_system[s])}")

    for rule in fz.RULES:
        surfaces = sorted(by_system[rule.system])
        rel = "fuzzy/golden-%s.tsv" % rule.name.lower()
        path = os.path.join(args.out, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(
                "# golden parity table for %s: surface -> variants (oracle: fuzzy.py)\n"
                % rule.name
            )
            f.write("# one line per real surface; variants is a sorted comma-list (may be empty)\n")
            f.write("# surface\tvariants\n")
            for s in surfaces:
                vs = sorted({v for v in rule.variants(s) if v})
                f.write("%s\t%s\n" % (s, ",".join(vs)))
        print("  wrote %s: %d surfaces" % (rel, len(surfaces)))


if __name__ == "__main__":
    main()
