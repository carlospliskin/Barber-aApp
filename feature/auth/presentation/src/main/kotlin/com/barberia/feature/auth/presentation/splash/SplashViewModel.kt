package com.barberia.feature.auth.presentation.splash

import com.barberia.core.common.fold
import com.barberia.core.mvi.MviViewModel
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.destination
import com.barberia.feature.auth.domain.usecase.RestoreSessionUseCase

class SplashViewModel(
    private val restoreSession: RestoreSessionUseCase,
) : MviViewModel<SplashState, SplashIntent, SplashEffect>(SplashState()) {

    override suspend fun handleIntent(intent: SplashIntent) = when (intent) {
        SplashIntent.Start -> {
            val destination = restoreSession().fold(
                onFailure = { SessionDestination.Login },
                onSuccess = { user -> user.destination() },
            )
            sendEffect(SplashEffect.Navigate(destination))
        }
    }
}
