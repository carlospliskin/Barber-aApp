plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.barberia.feature.auth.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":feature:auth:domain"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.android)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
