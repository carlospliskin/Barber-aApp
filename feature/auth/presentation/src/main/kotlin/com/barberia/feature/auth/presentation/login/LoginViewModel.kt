package com.barberia.feature.auth.presentation.login

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.feature.auth.domain.model.destination
import com.barberia.feature.auth.domain.usecase.LoginUseCase
import com.barberia.feature.auth.presentation.common.userMessage

class LoginViewModel(
    private val login: LoginUseCase,
) : MviViewModel<LoginState, LoginIntent, LoginEffect>(LoginState()) {

    override suspend fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            is LoginIntent.PasswordChanged -> setState { copy(password = intent.value, errorMessage = null) }
            LoginIntent.DismissError -> setState { copy(errorMessage = null) }
            LoginIntent.Submit -> submit()
        }
    }

    private suspend fun submit() {
        if (!currentState.canSubmit) return
        setState { copy(isLoading = true, errorMessage = null) }
        when (val result = login(currentState.email, currentState.password)) {
            is AppResult.Success -> {
                setState { copy(isLoading = false, password = "") }
                sendEffect(LoginEffect.Navigate(result.value.destination()))
            }
            is AppResult.Failure -> setState { copy(isLoading = false, errorMessage = result.error.userMessage()) }
        }
    }
}
