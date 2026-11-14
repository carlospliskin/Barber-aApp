package com.barberia.feature.auth.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.RegisterClientRequest
import com.barberia.feature.auth.domain.repository.RegisterOwnerRequest
import com.barberia.feature.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginUseCaseTest {
    private class SpyRepository : AuthRepository {
        var lastEmail: String? = null
        override val currentUser: AuthUser? = null
        override suspend fun restoreSession(): AuthResult<AuthUser?> = AppResult.Success(null)
        override suspend fun login(email: String, password: String): AuthResult<AuthUser> {
            lastEmail = email
            return AppResult.Success(AuthUser("u", "t", "r", setOf(Capability.CLIENT), Capability.CLIENT))
        }
        override suspend fun registerOwner(request: RegisterOwnerRequest) = error("no usado")
        override suspend fun registerClient(request: RegisterClientRequest) = error("no usado")
        override suspend fun switchMode(mode: Capability) = error("no usado")
        override suspend fun logout() = Unit
    }

    @Test fun `limpia espacios del email antes de llamar al repositorio`() = runTest {
        val repo = SpyRepository()
        LoginUseCase(repo)("  ana@mail.com ", "secreto")
        assertEquals("ana@mail.com", repo.lastEmail)
    }

    @Test fun `email invalido no llega al repositorio`() = runTest {
        val repo = SpyRepository()
        val result = LoginUseCase(repo)("no-es-email", "secreto")
        assertEquals(AppResult.Failure(AuthFailure.InvalidCredentials), result)
        assertNull(repo.lastEmail)
    }
}
