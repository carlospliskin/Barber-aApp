package com.barberia.feature.auth.presentation.splash

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.SessionDestination

data class SplashState(val errorMessage: String? = null) : UiState

sealed interface SplashIntent : UiIntent {
    data object Start : SplashIntent
}

sealed interface SplashEffect : UiEffect {
    data class Navigate(val destination: SessionDestination) : SplashEffect
}
