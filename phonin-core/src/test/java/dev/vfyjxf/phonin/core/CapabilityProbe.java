package dev.vfyjxf.phonin.core;

import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;

/**
 * Ad-hoc capability probe (not a unit test): prints whether the engine matches a matrix of (text,
 * query, Options) — documents the 简拼 (full-initial) semantics and its composition with fuzzy. Run
 * via {@code java -cp ... dev.vfyjxf.phonin.CapabilityProbe}.
 */
public class CapabilityProbe {

    public static void main(String[] args) {
        PhonIn p = PhonIn.create();
        Options quanpin = Options.mandarinQuanpin(); // abbrev OFF
        Options abbrev =
                Options.builder(PhoneticSystem.mandarin).abbrev(AbbrevPolicy.INITIALS).build();
        Options abbrevFuzzy =
                Options.builder(PhoneticSystem.mandarin)
                        .abbrev(AbbrevPolicy.INITIALS)
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();

        System.out.println("=== 中国 (zhong guo) — 简拼 = zhg ===");
        row(p, "中国", "zhongguo", quanpin, abbrev); // full
        row(p, "中国", "zhg", quanpin, abbrev); // 简拼: 中=zh + 国=g  (abbrev=T)
        row(p, "中国", "zg", quanpin, abbrev); // tightened: 中 no longer matches bare 'z'
        System.out.printf(
                "    中国  zg  +FUZZY_ZH_Z = %s (中→zong variant initial)%n",
                p.contains("中国", "zg", abbrevFuzzy));

        System.out.println("=== 安山岩 (an shan yan) — 简拼 = anshy ===");
        row(p, "安山岩", "anshanyan", quanpin, abbrev); // full
        row(p, "安山岩", "anshy", quanpin, abbrev); // 简拼: 安=an(zero-initial,full) + 山=sh + 岩=y
        row(p, "安山岩", "ashanyan", quanpin, abbrev); // dropped-n typo: 安 must be "an" -> rejected
    }

    private static void row(PhonIn p, String text, String query, Options q, Options a) {
        System.out.printf(
                "%-6s %-12s quanpin=%-5s abbrev=%-5s%n",
                text, query, p.contains(text, query, q), p.contains(text, query, a));
    }
}
