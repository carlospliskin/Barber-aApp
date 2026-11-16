package com.barberia.feature.auth.data.gateway

import kotlinx.coroutines.flow.Flow

// Interfaces chicas sobre los SDK (ISP + DIP). El repositorio se prueba con fakes en la JVM;
// solo las implementaciones de `firebase/` tocan Firebase.

internal data class AccountInfo(val uid: String, val email: String?, val displayName: String?, val emailVerified: Boolean)

internal interface AuthGateway {
    /** uid del usuario con sesión, o null. Emite al iniciar y al cambiar. */
    val uidChanges: Flow<String?>
    fun currentAccount(): AccountInfo?
    suspend fun signIn(email: String, password: String)

    /** Crea la cuenta y deja la sesión iniciada. Devuelve el uid. */
    suspend fun createUser(email: String, password: String): String
    suspend fun sendEmailVerification()

    /** Recarga el usuario y fuerza un token nuevo (A15). Devuelve si el correo ya está verificado. */
    suspend fun reload(): Boolean
    suspend fun sendPasswordReset(email: String)
    suspend fun deleteCurrentUser()

    /** A7: vuelve a iniciar sesión con la contraseña (Firebase lo pide para borrar la cuenta). */
    suspend fun reauthenticate(password: String)
    fun signOut()
}

/** Documento `users/{uid}`. */
internal data class ProfileRecord(
    val displayName: String,
    val email: String,
    val phone: String?,
    val staffTenantId: String?,
    val staffRole: String?,
    val staffBarberId: String? = null
)

/** Documento `tenants/{ownerUid}`. */
internal data class TenantRecord(val name: String, val ownerUid: String, val timezone: String)

internal interface ProfileGateway {
    suspend fun fetch(uid: String): ProfileRecord?

    /** Escribe perfil + barbería en un solo batch: o se guardan los dos o ninguno. */
    suspend fun createOwner(uid: String, profile: ProfileRecord, tenant: TenantRecord)
    suspend fun createClient(uid: String, profile: ProfileRecord)

    /** A7: borra `users/{uid}`. */
    suspend fun delete(uid: String)

    /** A7-b: `tenants/{id}` existe (1 lectura). */
    suspend fun tenantExists(tenantId: String): Boolean

    /** A16: `tenants/{t}/revoked/{uid}` existe (1 lectura). */
    suspend fun isRevoked(tenantId: String, uid: String): Boolean

    /** A7-b/A16: pone en null staffTenantId, staffRole, staffBarberId y staffInviteCode (todo o nada, como la regla). */
    suspend fun leaveStaff(uid: String)
}

/** Preferencia local del modo elegido (no se guarda en Firestore: no gasta escrituras). */
internal interface ModeStore {
    val mode: Flow<String?>
    suspend fun save(mode: String?)
}
