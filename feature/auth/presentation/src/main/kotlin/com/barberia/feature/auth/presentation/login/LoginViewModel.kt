package com.barberia.feature.auth.presentation.login

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.feature.auth.domain.usecase.SendPasswordResetUseCase
import com.barberia.feature.auth.domain.usecase.SignInUseCase

class LoginViewModel(
    private val signIn: SignInUseCase,
    private val sendPasswordReset: SendPasswordResetUseCase,
) : MviViewModel<LoginState, LoginIntent, LoginEffect>(LoginState()) {

    override suspend fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> mutate(LoginMutation.EmailChanged(intent.value))
            is LoginIntent.PasswordChanged -> mutate(LoginMutation.PasswordChanged(intent.value))
            LoginIntent.DismissFailure -> mutate(LoginMutation.FailureDismissed)
            LoginIntent.GoToRegister -> sendEffect(LoginEffect.OpenRegister)
            LoginIntent.Submit -> submit()
            LoginIntent.ForgotPassword -> forgotPassword()
        }
    }

    private suspend fun submit() {
        if (currentState.isLoading) return
        mutate(LoginMutation.Started)
        // Si sale bien, la sesión cambia y SessionViewModel navega. Aquí no se navega.
        when (val r = signIn(currentState.email, currentState.password)) {
            is AppResult.Success -> mutate(LoginMutation.Succeeded)
            is AppResult.Failure -> mutate(LoginMutation.Failed(r.error))
        }
    }

    private suspend fun forgotPassword() {
        when (val r = sendPasswordReset(currentState.email)) {
            is AppResult.Success -> sendEffect(LoginEffect.PasswordResetSent)
            is AppResult.Failure -> mutate(LoginMutation.Failed(r.error))
        }
    }

    private fun mutate(m: LoginMutation) = setState { reduce(m) }
}
