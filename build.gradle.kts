// The matching engine targets Java 8.
// Per-module configuration (Spotless, Checkstyle, JaCoCo, publishing, JPMS manifest) lives in
// buildSrc/src/main/kotlin/phonin.library.gradle.kts and is applied by each module's own
// build.gradle.kts. The root only keeps project-wide coordinates and helper tasks.

group = "dev.vfyjxf.phonin"
version = "0.1"

// Build the dataset by running the Python pipeline under tools/.
tasks.register<Exec>("refreshDatasets") {
    group = "phonin"
    description = "Fetch all sources and (re)build the dataset under phonin-data/ via the Python pipeline."
    commandLine(
        "python3", "tools/build_dataset.py",
        "--out=phonin-data/src/main/resources/phonin",
        "--cache=build/downloads",
        "--unihan-version=16.0.0",
        "--mozillazg-commit=923b108dc5d45dee061324c011b478fb649f8b73"
    )
}
