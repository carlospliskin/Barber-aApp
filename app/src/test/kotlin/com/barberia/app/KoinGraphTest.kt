package com.barberia.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.barberia.app.di.appModule
import com.barberia.app.di.authDomainModule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.barberia.feature.auth.data.di.authDataModule
import com.barberia.feature.auth.presentation.di.authPresentationModule
import com.barberia.feature.catalog.data.di.catalogDataModule
import com.barberia.feature.catalog.presentation.catalogPresentationModule
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Verifica que cada clase registrada en Koin pueda construirse con lo que hay en el grafo.
 * Si alguien agrega una dependencia y olvida registrarla, falla aquí y no en el teléfono.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {
    @Test
    fun `el grafo de dependencias esta completo`() {
        module {
            includes(
                appModule,
                authDomainModule,
                authDataModule,
                authPresentationModule,
                catalogDataModule,
                catalogPresentationModule,
            )
        }.verify(extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                CoroutineScope::class,
                // Los provee core:firebase con las fábricas del SDK (no se revisan aquí).
                FirebaseAuth::class,
                FirebaseFirestore::class,
            ),)
    }
}
