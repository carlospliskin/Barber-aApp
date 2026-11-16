plugins { alias(libs.plugins.barberia.android.compose) }

dependencies {
    implementation(project(":feature:auth:domain"))
    implementation(project(":core:mvi"))
    implementation(project(":core:designsystem"))
    implementation(libs.koin.androidx.compose)

    testImplementation(testFixtures(project(":feature:auth:domain")))
    testImplementation(libs.turbine)
}
