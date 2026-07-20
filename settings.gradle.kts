rootProject.name = "PhonIn"

// phonin-api: public interfaces, model classes and enums (no runtime dependencies).
// phonin-data: bundled datasets and the PhonInData loader.
// phonin-core: matching engine and PhonIn entry point (consumes api + data).
// phonin-systems: optional bundled keyboards (Korean, shuangpin) and the ByBlockRouter.
include(":phonin-api")
include(":phonin-data")
include(":phonin-core")
include(":phonin-systems")
include(":benchmark")
