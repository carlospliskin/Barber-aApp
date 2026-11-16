package com.barberia.feature.booking.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * R8: barbería elegida, guardada en el teléfono (sin costo en Firestore).
 * U3: es de cada usuario; otra cuenta en el mismo teléfono no la hereda.
 */
interface JoinedBarbershopStore {
    fun tenantId(uid: String): Flow<String?>
    suspend fun save(uid: String, tenantId: String?)
}
