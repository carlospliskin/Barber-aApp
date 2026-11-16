// Módulo Kotlin puro: reglas del catálogo (servicios, barberos, horarios).
plugins {
    alias(libs.plugins.barberia.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(project(":core:common"))
    api(libs.coroutines.core)
    testFixturesApi(libs.coroutines.core)
}
