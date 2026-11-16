package com.barberia.feature.auth.domain.repository

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffAccess
import kotlinx.coroutines.flow.Flow

typealias AuthResult<T> = AppResult<AuthFailure, T>

/** Operaciones de cuenta. No devuelve el usuario: la sesión se observa en [SessionRepository]. */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthResult<Unit>
    suspend fun registerOwner(registration: OwnerRegistration): AuthResult<Unit>
    suspend fun registerClient(registration: ClientRegistration): AuthResult<Unit>
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>
    suspend fun signOut()

    /** A14: reenvía el correo de verificación al usuario con sesión. */
    suspend fun resendVerificationEmail(): AuthResult<Unit>

    /** A14/A15: recarga el usuario y renueva el token (para que las reglas vean `email_verified`). */
    suspend fun reloadUser(): AuthResult<Boolean>

    /** A7: Firebase pide un inicio de sesión reciente para borrar la cuenta; además confirma que es el dueño. */
    suspend fun reauthenticate(password: String): AuthResult<Unit>

    /** A7: borra el perfil `users/{uid}` y la cuenta de Firebase Auth (la sesión se cierra sola). */
    suspend fun deleteAccount(): AuthResult<Unit>

    /** A7-b/A16: ¿existe la barbería y no me quitaron el acceso? (2 lecturas). */
    suspend fun staffAccess(tenantId: String): AuthResult<StaffAccess>

    /** A7-b/A16: el barbero vuelve a ser solo cliente (borra su rol del perfil). */
    suspend fun leaveStaff(): AuthResult<Unit>
}

/**
 * A7: borra lo que el usuario dejó en las barberías (citas, fichas) y, si es dueño, su barbería.
 * Lo implementa otra feature; `app` lo conecta. Debe poder reintentarse si se corta a la mitad.
 */
fun interface UserDataEraser {
    suspend fun erase(uid: String, ownedTenantId: String?): AuthResult<Unit>
}

/** Estado de sesión observable (sobrevive a la muerte del proceso) y modo elegido guardado localmente. */
interface SessionRepository {
    val session: Flow<SessionState>
    val selectedMode: Flow<AppMode?>
    fun currentUser(): AuthUser?
    suspend fun saveMode(mode: AppMode?)

    /** Vuelve a leer el perfil (después de registrarse o tras un error de red). */
    fun refresh()
}

data class OwnerRegistration(val email: String, val password: String, val ownerName: String, val businessName: String, val phone: String?)

data class ClientRegistration(val email: String, val password: String, val name: String, val phone: String)
