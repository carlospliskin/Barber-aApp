package com.barberia.feature.auth.data.di

import com.barberia.feature.auth.data.firebase.DataStoreModeStore
import com.barberia.feature.auth.data.firebase.FirebaseAuthGateway
import com.barberia.feature.auth.data.firebase.FirestoreProfileGateway
import com.barberia.feature.auth.data.gateway.AuthGateway
import com.barberia.feature.auth.data.gateway.ModeStore
import com.barberia.feature.auth.data.gateway.ProfileGateway
import com.barberia.feature.auth.data.repository.FirebaseAccountRepository
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/** Scope de toda la app (lo provee `app`). La sesión vive aquí, no en una pantalla. */
val ApplicationScope = named("application_scope")

/** Requiere `FirebaseAuth` y `FirebaseFirestore` (módulo core:firebase). */
val authDataModule = module {
    single<AuthGateway> { FirebaseAuthGateway(get()) }
    single<ProfileGateway> { FirestoreProfileGateway(get()) }
    single<ModeStore> { DataStoreModeStore(androidContext()) }
    single {
        FirebaseAccountRepository(get(), get(), get(), get<CoroutineScope>(ApplicationScope))
    } binds arrayOf(AuthRepository::class, SessionRepository::class)
}
