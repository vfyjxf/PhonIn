// Korean module: character data + 2-bulsik / choseong keyboard presets.
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
            include("phonin/raw/korean-char.tsv")
        }
    }
}
