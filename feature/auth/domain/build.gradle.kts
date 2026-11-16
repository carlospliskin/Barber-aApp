// Módulo Kotlin puro: sin dependencias de Android ni de Firebase.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":core:common"))
    api(libs.coroutines.core)

    // Fakes compartidos con los tests de data y presentation
    testFixturesApi(libs.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
