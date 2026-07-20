"""Fuzzy phonetic-equivalence rules, one per common confusion, grouped by system.

Each rule defines `variants(surface) -> List[str]` returning the alternative surface
strings (NOT including the original). `surfaces(surface)` returns the full equivalence
set (original + variants). The Java engine's `FuzzyRules` mirrors these functions
**exactly** (same name, same string transform), so Python data-gen and Java matching
agree by construction. `cases.py` is the regression net that enforces the agreement.

Two rule shapes:
  * SWAP  — a symmetric confusion (zh<->z, an<->ang, n<->l, u<->v, Hepburn<->Kunrei):
    both forms are typed interchangeably, so `variants` returns the counterpart and the
    index makes the two forms mutually reachable.
  * MERGE — an asymmetric simplification the USER types against a richer canonical form
    (drop initial ng, drop w after g/k, collapse a long vowel, ae->e). `variants` returns
    only the reduced form; the engine expands the target phoneme to expose it.

All rules operate on the toneless core surface (`Reading.syllable`):
  Mandarin syllable e.g. "zhong"; Cantonese "ngo"; Zhuyin Bopomofo "ㄓㄨㄥ";
  Japanese romaji "shi" (== normalized); Korean Revised Romanization "hae" (== normalized).
"""

import re
from typing import Callable, List

from . import System

# initials after which the u/ü distinction is real (n, l carry ü as 'v'; j, q, x, y carry
# ü as 'u'). Elsewhere (g, k, h, ...) 'u' is a genuine 'u' and must not be swapped.
_UV_INITIALS = frozenset("nljqxy")


def _dedup(xs):
    seen = set()
    out = []
    for x in xs:
        if x and x not in seen:
            seen.add(x)
            out.append(x)
    return out


# --------------------------------------------------------------------------- rule shapes

def initial_swap(a, b):
    """Symmetric swap of a leading initial `a` <-> `b` (e.g. zh<->z). `a` may be longer
    than `b`; the longer is matched first so zh is not shadowed by z."""
    def fn(s):
        if s.startswith(a):
            return [b + s[len(a):]]
        if s.startswith(b):
            return [a + s[len(b):]]
        return []
    return fn


def final_swap(a, b):
    """Symmetric swap of a trailing final `a` <-> `b` (e.g. ang<->an)."""
    def fn(s):
        if s.endswith(a):
            return [s[:-len(a)] + b]
        if s.endswith(b):
            return [s[:-len(b)] + a]
        return []
    return fn


def u_v(s):
    """Symmetric u<->v (ü) swap, but only after an ü-capable initial."""
    if not s or s[0] not in _UV_INITIALS:
        return []
    rest = s[1:]
    if "u" in rest:
        return [s[0] + rest.replace("u", "v")]
    if "v" in rest:
        return [s[0] + rest.replace("v", "u")]
    return []


def n_l(s):
    """Cantonese initial n<->l (excluding ng-initial, which has its own rule)."""
    if s.startswith("ng"):
        return []
    if s.startswith("n"):
        return ["l" + s[1:]]
    if s.startswith("l"):
        return ["n" + s[1:]]
    return []


def ng_omit(s):
    """Cantonese: drop an initial ng (我 ngo -> o)."""
    if s.startswith("ng") and len(s) > 2:
        return [s[2:]]
    return []


def gw_g(s):
    """Cantonese: drop the w after a labialized velar initial (gw->g, kw->k)."""
    if s.startswith("gw") and len(s) > 2:
        return ["g" + s[2:]]
    if s.startswith("kw") and len(s) > 2:
        return ["k" + s[2:]]
    return []


# Hepburn <-> Kunrei-shiki romaji equivalences (longest clusters first to avoid shadowing).
_JP_PAIRS = [
    ("shi", "si"), ("tsu", "tu"),
    ("sha", "sya"), ("shu", "syu"), ("sho", "syo"),
    ("cha", "tya"), ("chu", "tyu"), ("cho", "tyo"),
    ("ja", "zya"), ("ju", "zyu"), ("jo", "zyo"),
    ("chi", "ti"), ("ji", "zi"), ("fu", "hu"),
]

# A cluster starts at the beginning of the surface or after a vowel / 'n'. Match a Hepburn
# or Kunrei cluster only there, so that e.g. the "hu" inside "chuu" (c-h-u-u) is NOT treated
# as the standalone へ/ふ cluster. Implemented as a negated fixed-width lookbehind: not
# preceded by any consonant other than 'n'.
_CLUSTER_START = r"(?<![bcdfghjklmpqrstvwxyz])"


def _compile_pairs():
    compiled = []
    for h, k in _JP_PAIRS:
        compiled.append((re.compile(_CLUSTER_START + re.escape(h)), k,
                         re.compile(_CLUSTER_START + re.escape(k)), h))
    return compiled


_JP_COMPILED = _compile_pairs()


def hepburn_kunrei(s):
    """Symmetric Hepburn <-> Kunrei romaji confusion (cluster-boundary aware)."""
    out = []
    for rh, k, rk, h in _JP_COMPILED:
        if rh.search(s):
            out.append(rh.sub(k, s))
        if rk.search(s):
            out.append(rk.sub(h, s))
    return _dedup(out)


def long_vowel(s):
    """Collapse a long/doubled vowel (chuu->chu, too->to, tou->to). User-side merge."""
    out = []
    if "uu" in s:
        out.append(s.replace("uu", "u"))
    if "oo" in s:
        out.append(s.replace("oo", "o"))
    if "ou" in s:
        out.append(s.replace("ou", "o"))
    return _dedup(out)


def ae_e(s):
    """Korean: merge ae -> e (애 pronounced like 에 by most speakers). User-side merge."""
    if "ae" in s:
        return [s.replace("ae", "e")]
    return []


def l_r(s):
    """Korean: symmetric r <-> l swap. ㄹ is the ONLY Revised Romanization source of both
    'r' (initial/intervocalic ㄹ, e.g. 란 "ran") and 'l' (final/ㄹㄹ, e.g. 날 "nal"), so a
    global r/l swap stays confined to ㄹ-derived syllables -- this is the classic L/R input
    confusion (a user unsure whether to type "ra" or "la" for 라 gets either)."""
    out = []
    if "r" in s:
        out.append(s.replace("r", "l"))
    if "l" in s:
        out.append(s.replace("l", "r"))
    return _dedup(out)


# --------------------------------------------------------------------------- registry


class Rule:
    """A named fuzzy rule bound to one phonetic system."""

    __slots__ = ("name", "system", "variants_fn")

    def __init__(self, name, system, variants_fn):
        self.name = name
        self.system = system
        self.variants_fn = variants_fn  # Callable[[str], List[str]]

    def variants(self, surface):
        """Alternative surface strings (excludes the original)."""
        return self.variants_fn(surface)

    def surfaces(self, surface):
        """Full equivalence set: original + non-empty variants."""
        vs = {surface}
        for v in self.variants_fn(surface):
            if v and v != surface:
                vs.add(v)
        return vs


# The shared rule vocabulary. Java `dev.vfyjxf.phonin.fuzzy.FuzzyRules` mirrors these names.
RULES = [
    # Mandarin
    Rule("FUZZY_ZH_Z", System.MANDARIN, initial_swap("zh", "z")),
    Rule("FUZZY_CH_C", System.MANDARIN, initial_swap("ch", "c")),
    Rule("FUZZY_SH_S", System.MANDARIN, initial_swap("sh", "s")),
    Rule("FUZZY_ANG_AN", System.MANDARIN, final_swap("ang", "an")),
    Rule("FUZZY_ENG_EN", System.MANDARIN, final_swap("eng", "en")),
    Rule("FUZZY_ING_IN", System.MANDARIN, final_swap("ing", "in")),
    Rule("FUZZY_U_V", System.MANDARIN, u_v),
    # Cantonese
    Rule("FUZZY_N_L", System.CANTONESE, n_l),
    Rule("FUZZY_NG_OMIT", System.CANTONESE, ng_omit),
    Rule("FUZZY_GW_G", System.CANTONESE, gw_g),
    # Zhuyin (Bopomofo retroflex <-> dental)
    Rule("FUZZY_ZY_ZH_Z", System.ZHUYIN, initial_swap("ㄓ", "ㄗ")),
    Rule("FUZZY_ZY_CH_C", System.ZHUYIN, initial_swap("ㄔ", "ㄘ")),
    Rule("FUZZY_ZY_SH_S", System.ZHUYIN, initial_swap("ㄕ", "ㄙ")),
    # Japanese
    Rule("FUZZY_HEPBURN_KUNREI", System.JAPANESE, hepburn_kunrei),
    Rule("FUZZY_LONG_VOWEL", System.JAPANESE, long_vowel),
    # Korean: romaji-based fuzzy rules removed — Korean now uses 2-bulsik keyboard input.
    # Keyboard-specific fuzzy rules (adjacent-key errors, shift-key errors) may be added later.
]

BY_NAME = {r.name: r for r in RULES}


def rules_for(system) -> List[Rule]:
    return [r for r in RULES if r.system == system]
