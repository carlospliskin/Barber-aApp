import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.firebase.crashlytics)
}

// Release: valores reales de Firebase en `firebase.properties` (fuera de git). Ver docs/FIREBASE.md.
val firebaseProps = Properties().apply {
    rootProject.file("firebase.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun firebase(key: String) = "\"${firebaseProps.getProperty(key, "")}\""

android {
    namespace = "com.barberia.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.barberia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "CRASH_REPORTS", "false")
            // Proyecto demo + Emulator Suite: no existe en la nube, no gasta cuota.
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"demo-barberia\"")
            buildConfigField("String", "FIREBASE_APP_ID", "\"1:000000000000:android:0000000000000000\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"demo-api-key\"")
            // 10.0.2.2 = la Mac vista desde el emulador de Android
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"10.0.2.2\"")
        }
        release {
            isMinifyEnabled = true
            // Subir el mapa de R8 necesita el proyecto real de Firebase (L1). Se activa entonces.
            (this as ExtensionAware).extensions.configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
            buildConfigField("boolean", "CRASH_REPORTS", "true")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "FIREBASE_PROJECT_ID", firebase("projectId"))
            buildConfigField("String", "FIREBASE_APP_ID", firebase("appId"))
            buildConfigField("String", "FIREBASE_API_KEY", firebase("apiKey"))
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        }
        // QA: el mismo build que release (R8 activado) pero contra el emulador de Firebase.
        // Sirve para encontrar problemas de R8 antes de publicar.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            buildConfigField("boolean", "CRASH_REPORTS", "false")
            (this as ExtensionAware).extensions.configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false // initWith no copia esta extensión
            }
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"demo-barberia\"")
            buildConfigField("String", "FIREBASE_APP_ID", "\"1:000000000000:android:0000000000000000\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"demo-api-key\"")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"10.0.2.2\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:mvi"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:firebase"))
    implementation(platform(libs.firebase.bom))
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    implementation(project(":feature:auth:domain"))
    implementation(project(":feature:auth:data"))
    implementation(project(":feature:auth:presentation"))
    implementation(project(":feature:catalog:domain"))
    implementation(project(":feature:catalog:data"))
    implementation(project(":feature:catalog:presentation"))
    implementation(project(":feature:booking:domain"))
    implementation(project(":feature:booking:data"))
    implementation(project(":feature:booking:presentation"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    debugImplementation(libs.compose.ui.tooling)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
}
