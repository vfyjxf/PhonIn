import io.phonin.build.NoInlineFqnTask
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

// The matching engine targets Java 8.
// Quality stack (applied to every java subproject): Spotless (formatting), Checkstyle
// (import hygiene + style), JaCoCo (coverage gate, only for phonin-core), and a custom task that
// bans inline fully-qualified class names. All are wired into `./gradlew check`.

group = "io.phonin"
version = "1.0.0-SNAPSHOT"

plugins {
    alias(libs.plugins.spotless) apply false
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    plugins.withId("java") {
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(8)
        }

        // ---- Spotless: deterministic formatting (google-java-format) ------------
        apply(plugin = "com.diffplug.spotless")
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                target("src/*/java/**/*.java")
                // AOSP = 4-space indent (matches the project's cloudlib-derived style).
                googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }

        // ---- Checkstyle: import hygiene + light style --------------------------
        apply(plugin = "checkstyle")
        configure<CheckstyleExtension> {
            toolVersion = libs.versions.checkstyleTool.get()
            configDirectory = rootProject.file("config/checkstyle")
        }

        // ---- JaCoCo: coverage gate ---------------------------------------------
        apply(plugin = "jacoco")
        configure<JacocoPluginExtension> {
            toolVersion = libs.versions.jacoco.get()
        }
        tasks.withType<JacocoReport>().configureEach {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
        tasks.withType<JacocoCoverageVerification>().configureEach {
            // Bundle-level gate (whole module). WordEntry is unused until Phase 2C word
            // matching; its few uncovered lines do not lower the bundle below this bar, but
            // any sizable untested class added later will fail the build.
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = "0.85".toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        minimum = "0.70".toBigDecimal()
                    }
                }
            }
        }

        // ---- ban inline fully-qualified class names (use an import instead) -----
        val banFullyQualifiedNames = tasks.register<NoInlineFqnTask>("banFullyQualifiedNames") {
            sources.from(fileTree("src") { include("**/*.java") })
        }

        tasks.named("check") {
            dependsOn("jacocoTestCoverageVerification", banFullyQualifiedNames)
        }

        // Only phonin-core has a test suite and coverage gate; other modules are interfaces,
        // data, or optional add-ons.
        if (project.name !in setOf("phonin-core")) {
            tasks.withType<JacocoReport>().configureEach { enabled = false }
            tasks.withType<JacocoCoverageVerification>().configureEach { enabled = false }
        }

        // ---- Maven publish: make the library available to local Maven ---------------
        apply(plugin = "maven-publish")
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
            repositories {
                maven {
                    name = "local"
                    url = uri(rootProject.file("../jech-new-exp/maven"))
                }
            }
        }
    }
}

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
