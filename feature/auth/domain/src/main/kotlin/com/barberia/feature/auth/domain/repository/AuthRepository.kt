package com.barberia.feature.auth.domain.repository

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability

typealias AuthResult<T> = AppResult<AuthFailure, T>

/** Contrato de autenticación. El dominio no sabe nada de GraphQL, DataStore ni Android. */
interface AuthRepository {
    /** Usuario de la sesión en memoria (después de restaurar o iniciar sesión). */
    val currentUser: AuthUser?

    suspend fun restoreSession(): AuthResult<AuthUser?>
    suspend fun login(email: String, password: String): AuthResult<AuthUser>
    suspend fun registerOwner(request: RegisterOwnerRequest): AuthResult<AuthUser>
    suspend fun registerClient(request: RegisterClientRequest): AuthResult<AuthUser>
    suspend fun switchMode(mode: Capability): AuthResult<AuthUser>
    suspend fun logout()
}

data class RegisterOwnerRequest(
    val email: String,
    val password: String,
    val barberiaName: String,
    val ownerName: String,
    val phone: String? = null,
)

data class RegisterClientRequest(
    val email: String,
    val password: String,
    val name: String,
    val phone: String,
)
