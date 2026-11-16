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

    /** R4: citas del cliente con `date >= fromDate` (yyyy-MM-dd), ordenadas por fecha, máx. 20. */
    fun observeUpcoming(tenantId: String, clientUid: String, fromDate: String): Flow<List<Pair<String, Map<String, Any?>>>>

    suspend fun shareCode(code: String): String?

    /** Documento `tenants/{id}` o null. */
    suspend fun tenant(tenantId: String): Map<String, Any?>?

    /** Crea `shareCodes/{code}` y lo guarda en la barbería. `false` si el código ya existe. */
    suspend fun claimShareCode(tenantId: String, code: String): Boolean
}
