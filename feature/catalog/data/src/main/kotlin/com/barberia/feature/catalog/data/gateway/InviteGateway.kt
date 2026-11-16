package com.barberia.feature.catalog.data.gateway

/** `staffInvites/{code}` + perfil del barbero. Los instantes viajan como `java.time.Instant`. */
internal interface InviteGateway {
    suspend fun get(code: String): Map<String, Any?>?

    /** Crea el documento si no existe. `false` si el código ya estaba tomado. */
    suspend fun createIfAbsent(code: String, data: Map<String, Any?>): Boolean

    /** Batch: `users/{uid}` += [profileChanges] y `staffInvites/{code}.usedBy = uid`. */
    suspend fun redeem(code: String, uid: String, profileChanges: Map<String, Any?>)
}
