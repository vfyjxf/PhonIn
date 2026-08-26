import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// The matching engine and the PhonIn entry point. Exposes the public API and the data loader;
// phonin-core itself does not pull any language data, so consumers pick the :phonin-<system>
// modules they need and pass them in explicitly.
plugins {
    id("phonin.library")
    alias(libs.plugins.shadow)
}

// Shadowed / fat-jar variant: bundles phonin-core with its runtime dependencies and relocates
// fastutil so consumers are not exposed to the original package.

tasks.withType<ShadowJar>().configureEach {
    // Use a separate artifactId, not a classifier, to distinguish the shadow variant.
    archiveBaseName.set("${project.name}-all")
    archiveClassifier.set("")
    // Relocate all fastutil classes into a private namespace to avoid classpath conflicts.
    relocate("it.unimi.dsi.fastutil", "dev.vfyjxf.phonin.shadow.fastutil")
    // Strip unused classes from the bundled dependencies, keeping only reachable fastutil types.
    minimize()
}

// Publish the shadow jar as a separate 'all' artifact with no transitive dependencies.
publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifactId = "${project.name}-all"
            project.extensions.configure<ShadowExtension>("shadow") {
                component(this@create)
            }
        }
    }
}

afterEvaluate {
    // The Shadow plugin adds shadowRuntimeElements to the java component; keep it out of the
    // default 'maven' publication so the normal artifact is not duplicated. The standalone
    // shadow publication below owns the phonin-core-all artifact.
    val shadowRuntimeElements = configurations.findByName("shadowRuntimeElements")
    if (shadowRuntimeElements != null) {
        val adhoc = components["java"] as org.gradle.api.component.AdhocComponentWithVariants
        adhoc.withVariantsFromConfiguration(shadowRuntimeElements) {
            skip()
        }
    }
}

dependencies {
    implementation(rootProject.libs.fastutil)

    // Core tests exercise Mandarin (shuangpin) and Korean keyboards, plus all five datasets.
    testImplementation(project(":phonin-mandarin"))
    testImplementation(project(":phonin-korean"))
    testRuntimeOnly(project(":phonin-cantonese"))
    testRuntimeOnly(project(":phonin-zhuyin"))
    testRuntimeOnly(project(":phonin-japanese"))

    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.assertj)
    testImplementation(rootProject.libs.jackson.databind)
}

sourceSets {
    test {
        resources {
            srcDir(rootProject.file("phonin-data/src/main/resources"))
            include("phonin/fuzzy/**")
            include("phonin/polyphone/**")
            include("phonin/regression/**")
        }
    }
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
    // The generated corpus is gitignored and huge (~860MB): stream it from the source dir
    // instead of copying it through processTestResources. Absent -> CaseRunnerTest skips it.
    classpath += files(rootProject.file("phonin-data/src/main/resources"))
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
