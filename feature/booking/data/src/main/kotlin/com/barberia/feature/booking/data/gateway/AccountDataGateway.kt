package com.barberia.feature.booking.data.gateway

/** A7: acceso crudo a Firestore para eliminar cuenta. Cada lista está acotada por [limit]. */
internal interface AccountDataGateway {
    /** Consulta de grupo `appointments where clientUid == uid`: (tenantId, id, datos). */
    suspend fun appointmentsOfClient(uid: String, limit: Int): List<Triple<String, String, Map<String, Any?>>>

    /** Batch: `clientUid = null`, `clientName = ""` en esas citas de la barbería. */
    suspend fun anonymize(tenantId: String, ids: List<String>)

    suspend fun tenantAppointments(tenantId: String, limit: Int): List<Pair<String, Map<String, Any?>>>
    suspend fun clientIds(tenantId: String, limit: Int): List<String>
    suspend fun inviteCodes(tenantId: String): List<String>

    /** A16: uids en `tenants/{t}/revoked` (también se borran con la barbería). */
    suspend fun revokedIds(tenantId: String): List<String>
    suspend fun shareCodeOf(tenantId: String): String?

    /** Batch de borrados (máx. 500 rutas). */
    suspend fun deleteAll(paths: List<String>)
}
