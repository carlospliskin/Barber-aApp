package com.barberia.feature.auth.domain.repository

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

typealias AuthResult<T> = AppResult<AuthFailure, T>

/** Operaciones de cuenta. No devuelve el usuario: la sesión se observa en [SessionRepository]. */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthResult<Unit>
    suspend fun registerOwner(registration: OwnerRegistration): AuthResult<Unit>
    suspend fun registerClient(registration: ClientRegistration): AuthResult<Unit>
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>
    suspend fun signOut()
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

data class OwnerRegistration(
    val email: String,
    val password: String,
    val ownerName: String,
    val businessName: String,
    val phone: String?,
)

data class ClientRegistration(
    val email: String,
    val password: String,
    val name: String,
    val phone: String,
)
