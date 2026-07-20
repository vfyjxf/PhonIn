// Builds the custom Gradle tasks and the shared phonin.library convention plugin.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Make the Spotless plugin classes available to the precompiled convention plugin.
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
}
