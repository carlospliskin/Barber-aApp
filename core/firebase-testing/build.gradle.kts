plugins { alias(libs.plugins.barberia.android.library) }

// Q1: solo para androidTest. Conecta el SDK real de Firebase a Emulator Suite (nunca a la nube).
dependencies {
    api(project(":core:firebase"))
    api(libs.coroutines.play.services)
    implementation(libs.coroutines.core)
    implementation(libs.androidx.test.runner) // InstrumentationRegistry
}
