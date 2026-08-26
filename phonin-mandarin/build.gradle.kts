// Mandarin module: character data + shuangpin keyboard presets and keymaps.
plugins {
    id("phonin.library")
}

dependencies {
    api(project(":phonin-core"))

    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        resources {
            // mandarin-char.tsv comes from phonin-data; the shuangpin keymaps are this module's
            // own resources (written by refreshDatasets --keymaps-out).
            srcDir(rootProject.file("phonin-data/src/main/resources"))
            include("phonin/raw/mandarin-char.tsv")
            include("phonin/keymaps/shuangpin-*.tsv")
        }
    }
}
