package com.barberia.feature.booking.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.barberia.feature.booking.domain.repository.JoinedBarbershopStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bookingPrefs by preferencesDataStore(name = "booking_prefs")

/** R8/U3: la barbería elegida vive en el teléfono, una por usuario (0 costo en Firestore). */
internal class DataStoreJoinedStore(private val context: Context) : JoinedBarbershopStore {
    private fun key(uid: String) = stringPreferencesKey("joined_tenant_$uid")

    override fun tenantId(uid: String): Flow<String?> = context.bookingPrefs.data.map { it[key(uid)] }

    override suspend fun save(uid: String, tenantId: String?) {
        context.bookingPrefs.edit { if (tenantId == null) it.remove(key(uid)) else it[key(uid)] = tenantId }
    }
}
