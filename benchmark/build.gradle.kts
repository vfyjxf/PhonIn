plugins {
    id("phonin.library")
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    implementation(project(":phonin-core"))
    implementation(rootProject.libs.fastutil)
    jmh(project(":phonin-core"))

    runtimeOnly(project(":phonin-mandarin"))
    runtimeOnly(project(":phonin-cantonese"))
    runtimeOnly(project(":phonin-zhuyin"))
    runtimeOnly(project(":phonin-japanese"))
    runtimeOnly(project(":phonin-korean"))
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    profilers.add("gc")
    timeOnIteration.set("5s")
    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("us")
}
