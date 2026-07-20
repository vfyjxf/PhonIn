// Bundled dataset resources and the PhonInData loader. Depends only on the public API.
plugins {
    `java-library`
}

dependencies {
    api(project(":phonin-api"))
}
