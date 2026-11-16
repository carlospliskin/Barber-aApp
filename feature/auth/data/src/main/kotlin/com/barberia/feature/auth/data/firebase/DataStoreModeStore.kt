package com.barberia.feature.auth.data.firebase

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.barberia.feature.auth.data.gateway.ModeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionPrefs by preferencesDataStore(name = "session_prefs")

internal class DataStoreModeStore(private val context: Context) : ModeStore {
    override val mode: Flow<String?> = context.sessionPrefs.data.map { it[KEY] }

    override suspend fun save(mode: String?) {
        context.sessionPrefs.edit { prefs -> if (mode == null) prefs.remove(KEY) else prefs[KEY] = mode }
    }

    private companion object {
        val KEY = stringPreferencesKey("selected_mode")
    }
}
