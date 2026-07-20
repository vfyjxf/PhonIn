import dev.vfyjxf.phonin.gradle.NoInlineFqnTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

// Pull versions from the root version catalog so they stay in one place.
val toml = rootProject.file("gradle/libs.versions.toml").readText()
fun version(name: String): String {
    val regex = Regex("""^$name\s*=\s*"([^"]+)""", RegexOption.MULTILINE)
    return regex.find(toml)?.groupValues?.get(1)
        ?: throw GradleException("Version '$name' not found in libs.versions.toml")
}

val googleJavaFormat = version("googleJavaFormat")
val checkstyleToolVersion = version("checkstyleTool")
val jacocoVersion = version("jacoco")

configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Automatic-Module-Name" to
                "${rootProject.group}.${project.name.replace("phonin-", "").replace("-", ".")}"
        )
    }
}

//region Spotless: deterministic formatting (google-java-format)
apply(plugin = "com.diffplug.spotless")
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/*/java/**/*.java")
        googleJavaFormat(googleJavaFormat).aosp().formatJavadoc(false)
        replace("region marker", "// region", "//region")
        replace("endregion marker", "// endregion", "//endregion")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

//endregion
//region Checkstyle: import hygiene + light style
apply(plugin = "checkstyle")
configure<CheckstyleExtension> {
    toolVersion = checkstyleToolVersion
    configDirectory = rootProject.file("config/checkstyle")
}

//endregion
//region JaCoCo: coverage gate
apply(plugin = "jacoco")
configure<JacocoPluginExtension> {
    toolVersion = jacocoVersion
}
tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.withType<JacocoCoverageVerification>().configureEach {
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

if (project.name != "phonin-core") {
    tasks.withType<JacocoReport>().configureEach { enabled = false }
    tasks.withType<JacocoCoverageVerification>().configureEach { enabled = false }
}

//endregion
//region ban inline fully-qualified class names (use an import instead)
val banFullyQualifiedNames = tasks.register<NoInlineFqnTask>("banFullyQualifiedNames") {
    sources.from(fileTree("src") { include("**/*.java") })
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification", banFullyQualifiedNames)
}

//endregion
//region Maven publish: make every library available to local Maven / JitPack
if (project.name != "benchmark") {
    apply(plugin = "maven-publish")
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("PhonIn ${project.name} module")
                    url.set("https://github.com/vfyjxf/PhonIn")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                }
            }
        }
        repositories {
            mavenLocal()
        }
    }
}
//endregion
