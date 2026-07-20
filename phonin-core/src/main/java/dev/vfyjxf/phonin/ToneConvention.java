package dev.vfyjxf.phonin;

/**
 * How a system marks tone on its normalized reading, so the toneless syllable (the default
 * matchable surface) can be recovered. A property of {@link PhoneticSystem}, not a per-system
 * switch elsewhere: the predefined systems carry the convention they need, and a custom system
 * supplies its own.
 */
public enum ToneConvention {

    /**
     * No tone marking (Japanese romaji, Korean Revised Romanization). Surface = normalized.
     */
    NONE {
        @Override
        public String strip(String normalized) {
            return normalized;
        }
    },

    /**
     * A trailing ASCII digit carries the tone (Mandarin 1-5, Cantonese 1-9).
     */
    DIGIT {
        @Override
        public String strip(String normalized) {
            if (normalized.isEmpty()) return normalized;
            char last = normalized.charAt(normalized.length() - 1);
            return Character.isDigit(last)
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        }
    },

    /**
     * Bopomofo trailing tone marks ˊ ˇ ˋ ˙ (and a leading ˙).
     */
    ZHUYIN {
        @Override
        public String strip(String normalized) {
            if (normalized.isEmpty()) return normalized;
            String s = normalized;
            char last = s.charAt(s.length() - 1);
            if (last == 'ˊ' || last == 'ˇ' || last == 'ˋ' || last == '˙') {
                s = s.substring(0, s.length() - 1);
            }
            if (!s.isEmpty() && s.charAt(0) == '˙') s = s.substring(1);
            return s;
        }
    };

    /**
     * Recover the toneless syllable from a normalized reading.
     */
    public abstract String strip(String normalized);
}
