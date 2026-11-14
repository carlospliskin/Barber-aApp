plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(project(":core:network"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.serialization.json)
    implementation(libs.koin.android)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
