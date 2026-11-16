package com.barberia.app

import android.app.Application
import android.util.Log
import com.barberia.app.di.appModule
import com.barberia.app.di.authDomainModule
import com.barberia.core.firebase.FirebaseConfig
import com.barberia.core.firebase.firebaseModule
import com.barberia.core.mvi.MviErrors
import com.barberia.feature.auth.data.di.authDataModule
import com.barberia.feature.auth.presentation.di.authPresentationModule
import com.barberia.feature.booking.data.di.bookingDataModule
import com.barberia.feature.booking.presentation.bookingPresentationModule
import com.barberia.feature.catalog.data.di.catalogDataModule
import com.barberia.feature.catalog.presentation.catalogPresentationModule
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
            crashReports = BuildConfig.CRASH_REPORTS,
            appCheckInstaller = appCheckInstaller() // por build type: src/release, src/debug, src/qa
        )
        // Errores inesperados de pantallas: no cierran la app; en release se reportan como no fatales.
        MviErrors.reporter = if (BuildConfig.CRASH_REPORTS) {
            { FirebaseCrashlytics.getInstance().recordException(it) }
        } else {
            // debug/QA: que se vea en logcat (antes se tragaba en silencio y un bug pasaba desapercibido)
            { Log.e("Barberia", "Error inesperado en una pantalla", it) }
        }
        startKoin {
            androidContext(this@BarberiaApplication)
            modules(
                firebaseModule(firebase),
                appModule,
                authDomainModule,
                authDataModule,
                authPresentationModule,
                catalogDataModule,
                catalogPresentationModule,
                bookingDataModule,
                bookingPresentationModule
            )
        }
    }
}
