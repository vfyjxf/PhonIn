"""Emit normalized raw tables (TSV), plus MANIFEST.json and reports (JSON)."""

import hashlib
import json
import os

from . import System

_RAW_HEADER = "# codepoint\tchar\treadings_display\treadings_normalized\ttones\tfreq\tsources"
_WORD_HEADER = "# codepoints\ttext\treadings_display\treadings_normalized\tsource"


def _cp_hex(cp):
    return "U+" + format(cp, "X")


def _write_tsv(out_dir, rel, header, lines):
    path = os.path.join(out_dir, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(header + "\n")
        for line in lines:
            f.write(line + "\n")
    return rel, len(lines)


def write_char_table(ds, out_dir, system, rel):
    lines = []
    for cp in sorted(e for e in ds.chars if system in ds.chars[e].readings):
        ce = ds.chars[cp]
        rs = ce.readings[system]
        lines.append("\t".join([
            _cp_hex(cp),
            ce.character,
            ",".join(r.display for r in rs),
            ",".join(r.normalized for r in rs),
            ",".join(str(r.tone) for r in rs),
            ",".join(str(r.freq) for r in rs),
            ",".join(s.value for s in sorted(ce.sources, key=lambda s: s.value)),
        ]))
    print(f"  wrote {rel}: {len(lines)} rows")
    return _write_tsv(out_dir, rel, _RAW_HEADER, lines)


def write_word_table(ds, out_dir, system, rel):
    lines = []
    for w in ds.words:
        if w.system != system:
            continue
        lines.append("\t".join([
            ",".join(_cp_hex(cp) for cp in w.codepoints),
            w.text,
            " ".join(r.display for r in w.readings),
            " ".join(r.normalized for r in w.readings),
            w.source.value,
        ]))
    print(f"  wrote {rel}: {len(lines)} rows")
    return _write_tsv(out_dir, rel, _WORD_HEADER, lines)


def _sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def write_manifest(out_dir, files, stats, meta):
    """files: list of (rel, rows). Compute sha256 + assemble MANIFEST.json."""
    file_info = {}
    for rel, rows in files:
        path = os.path.join(out_dir, rel)
        file_info[rel] = {"rows": rows, "sha256": _sha256(path), "bytes": os.path.getsize(path)}
    manifest = {
        "schemaVersion": 1,
        "generatedAt": meta.get("generatedAt", "unknown"),
        "unihanVersion": meta.get("unihanVersion"),
        "mozillazgCommit": meta.get("mozillazgCommit"),
        "sources": meta.get("sources", {}),
        "files": file_info,
        "stats": stats,
    }
    path = os.path.join(out_dir, "MANIFEST.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2, sort_keys=True)
    print(f"  wrote MANIFEST.json ({len(file_info)} files)")
    return manifest


def write_json(out_dir, rel, obj):
    path = os.path.join(out_dir, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, sort_keys=True)
    print(f"  wrote {rel}")


def write_shuangpin_keymaps(ds, out_dir):
    """Emit per-scheme pinyin-syllable -> code keymaps (engine data + derivation source)."""
    from . import shuangpin as sp
    syllables = sorted({r.syllable for ce in ds.chars.values()
                        if System.MANDARIN in ce.readings for r in ce.readings[System.MANDARIN]})
    files = []
    for scheme in sp.SCHEMES:
        rel = f"keymaps/shuangpin-{scheme}.tsv"
        path = os.path.join(out_dir, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        rows = 0
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# shuangpin {scheme} ({sp.SCHEME_NAMES[scheme]}): pinyin syllable -> code "
                    f"(ported from RIME rime-double-pinyin, BSD-3-Clause)\n")
            f.write("# pinyin\tcode\n")
            for syl in syllables:
                code = sp.encode(syl, scheme)
                if code:
                    f.write(f"{syl}\t{code}\n")
                    rows += 1
        print(f"  wrote {rel}: {rows} rows")
        files.append((rel, rows))
    return files
