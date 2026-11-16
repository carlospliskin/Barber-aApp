plugins { alias(libs.plugins.barberia.android.library) }

dependencies {
    implementation(project(":feature:catalog:domain"))
    implementation(project(":core:firebase"))
    implementation(libs.coroutines.play.services)
    implementation(libs.koin.android)
}

dependencies {
    androidTestImplementation(project(":core:firebase-testing")) // Q1
}
