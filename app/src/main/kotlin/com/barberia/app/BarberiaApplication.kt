package com.barberia.app

import android.app.Application
import com.barberia.app.di.appModule
import com.barberia.app.di.authDomainModule
import com.barberia.core.firebase.FirebaseConfig
import com.barberia.core.firebase.firebaseModule
import com.barberia.feature.auth.data.di.authDataModule
import com.barberia.feature.auth.presentation.di.authPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BarberiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val firebase = FirebaseConfig(
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            applicationId = BuildConfig.FIREBASE_APP_ID,
            apiKey = BuildConfig.FIREBASE_API_KEY,
            emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST.ifBlank { null },
        )
        startKoin {
            androidContext(this@BarberiaApplication)
            modules(firebaseModule(firebase), appModule, authDomainModule, authDataModule, authPresentationModule)
        }
    }
}
