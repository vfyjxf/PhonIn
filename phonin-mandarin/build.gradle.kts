// Mandarin module: character data + shuangpin keyboard presets and keymaps.
plugins {
    id("phonin.library")
}

dependencies {
    api(project(":phonin-core"))
}

sourceSets {
    main {
        resources {
            srcDir(rootProject.file("phonin-data/src/main/resources"))
            include("phonin/raw/mandarin-char.tsv")
            include("phonin/keymaps/shuangpin-*.tsv")
        }
    }
}
