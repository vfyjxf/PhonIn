// Optional bundled systems support: Korean / shuangpin keyboards and the ByBlock router.
// Consumers who only need one language can skip this module and its classpath resources.
plugins {
    `java-library`
}

dependencies {
    api(project(":phonin-api"))

    // Tests exercise the keyboards through the PhonIn engine.
    testImplementation(project(":phonin-core"))
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.assertj)
}
