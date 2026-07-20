rootProject.name = "PhonIn"

// phonin-core: public API + matching engine + PhonIn entry point + PhonInData loader.
// phonin-data: resource-only module with shared test datasets and legal notices.
// phonin-<system>: per-language module; ships the character table and any language-specific
// keyboards / option presets (e.g. shuangpin for Mandarin, 2-bulsik for Korean).
include(":phonin-data")
include(":phonin-mandarin")
include(":phonin-cantonese")
include(":phonin-zhuyin")
include(":phonin-japanese")
include(":phonin-korean")
include(":phonin-core")
include(":benchmark")
