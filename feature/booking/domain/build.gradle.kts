// Módulo Kotlin puro: disponibilidad y reglas de reserva.
plugins {
    alias(libs.plugins.barberia.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(project(":core:common"))
    api(project(":feature:catalog:domain"))
    api(libs.coroutines.core)
    testFixturesApi(libs.coroutines.core)
}

dependencies {
    testImplementation(testFixtures(project(":feature:catalog:domain")))
}
