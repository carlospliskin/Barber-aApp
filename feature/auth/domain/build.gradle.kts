// Módulo Kotlin puro: sin dependencias de Android ni de Firebase.
plugins {
    alias(libs.plugins.barberia.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(project(":core:common"))
    api(libs.coroutines.core)
    testFixturesApi(libs.coroutines.core) // fakes compartidos con data y presentation
}
