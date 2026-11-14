package com.barberia.feature.auth.data.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.barberia.feature.auth.data.local.DataStoreSessionStorage
import com.barberia.feature.auth.data.local.SessionStorage
import com.barberia.feature.auth.data.remote.AuthRemoteDataSource
import com.barberia.feature.auth.data.repository.AuthRepositoryImpl
import com.barberia.feature.auth.domain.repository.AuthRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Requiere un `GraphQLClient` provisto por el módulo `app`. */
val authDataModule = module {
    single { AuthRemoteDataSource(client = get()) }
    single<SessionStorage> { DataStoreSessionStorage(androidContext().sessionDataStore) }
    single<AuthRepository> { AuthRepositoryImpl(remote = get(), storage = get()) }
}
