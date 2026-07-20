// Resource-only module: shared test datasets, legal notices and polyphone/regression tables.
// Per-language raw tables and keymaps are published in the :phonin-<system> modules.
plugins {
    id("phonin.library")
}

sourceSets {
    main {
        resources {
            // Raw character tables ship in per-language modules (:phonin-mandarin, etc.).
            // Word tables (raw/*-word.tsv) are not packaged until Phase 2C word matching is wired up.
            exclude("phonin/raw/*.tsv")
            // Generated test corpora are gitignored and should not be packaged.
            exclude("phonin/generated/**")
            // Keymaps, golden fuzzy tables, regression cases and polyphone samples are
            // shipped by the modules/tests that consume them.
            exclude("phonin/keymaps/**")
            exclude("phonin/fuzzy/**")
            exclude("phonin/polyphone/**")
            exclude("phonin/regression/**")
            // Hidden macOS metadata.
            exclude("**/.DS_Store")
        }
    }
}
