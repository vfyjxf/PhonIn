// Public API, model classes and enums. Fastutil is used only for internal PhoneticSystem storage;
// the engine, data, and optional keyboards are not pulled in.
plugins {
    `java-library`
}

dependencies {
    implementation(rootProject.libs.fastutil)
}
