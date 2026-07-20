// Cantonese module: character data.
plugins {
    id("phonin.library")
}

sourceSets {
    main {
        resources {
            srcDir(rootProject.file("phonin-data/src/main/resources"))
            include("phonin/raw/cantonese-char.tsv")
        }
    }
}
