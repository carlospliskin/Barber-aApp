plugins { alias(libs.plugins.barberia.android.library) }

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.firebase.firestore)
    api(libs.firebase.crashlytics)
    implementation(libs.koin.android)
}

dependencies {
    implementation(libs.coroutines.core)
}
