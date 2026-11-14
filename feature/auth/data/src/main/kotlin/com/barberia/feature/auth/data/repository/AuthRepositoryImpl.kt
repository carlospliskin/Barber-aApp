package com.barberia.feature.auth.data.repository

import com.barberia.core.common.AppResult
import com.barberia.core.network.NetworkException
import com.barberia.feature.auth.data.local.SessionStorage
import com.barberia.feature.auth.data.local.StoredSession
import com.barberia.feature.auth.data.remote.AuthPayloadDto
import com.barberia.feature.auth.data.remote.AuthRemoteDataSource
import com.barberia.feature.auth.data.remote.toDomain
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.RegisterClientRequest
import com.barberia.feature.auth.domain.repository.RegisterOwnerRequest
import kotlinx.coroutines.CancellationException

/**
 * Orquesta datasource remoto + almacenamiento local, mapea DTO → dominio
 * y traduce excepciones de red a [AuthFailure].
 */
internal class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val storage: SessionStorage,
) : AuthRepository {

    @Volatile
    override var currentUser: AuthUser? = null
        private set

    override suspend fun restoreSession(): AuthResult<AuthUser?> {
        val stored = storage.read() ?: return AppResult.Success(null)
        return try {
            val user = remote.me(stored.token).toDomain(stored.token, stored.refreshToken)
            currentUser = user
            AppResult.Success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException.NoConnection) {
            AppResult.Failure(AuthFailure.NoConnection)
        } catch (e: Exception) {
            logout() // Token inválido o expirado
            AppResult.Success(null)
        }
    }

    override suspend fun login(email: String, password: String) =
        authenticate { remote.login(email, password) }

    override suspend fun registerOwner(request: RegisterOwnerRequest) = authenticate {
        with(request) { remote.registerOwner(email, password, barberiaName, ownerName, phone) }
    }

    override suspend fun registerClient(request: RegisterClientRequest) = authenticate {
        with(request) { remote.registerClient(email, password, name, phone) }
    }

    override suspend fun switchMode(mode: Capability): AuthResult<AuthUser> {
        val token = currentUser?.token ?: return AppResult.Failure(AuthFailure.SessionExpired)
        return authenticate { remote.switchMode(mode.name, token) }
    }

    override suspend fun logout() {
        currentUser = null
        storage.clear()
    }

    /** Patrón común (DRY) para toda operación que devuelve una sesión nueva. */
    private suspend fun authenticate(call: suspend () -> AuthPayloadDto): AuthResult<AuthUser> = try {
        val user = call().toDomain()
        storage.save(StoredSession(user.token, user.refreshToken))
        currentUser = user
        AppResult.Success(user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(e.toAuthFailure())
    }
}

internal fun Throwable.toAuthFailure(): AuthFailure = when (this) {
    is NetworkException.NoConnection -> AuthFailure.NoConnection
    is NetworkException.Http -> when (code) {
        401, 403 -> AuthFailure.SessionExpired
        else -> AuthFailure.ServerError() // 404, 5xx…: no se muestra el código crudo al usuario
    }
    is NetworkException.GraphQL -> {
        val text = errors.joinToString().lowercase()
        when {
            "invalid" in text || "credentials" in text -> AuthFailure.InvalidCredentials
            "already" in text || "exists" in text -> AuthFailure.EmailAlreadyExists
            "unauthorized" in text || "expired" in text -> AuthFailure.SessionExpired
            else -> AuthFailure.ServerError(errors.firstOrNull())
        }
    }
    else -> AuthFailure.Unknown()
}
