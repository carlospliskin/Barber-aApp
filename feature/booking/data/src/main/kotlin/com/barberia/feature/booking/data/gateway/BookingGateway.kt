package com.barberia.feature.booking.data.gateway

import kotlinx.coroutines.flow.Flow

/** El día ya tenía alguno de los bloques pedidos (B3). */
internal class SlotTakenException : Exception()

/** Acceso crudo a Firestore para reservas. Los instantes viajan como `java.time.Instant`. */
internal interface BookingGateway {
    suspend fun getDay(tenantId: String, dayId: String): Map<String, Any?>?

    /** Citas BOOKED del cliente (solo filtros de igualdad: no requiere índice compuesto). */
    suspend fun activeAppointments(tenantId: String, clientUid: String): List<Pair<String, Map<String, Any?>>>

    /**
     * Transacción: si algún bloque ya existe lanza [SlotTakenException]; si no, escribe cita + bloques y,
     * si el cliente todavía no tiene ficha en esta barbería, la crea con [newClient] (F7-6).
     * N1: [clientUid] null = cita sin cuenta, sin ficha.
     */
    suspend fun book(
        tenantId: String,
        apptId: String,
        appointment: Map<String, Any?>,
        dayId: String,
        keys: Set<String>,
        clientUid: String?,
        newClient: Map<String, Any?>?
    )

    /** Batch: cita CANCELLED + quitar sus bloques del día. */
    suspend fun cancel(tenantId: String, apptId: String, dayId: String, keys: Set<String>)

    /** R4: citas del cliente con `date >= fromDate` (yyyy-MM-dd), ordenadas por fecha, máx. 20. */
    fun observeUpcoming(tenantId: String, clientUid: String, fromDate: String): Flow<List<Pair<String, Map<String, Any?>>>>

    /** A3: citas de un día (`date ==`, y `barberId ==` si se da). Solo igualdades: sin índice compuesto. */
    fun observeDay(tenantId: String, date: String, barberId: String?): Flow<List<Pair<String, Map<String, Any?>>>>

    /**
     * Transacción (P7/P9): lee la ficha del cliente, cambia el estado de la cita y escribe la ficha que
     * devuelva [updateClient] (la ficha nunca se pisa con un dato viejo). N1: sin cuenta, solo la cita.
     */
    suspend fun setStatus(
        tenantId: String,
        apptId: String,
        status: String,
        clientUid: String?,
        updateClient: ((Map<String, Any?>?) -> Map<String, Any?>)?
    )

    suspend fun getAppointment(tenantId: String, id: String): Map<String, Any?>?

    suspend fun getClient(tenantId: String, uid: String): Map<String, Any?>?

    fun observeClient(tenantId: String, uid: String): Flow<Map<String, Any?>?>

    /** Máx. 50, los actualizados más recientemente (orderBy de un solo campo: sin índice compuesto). */
    fun observeClients(tenantId: String): Flow<List<Pair<String, Map<String, Any?>>>>

    /** Transacción sobre la ficha; [update] devuelve null para no guardar. */
    suspend fun updateClient(tenantId: String, uid: String, update: (Map<String, Any?>) -> Map<String, Any?>?)

    suspend fun shareCode(code: String): String?

    /** Documento `tenants/{id}` o null. */
    suspend fun tenant(tenantId: String): Map<String, Any?>?

    /** N5: ¿alguna cita BOOKED con `date >= fromDate`? Límite 1 (índice compuesto status + date). */
    suspend fun hasBookedFrom(tenantId: String, fromDate: String): Boolean

    /** N5: cambia `timezone` de la barbería (las reglas validan la lista). */
    suspend fun updateTimezone(tenantId: String, timezone: String)

    /** Crea `shareCodes/{code}` y lo guarda en la barbería. `false` si el código ya existe. */
    suspend fun claimShareCode(tenantId: String, code: String): Boolean
}
