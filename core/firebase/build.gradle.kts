plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.barberia.core.firebase"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.firebase.firestore)
    implementation(libs.koin.android)
}
