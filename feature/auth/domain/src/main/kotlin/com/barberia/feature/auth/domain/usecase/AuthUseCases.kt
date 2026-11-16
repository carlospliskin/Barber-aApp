package com.barberia.feature.auth.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.resolveDestination
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import com.barberia.feature.auth.domain.repository.SessionRepository
import com.barberia.feature.auth.domain.repository.UserDataEraser
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.validation.AuthValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Un caso de uso = una responsabilidad (SRP). Solo dependen de interfaces del dominio (DIP).

private fun invalid(errors: Set<com.barberia.feature.auth.domain.model.FieldError>) =
    AppResult.Failure(AuthFailure.Validation(errors))

class SignInUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AuthResult<Unit> {
        val errors = AuthValidator.signIn(email, password)
        if (errors.isNotEmpty()) return invalid(errors)
        return auth.signIn(email.normalizedEmail(), password)
    }
}

class RegisterOwnerUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(r: OwnerRegistration): AuthResult<Unit> {
        val errors = AuthValidator.ownerRegistration(r.email, r.password, r.ownerName, r.businessName, r.phone)
        if (errors.isNotEmpty()) return invalid(errors)
        return auth.registerOwner(
            r.copy(
                email = r.email.normalizedEmail(),
                ownerName = r.ownerName.trim(),
                businessName = r.businessName.trim(),
                phone = r.phone?.takeIf { it.isNotBlank() }?.let(AuthValidator::normalizePhone),
            ),
        )
    }
}

class RegisterClientUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(r: ClientRegistration): AuthResult<Unit> {
        val errors = AuthValidator.clientRegistration(r.email, r.password, r.name, r.phone)
        if (errors.isNotEmpty()) return invalid(errors)
        return auth.registerClient(
            r.copy(email = r.email.normalizedEmail(), name = r.name.trim(), phone = AuthValidator.normalizePhone(r.phone)),
        )
    }
}

class SendPasswordResetUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): AuthResult<Unit> {
        val errors = AuthValidator.email(email)
        if (errors.isNotEmpty()) return invalid(errors)
        return auth.sendPasswordReset(email.normalizedEmail())
    }
}

class SelectModeUseCase(private val session: SessionRepository) {
    suspend operator fun invoke(mode: AppMode): AuthResult<Unit> {
        val user = session.currentUser() ?: return AppResult.Failure(AuthFailure.NotSignedIn)
        if (mode !in user.availableModes) return AppResult.Failure(AuthFailure.ModeNotAllowed)
        session.saveMode(mode)
        return AppResult.Success(Unit)
    }
}

class SignOutUseCase(private val auth: AuthRepository, private val session: SessionRepository) {
    suspend operator fun invoke() {
        session.saveMode(null) // A5: el siguiente usuario no hereda el modo
        auth.signOut()
    }
}

class ResendVerificationEmailUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(): AuthResult<Unit> = auth.resendVerificationEmail()
}

class CheckEmailVerifiedUseCase(private val auth: AuthRepository) {
    /** `true` si el usuario ya abrió el enlace del correo. */
    suspend operator fun invoke(): AuthResult<Boolean> = auth.reloadUser()
}

/**
 * A7: eliminar cuenta. Orden: confirmar contraseña → borrar datos → borrar cuenta.
 * Si algo falla antes del último paso la cuenta sigue y se puede reintentar (los datos se borran por partes).
 */
class DeleteAccountUseCase(
    private val auth: AuthRepository,
    private val session: SessionRepository,
    private val eraser: UserDataEraser,
) {
    suspend operator fun invoke(password: String): AuthResult<Unit> {
        val user = session.currentUser() ?: return AppResult.Failure(AuthFailure.NotSignedIn)
        if (password.isBlank()) return invalid(setOf(FieldError.PASSWORD_EMPTY))
        auth.reauthenticate(password).let { if (it is AppResult.Failure) return it }
        val owned = user.staffTenantId.takeIf { user.staffRole == StaffRole.OWNER }
        eraser.erase(user.uid, owned).let { if (it is AppResult.Failure) return it }
        session.saveMode(null)
        return auth.deleteAccount()
    }
}

class ObserveSessionDestinationUseCase(private val session: SessionRepository) {
    operator fun invoke(): Flow<SessionDestination?> =
        combine(session.session, session.selectedMode, ::resolveDestination).distinctUntilChanged()
}

class ObserveCurrentUserUseCase(private val session: SessionRepository) {
    operator fun invoke(): Flow<AuthUser?> =
        session.session.map { (it as? SessionState.SignedIn)?.user }.distinctUntilChanged()
}

class RetrySessionUseCase(private val session: SessionRepository) {
    operator fun invoke() = session.refresh()
}

/** Firebase Auth guarda el email en minúsculas; el perfil debe coincidir con la cuenta. */
private fun String.normalizedEmail() = trim().lowercase()
