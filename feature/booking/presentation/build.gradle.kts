plugins { alias(libs.plugins.barberia.android.compose) }

dependencies {
    implementation(project(":feature:booking:domain"))
    implementation(project(":feature:catalog:domain"))
    implementation(project(":feature:auth:domain")) // usuario actual (uid, nombre, barbería del dueño)
    implementation(project(":core:mvi"))
    implementation(project(":core:designsystem"))
    implementation(libs.koin.androidx.compose)

    testImplementation(testFixtures(project(":feature:booking:domain")))
    testImplementation(testFixtures(project(":feature:catalog:domain")))
    testImplementation(testFixtures(project(":feature:auth:domain")))
    testImplementation(libs.turbine)
}
