plugins { alias(libs.plugins.barberia.android.compose) }

dependencies {
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.coroutines.android)
    implementation(libs.compose.ui)
    testImplementation(libs.turbine)
}
