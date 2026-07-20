"""Algorithmic Korean Hangul -> 2-bulsik keyboard key sequences.

Covers all 11,172 precomposed modern Hangul syllables (U+AC00..U+D7A3). Each
syllable decomposes into choseong (initial), jungseong (medial), jongseong
(final) which are mapped to their 2-bulsik (두벌식) QWERTY key positions and
concatenated. Shifted keys (tensed consonants ㅃㅉㄸㄲㅆ and compound vowels
ㅒㅖ) are represented as uppercase letters.

The standard 2-bulsik layout maps QWERTY keys to jamo as follows:

  Consonants (choseong/jongseong):
    q=ㅂ w=ㅈ e=ㄷ r=ㄱ t=ㅅ a=ㅁ s=ㄴ d=ㅇ f=ㄹ g=ㅎ
    z=ㅋ x=ㅌ c=ㅊ v=ㅍ
    Q=ㅃ W=ㅉ E=ㄸ R=ㄲ T=ㅆ

  Vowels (jungseong):
    k=ㅏ o=ㅐ i=ㅑ j=ㅓ p=ㅔ u=ㅕ
    h=ㅗ y=ㅛ n=ㅜ b=ㅠ m=ㅡ l=ㅣ
    O=ㅒ P=ㅖ
    Compound: hk=ㅘ ho=ㅙ hl=ㅚ nj=ㅝ np=ㅞ nl=ㅟ ml=ㅢ

  Compound jongseong (typed as consecutive consonant keys):
    ㄳ=rt ㄵ=sw ㄶ=sg ㄺ=fr ㄻ=fa ㄼ=fq ㄽ=ft ㄾ=fx ㄿ=fv ㅀ=fg ㅄ=qt
"""

# Choseong (19 initial consonants) → 2-bulsik key
_CHO_KEY = ["r", "R", "s", "e", "E", "f", "a", "q", "Q", "t", "T",
            "d", "w", "W", "c", "z", "x", "v", "g"]

# Jungseong (21 vowels) → 2-bulsik key sequence
_JUNG_KEY = ["k", "o", "i", "O", "j", "p", "u", "P", "h", "hk", "ho", "hl",
             "y", "n", "nj", "np", "nl", "b", "m", "ml", "l"]

# Jongseong (28 finals, index 0 = none) → 2-bulsik key sequence
# Compound jongseong are typed as consecutive consonant keys.
_JONG_KEY = ["", "r", "R", "rt", "s", "sw", "sg", "e", "f", "fr", "fa", "fq",
             "ft", "fx", "fv", "fg", "a", "q", "qt", "t", "T", "d", "w", "c",
             "z", "x", "v", "g"]

HANGUL_START = 0xAC00
HANGUL_END = 0xD7A3


def is_hangul_syllable(cp: int) -> bool:
    return HANGUL_START <= cp <= HANGUL_END


def syllable_to_keyboard(cp: int) -> str:
    """Convert a precomposed Hangul codepoint to its 2-bulsik keyboard key sequence."""
    s = cp - HANGUL_START
    l = s // 588
    v = (s % 588) // 28
    t = s % 28
    return _CHO_KEY[l] + _JUNG_KEY[v] + _JONG_KEY[t]


def hangul_to_keyboard(text: str) -> str:
    """Convert a string of Hangul syllables to 2-bulsik keyboard key sequences."""
    out = []
    for ch in text:
        cp = ord(ch)
        if is_hangul_syllable(cp):
            out.append(syllable_to_keyboard(cp))
        else:
            out.append(ch)
    return "".join(out)


def all_syllables():
    """Yield (codepoint, hangul, hangul) for every precomposed Hangul syllable.

    The third element is the normalized form — now Hangul itself (the canonical form stored in
    the dataset). The Java KoreanKeyboard re-maps it to the appropriate key sequence at match
    time. This mirrors how Mandarin stores pinyin as the canonical form and ShuangpinKeyboard
    re-maps it to 2-key codes.
    """
    for cp in range(HANGUL_START, HANGUL_END + 1):
        yield cp, chr(cp), chr(cp)
