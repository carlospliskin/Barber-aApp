package com.barberia.feature.auth.data.repository

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.data.gateway.AuthGateway
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.auth.data.gateway.ModeStore
import com.barberia.feature.auth.data.gateway.ProfileGateway
import com.barberia.feature.auth.data.gateway.ProfileRecord
import com.barberia.feature.auth.data.gateway.TenantRecord
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import com.barberia.feature.auth.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Una sola clase implementa las dos interfaces porque comparten el estado de sesión
 * (al registrarse hay que refrescar el perfil). Los consumidores dependen solo de la interfaz que usan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FirebaseAccountRepository(
    private val auth: AuthGateway,
    private val profiles: ProfileGateway,
    private val modes: ModeStore,
    scope: CoroutineScope,
) : AuthRepository, SessionRepository {

    private val refreshes = MutableStateFlow(0)

    /** H1: mientras se crea la cuenta y su perfil, la sesión no se publica (si no, el dueño se vería como cliente). */
    private val registering = MutableStateFlow(false)

    override val session: StateFlow<SessionState> =
        combine(auth.uidChanges, refreshes, registering) { uid, _, busy -> uid to busy }
            .mapLatest { (uid, busy) ->
                when {
                    busy -> SessionState.Loading
                    uid == null -> SessionState.SignedOut
                    else -> loadSession(uid)
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** H4: el modo se guarda como `uid:MODO`; el de otra cuenta se ignora. */
    override val selectedMode: Flow<AppMode?> =
        combine(modes.mode, session) { raw, state ->
            val uid = (state as? SessionState.SignedIn)?.user?.uid ?: return@combine null
            val (owner, mode) = raw?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return@combine null
            if (owner == uid) AppMode.entries.find { it.name == mode } else null
        }

    override fun currentUser(): AuthUser? = (session.value as? SessionState.SignedIn)?.user

    override suspend fun saveMode(mode: AppMode?) {
        val uid = currentUser()?.uid
        modes.save(if (mode == null || uid == null) null else "$uid:${mode.name}")
    }

    override fun refresh() {
        refreshes.value++
    }

    // ── Cuenta ────────────────────────────────────────────────────────────────

    override suspend fun signIn(email: String, password: String) = attempt { auth.signIn(email, password) }

    override suspend fun registerOwner(registration: OwnerRegistration) = registeringAttempt {
        val uid = auth.createUser(registration.email, registration.password)
        createProfileOrRollback {
            profiles.createOwner(
                uid,
                ProfileRecord(registration.ownerName, registration.email, registration.phone, uid, StaffRole.OWNER.name),
                TenantRecord(registration.businessName, ownerUid = uid, timezone = DEFAULT_TIMEZONE),
            )
        }
    }

    override suspend fun registerClient(registration: ClientRegistration) = registeringAttempt {
        val uid = auth.createUser(registration.email, registration.password)
        createProfileOrRollback {
            profiles.createClient(uid, ProfileRecord(registration.name, registration.email, registration.phone, null, null))
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> = try {
        auth.sendPasswordReset(email)
        AppResult.Success(Unit)
    } catch (e: GatewayError.InvalidCredentials) {
        AppResult.Success(Unit) // No revelar si el email está registrado
    } catch (e: GatewayError) {
        AppResult.Failure(e.toFailure())
    }

    override suspend fun signOut() = auth.signOut()

    override suspend fun resendVerificationEmail(): AuthResult<Unit> = attempt { auth.sendEmailVerification() }

    override suspend fun reloadUser(): AuthResult<Boolean> {
        var verified = false
        val r = attempt { verified = auth.reload() }
        if (r is AppResult.Failure) return r
        refresh() // la sesión vuelve a leer la cuenta con emailVerified actualizado
        return AppResult.Success(verified)
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    private suspend fun loadSession(uid: String): SessionState {
        val account = auth.currentAccount()
        return try {
            val profile = profiles.fetch(uid)
            SessionState.SignedIn(toUser(uid, account?.email, account?.displayName, account?.emailVerified, profile))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SessionState.Unavailable
        }
    }

    /** A9: si no se pudo escribir el perfil, se borra la cuenta para no dejar un usuario a medias. */
    private suspend fun createProfileOrRollback(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { auth.deleteCurrentUser() }
            throw e
        }
        runCatching { auth.sendEmailVerification() } // A2: mejor esfuerzo
        refresh()
    }

    private suspend fun registeringAttempt(block: suspend () -> Unit): AuthResult<Unit> {
        registering.value = true
        return try {
            attempt(block)
        } finally {
            registering.value = false
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): AuthResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: GatewayError) {
        AppResult.Failure(e.toFailure())
    } catch (e: Exception) {
        AppResult.Failure(AuthFailure.Unknown(e.message))
    }

    private companion object {
        const val DEFAULT_TIMEZONE = "America/Mexico_City"
    }
}

internal fun toUser(
    uid: String,
    email: String?,
    authName: String?,
    verified: Boolean?,
    profile: ProfileRecord?,
): AuthUser {
    val mail = profile?.email ?: email.orEmpty()
    return AuthUser(
        uid = uid,
        email = mail,
        displayName = profile?.displayName ?: authName ?: mail.substringBefore('@'),
        emailVerified = verified ?: false,
        phone = profile?.phone,
        staffTenantId = profile?.staffTenantId,
        staffRole = StaffRole.entries.find { it.name == profile?.staffRole },
        staffBarberId = profile?.staffBarberId,
    )
}

internal fun GatewayError.toFailure(): AuthFailure = when (this) {
    is GatewayError.InvalidCredentials -> AuthFailure.InvalidCredentials
    is GatewayError.EmailInUse -> AuthFailure.EmailAlreadyInUse
    is GatewayError.WeakPassword -> AuthFailure.WeakPassword
    is GatewayError.Network -> AuthFailure.NoConnection
    is GatewayError.TooManyRequests -> AuthFailure.TooManyRequests
    is GatewayError.PermissionDenied, is GatewayError.Other -> AuthFailure.Unknown(cause?.message)
}
