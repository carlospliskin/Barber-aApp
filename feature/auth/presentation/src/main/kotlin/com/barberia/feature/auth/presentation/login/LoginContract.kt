package com.barberia.feature.auth.presentation.login

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.SessionDestination

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface LoginIntent : UiIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
    data object DismissError : LoginIntent
}

sealed interface LoginEffect : UiEffect {
    data class Navigate(val destination: SessionDestination) : LoginEffect
}
