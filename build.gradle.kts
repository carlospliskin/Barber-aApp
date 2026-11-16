// Root: declara plugins sin aplicarlos, salvo el estilo (Q3), que va en todos los módulos.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false

    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kotlinter) apply false
}

// Q3: ktlint (vía kotlinter) en cada módulo. `./gradlew lintKotlin` revisa, `formatKotlin` corrige.
// Reglas en .editorconfig. detekt queda fuera hasta que tenga versión estable para Kotlin 2.4.
subprojects {
    apply(plugin = "org.jmailen.kotlinter")
}
