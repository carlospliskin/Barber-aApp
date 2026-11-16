plugins { alias(libs.plugins.barberia.android.library) }

dependencies {
    implementation(project(":feature:booking:domain"))
    implementation(project(":core:firebase"))
    implementation(libs.coroutines.play.services)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
}
