plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.barberia.core.mvi"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
