package com.barberia.feature.auth.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.model.StaffAccess
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import com.barberia.feature.auth.domain.repository.SessionRepository
import com.barberia.feature.auth.domain.repository.UserDataEraser
import kotlinx.coroutines.flow.MutableStateFlow

fun user(role: StaffRole? = null, name: String = "Ana Pérez") = AuthUser(
    uid = "uid-1",
    email = "ana@mail.com",
    displayName = name,
    staffTenantId = role?.let { "uid-1" },
    staffRole = role,
)

class FakeAuthRepository(var result: AuthResult<Unit> = AppResult.Success(Unit)) : AuthRepository {
    val calls = mutableListOf<String>()
    var lastOwner: OwnerRegistration? = null
    var lastClient: ClientRegistration? = null

    override suspend fun signIn(email: String, password: String): AuthResult<Unit> {
        calls += "signIn:$email"
        return result
    }
    override suspend fun registerOwner(registration: OwnerRegistration): AuthResult<Unit> {
        lastOwner = registration
        calls += "registerOwner"
        return result
    }
    override suspend fun registerClient(registration: ClientRegistration): AuthResult<Unit> {
        lastClient = registration
        calls += "registerClient"
        return result
    }
    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        calls += "reset:$email"
        return result
    }
    override suspend fun signOut() {
        calls += "signOut"
    }

    var verified = false
    override suspend fun resendVerificationEmail(): AuthResult<Unit> {
        calls += "resendVerification"
        return result
    }
    override suspend fun reloadUser(): AuthResult<Boolean> {
        calls += "reloadUser"
        return AppResult.Success(verified)
    }

    var reauthResult: AuthResult<Unit> = AppResult.Success(Unit)
    override suspend fun reauthenticate(password: String): AuthResult<Unit> {
        calls += "reauthenticate"
        return reauthResult
    }
    override suspend fun deleteAccount(): AuthResult<Unit> {
        onCall("deleteAccount")
        calls += "deleteAccount"
        return result
    }

    var access = StaffAccess.ACTIVE
    override suspend fun staffAccess(tenantId: String): AuthResult<StaffAccess> {
        calls += "staffAccess:$tenantId"
        return AppResult.Success(access)
    }
    /** Para comprobar el orden respecto a la sesión (p. ej. que el modo se borre después). */
    var onCall: (String) -> Unit = {}
    override suspend fun leaveStaff(): AuthResult<Unit> {
        onCall("leaveStaff")
        calls += "leaveStaff"
        return result
    }
}

class FakeUserDataEraser(var result: AuthResult<Unit> = AppResult.Success(Unit)) : UserDataEraser {
    val erased = mutableListOf<Pair<String, String?>>()
    override suspend fun erase(uid: String, ownedTenantId: String?): AuthResult<Unit> {
        erased += uid to ownedTenantId
        return result
    }
}

class FakeSessionRepository(
    initial: SessionState = SessionState.SignedOut,
    mode: AppMode? = null,
) : SessionRepository {
    val sessionFlow = MutableStateFlow(initial)
    val modeFlow = MutableStateFlow(mode)
    val calls = mutableListOf<String>()

    override val session = sessionFlow
    override val selectedMode = modeFlow
    override fun currentUser(): AuthUser? = (sessionFlow.value as? SessionState.SignedIn)?.user
    override suspend fun saveMode(mode: AppMode?) {
        calls += "saveMode:$mode"
        modeFlow.value = mode
    }
    override fun refresh() {
        calls += "refresh"
    }
}
