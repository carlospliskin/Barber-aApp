// Módulo Kotlin puro: sin dependencias de Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
