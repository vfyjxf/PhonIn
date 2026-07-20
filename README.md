# PhonIn

PhonIn 是一个 Java 8+ 的音标/拼音/注音/罗马化字符串匹配库，支持 Mandarin Pinyin、Cantonese Jyutping、Bopomofo/Zhuyin、Japanese（kana/romaji）、Korean（Hangul / 2-bulsik）以及多种双拼方案。基于 [Towdium/PinIn](https://github.com/Towdium/PinIn) 的设计重写。

## 用法

```java
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.core.PhonIn;

PhonIn phonIn = PhonIn.create();
Options opts = Options.mandarinQuanpin();

phonIn.contains("中国", "zhongguo", opts); // true
phonIn.contains("中国", "zg", opts);       // true（简拼）
```

带模糊规则：

```java
import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.core.PhonIn;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;

PhonIn phonIn = PhonIn.create();
Options opts = Options.builder(PhoneticSystem.mandarin)
        .abbrev(AbbrevPolicy.FULL)
        .addFuzzy(FuzzyRules.fuzzyZhZ)
        .addFuzzy(FuzzyRules.fuzzyAngAn)
        .build();

phonIn.contains("重庆", "zongqing", opts); // true（zh -> z）
phonIn.contains("重庆", "cq", opts);       // true（简拼）
```

## 语言模块

`phonin-core` 本身不带任何语言数据；你需要显式依赖所需的语言模块，并在创建 `PhonIn` 时传入对应的 `PhoneticSystem`：

```kotlin
implementation("dev.vfyjxf.phonin:phonin-core:0.1")
implementation("dev.vfyjxf.phonin:phonin-mandarin:0.1")
```

```java
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.core.PhonIn;

PhonIn phonIn = PhonIn.create(PhoneticSystem.mandarin);
```

可用的语言模块：`phonin-mandarin`（含双拼键盘）、`phonin-cantonese`、`phonin-zhuyin`、`phonin-japanese`、`phonin-korean`（含 2-bulsik / choseong 键盘）。

## 致谢与数据源
- 灵感来源：[Towdium/PinIn](https://github.com/Towdium/PinIn)

数据集由 `tools/build_dataset.py` 从以下上游来源生成；完整许可文件见 `phonin-data/src/main/resources/phonin/LICENSES/`：

| Source | Used for | License |
|---|---|---|
| [Unicode Unihan 16.0.0](https://www.unicode.org/reports/tr38/) | Mandarin / Cantonese / Korean readings | Unicode Data Files agreement |
| [mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data) | Mandarin cross-check reference | MIT |
| [mozillazg/python-pinyin](https://github.com/mozillazg/python-pinyin) (pypinyin) | Mandarin supplement + Zhuyin derivation | MIT |
| [CC-CEDICT (MDBG)](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) | Mandarin word-level pinyin | CC BY-SA 4.0 |
| [KANJIDIC2 / JMDict (EDRDG)](https://www.edrdg.org/) | Japanese kanji/kana and word readings | EDRDG license |
| [AlienKevin/wordshk-tools](https://github.com/AlienKevin/wordshk-tools) | Cantonese char-level Jyutping | MIT |
| [words.hk](https://words.hk/) | Cantonese word-level data (user-supplied CSV drop-in) | Public domain |
| [rime/rime-double-pinyin](https://github.com/rime/rime-double-pinyin) | Shuangpin scheme algebras | BSD-3-Clause |

Korean Hangul romanization and 2-bulsik key sequences 为算法生成，未引入第三方韩语数据。
