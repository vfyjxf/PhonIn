// The matching engine and the PhonIn entry point. Exposes the public API and the data loader;
// fastutil is an internal implementation detail. Optional bundled keyboards / routers are
// provided as testImplementation so core tests can exercise them without forcing them on consumers.
plugins {
    `java-library`
}

dependencies {
    api(project(":phonin-api"))
    api(project(":phonin-data"))
    implementation(rootProject.libs.fastutil)

    testImplementation(project(":phonin-systems"))
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.assertj)
    testImplementation(rootProject.libs.jackson.databind)
}

tasks.test {
    useJUnitPlatform()
    // The full case corpus is ~2.15M cases. Default to a 1% sample for fast `./gradlew test`;
    // run the full suite with `-Dphonin.case.sample=1.0` (or PHONIN_CASE_SAMPLE=1.0 env var).
    val sample = (System.getProperty("phonin.case.sample")
            ?: System.getenv("PHONIN_CASE_SAMPLE") ?: "0.01").toDoubleOrNull() ?: 0.01
    systemProperty("phonin.case.sample", sample.toString())
    // Bridge the perf-survey flag to the test JVM (PerfBenchmarkTest is skipped unless set).
    systemProperty("phonin.bench", System.getProperty("phonin.bench", "false"))
    // Allow the JSONL case streaming test + the 1M-name huge-scale benchmark enough memory.
    jvmArgs("-Xmx4g")
}

// Emit the test runtime classpath to a file so HeapProbe can be run directly with `java -cp`
// (for JProfiler heap analysis). Run: ./gradlew :phonin-core:printTestClasspath
tasks.register("printTestClasspath") {
    doLast {
        val cp = sourceSets.test.get().runtimeClasspath.asPath
        file("build/test.classpath").writeText(cp)
        println("wrote build/test.classpath")
    }
}
