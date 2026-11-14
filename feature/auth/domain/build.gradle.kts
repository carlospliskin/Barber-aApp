// Módulo Kotlin puro: sin dependencias de Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":core:common"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
