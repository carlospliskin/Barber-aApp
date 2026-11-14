package com.barberia.app

import android.app.Application
import com.barberia.app.di.appModule
import com.barberia.feature.auth.data.di.authDataModule
import com.barberia.feature.auth.presentation.di.authPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BarberiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BarberiaApplication)
            modules(appModule, authDataModule, authPresentationModule)
        }
    }
}
