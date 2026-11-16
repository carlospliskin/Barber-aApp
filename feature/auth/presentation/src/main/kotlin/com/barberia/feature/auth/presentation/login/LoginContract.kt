package com.barberia.feature.auth.presentation.login

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.presentation.common.splitValidation

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val fieldErrors: Set<FieldError> = emptySet(),
    val failure: AuthFailure? = null
) : UiState

sealed interface LoginIntent : UiIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
    data object ForgotPassword : LoginIntent
    data object GoToRegister : LoginIntent
    data object DismissFailure : LoginIntent
}

sealed interface LoginEffect : UiEffect {
    data object PasswordResetSent : LoginEffect
    data object OpenRegister : LoginEffect
}

/** Resultado interno que alimenta al reducer. */
sealed interface LoginMutation {
    data class EmailChanged(val value: String) : LoginMutation
    data class PasswordChanged(val value: String) : LoginMutation
    data object Started : LoginMutation
    data object Succeeded : LoginMutation
    data class Failed(val failure: AuthFailure) : LoginMutation
    data object FailureDismissed : LoginMutation
}

/** Reducer puro: mismo estado + misma mutación = mismo resultado. Se prueba sin corrutinas. */
fun LoginState.reduce(m: LoginMutation): LoginState = when (m) {
    is LoginMutation.EmailChanged ->
        copy(email = m.value, fieldErrors = fieldErrors - FieldError.EMAIL_INVALID, failure = null)

    is LoginMutation.PasswordChanged ->
        copy(password = m.value, fieldErrors = fieldErrors - FieldError.PASSWORD_EMPTY, failure = null)

    LoginMutation.Started -> copy(isLoading = true, fieldErrors = emptySet(), failure = null)

    LoginMutation.Succeeded -> copy(isLoading = false, password = "")

    is LoginMutation.Failed -> {
        val (fields, general) = m.failure.splitValidation()
        copy(isLoading = false, fieldErrors = fields, failure = general)
    }

    LoginMutation.FailureDismissed -> copy(failure = null)
}
