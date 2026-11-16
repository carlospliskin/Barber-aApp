package com.barberia.feature.booking.data.gateway

import kotlinx.coroutines.flow.Flow

/** El día ya tenía alguno de los bloques pedidos (B3). */
internal class SlotTakenException : Exception()

/** Acceso crudo a Firestore para reservas. Los instantes viajan como `java.time.Instant`. */
internal interface BookingGateway {
    suspend fun getDay(tenantId: String, dayId: String): Map<String, Any?>?

    /** Citas BOOKED del cliente (solo filtros de igualdad: no requiere índice compuesto). */
    suspend fun activeAppointments(tenantId: String, clientUid: String): List<Map<String, Any?>>

    /** Transacción: si algún bloque ya existe lanza [SlotTakenException]; si no, escribe cita + bloques. */
    suspend fun book(tenantId: String, apptId: String, appointment: Map<String, Any?>, dayId: String, keys: Set<String>)

    /** Batch: cita CANCELLED + quitar sus bloques del día. */
    suspend fun cancel(tenantId: String, apptId: String, dayId: String, keys: Set<String>)

    fun observeClient(tenantId: String, clientUid: String): Flow<List<Pair<String, Map<String, Any?>>>>

    suspend fun shareCode(code: String): String?
}
