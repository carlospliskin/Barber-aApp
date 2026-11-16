package com.barberia.feature.catalog.data.gateway

/** `staffInvites/{code}` + perfil del barbero. Los instantes viajan como `java.time.Instant`. */
internal interface InviteGateway {
    suspend fun get(code: String): Map<String, Any?>?

    /** Crea el documento si no existe. `false` si el código ya estaba tomado. */
    suspend fun createIfAbsent(code: String, data: Map<String, Any?>): Boolean

    /** Batch: `users/{uid}` += [profileChanges] y `staffInvites/{code}.usedBy = uid`. */
    suspend fun redeem(code: String, uid: String, profileChanges: Map<String, Any?>)

    /** A16: invitaciones de la barbería (`where tenantId ==`, lo exige la regla). */
    suspend fun invitesOfTenant(tenantId: String): List<Map<String, Any?>>

    /** A16: crea `tenants/{t}/revoked/{uid}`. */
    suspend fun revoke(tenantId: String, uid: String, barberId: String)
}
