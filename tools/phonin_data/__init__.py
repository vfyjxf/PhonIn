"""PhonIn dataset generator (Python pipeline).

In-memory model shared by all source parsers, derivers, emitters, and case generators.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Set


class System(str, Enum):
    MANDARIN = "MANDARIN"
    CANTONESE = "CANTONESE"
    ZHUYIN = "ZHUYIN"
    JAPANESE = "JAPANESE"
    KOREAN = "KOREAN"


class Source(str, Enum):
    UNIHAN = "UNIHAN"
    MOZILLAZG = "MOZILLAZG"
    KANJIDIC = "KANJIDIC"
    JMDICT = "JMDICT"
    WANAKANA = "WANAKANA"
    CEDICT = "CEDICT"
    WORDSHK = "WORDSHK"
    WORDSHK_CHARS = "WORDSHK_CHARS"
    PYPINYIN = "PYPINYIN"
    HANGUL = "HANGUL"
    HAND = "HAND"
    DERIVED = "DERIVED"


@dataclass
class Reading:
    system: "System"
    display: str          # human form: diacritic pinyin "zhōng", kana "チュウ"
    normalized: str       # machine form: numeric "zhong1", romaji "chuu"
    tone: int             # 0 if none/neutral
    syllable: str         # toneless core: "zhong", "chu"
    freq: int = 0         # frequency weight (kHanyuPinlu), 0 if unknown

    def key(self):
        return (self.system, self.normalized)


@dataclass
class CharEntry:
    codepoint: int
    readings: Dict["System", List[Reading]] = field(default_factory=dict)
    sources: Set["Source"] = field(default_factory=set)

    @property
    def character(self) -> str:
        return chr(self.codepoint)

    def add_reading(self, r: Reading) -> None:
        lst = self.readings.setdefault(r.system, [])
        for x in lst:
            if x.normalized == r.normalized:
                if r.freq and (not x.freq or r.freq > x.freq):
                    x.freq = r.freq
                return
        lst.append(r)


@dataclass
class WordEntry:
    codepoints: List[int]
    text: str
    system: "System"
    readings: List[Reading]
    source: "Source"


@dataclass
class DataSet:
    chars: Dict[int, CharEntry] = field(default_factory=dict)
    words: List[WordEntry] = field(default_factory=list)

    def char(self, codepoint: int) -> CharEntry:
        return self.chars.setdefault(codepoint, CharEntry(codepoint))

    def count_chars(self, system: "System") -> int:
        return sum(1 for e in self.chars.values() if system in e.readings)
