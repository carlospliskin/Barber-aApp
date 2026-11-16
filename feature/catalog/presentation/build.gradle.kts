plugins { alias(libs.plugins.barberia.android.compose) }

dependencies {
    implementation(project(":feature:catalog:domain"))
    implementation(project(":feature:auth:domain")) // para saber de qué barbería es dueño el usuario
    implementation(project(":core:mvi"))
    implementation(project(":core:designsystem"))
    implementation(libs.koin.androidx.compose)

    testImplementation(testFixtures(project(":feature:catalog:domain")))
    testImplementation(testFixtures(project(":feature:auth:domain")))
    testImplementation(libs.turbine)
}
