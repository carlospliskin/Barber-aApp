package com.barberia.feature.auth.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal data class StoredSession(val token: String, val refreshToken: String)

/** Abstracción del almacenamiento de tokens (permite cambiar a uno cifrado sin tocar el repositorio). */
internal interface SessionStorage {
    suspend fun read(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

// TODO(seguridad): cifrar los tokens con Android Keystore antes de producción.
internal class DataStoreSessionStorage(private val store: DataStore<Preferences>) : SessionStorage {

    override suspend fun read(): StoredSession? {
        val prefs = store.data.first()
        val token = prefs[TOKEN] ?: return null
        return StoredSession(token, prefs[REFRESH].orEmpty())
    }

    override suspend fun save(session: StoredSession) {
        store.edit {
            it[TOKEN] = session.token
            it[REFRESH] = session.refreshToken
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("auth_token")
        val REFRESH = stringPreferencesKey("auth_refresh_token")
    }
}
