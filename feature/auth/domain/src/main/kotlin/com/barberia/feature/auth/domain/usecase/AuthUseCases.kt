package com.barberia.feature.auth.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.RegisterClientRequest
import com.barberia.feature.auth.domain.repository.RegisterOwnerRequest

// Un caso de uso = una responsabilidad (SRP). Dependen de la abstracción AuthRepository (DIP).

class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AuthResult<AuthUser?> = repository.restoreSession()
}

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): AuthUser? = repository.currentUser
}

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AuthResult<AuthUser> {
        val cleanEmail = email.trim()
        if (!cleanEmail.isValidEmail() || password.isBlank()) {
            return AppResult.Failure(AuthFailure.InvalidCredentials)
        }
        return repository.login(cleanEmail, password)
    }
}

class RegisterOwnerUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(request: RegisterOwnerRequest): AuthResult<AuthUser> =
        repository.registerOwner(request.copy(email = request.email.trim()))
}

class RegisterClientUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(request: RegisterClientRequest): AuthResult<AuthUser> =
        repository.registerClient(request.copy(email = request.email.trim()))
}

class SwitchModeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(mode: Capability): AuthResult<AuthUser> = repository.switchMode(mode)
}

class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.logout()
}

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
internal fun String.isValidEmail(): Boolean = EMAIL_REGEX.matches(this)
