package com.barberia.feature.auth.presentation.session

import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.usecase.ObserveSessionDestinationUseCase
import com.barberia.feature.auth.domain.usecase.ObserveSessionStateUseCase
import com.barberia.feature.auth.domain.usecase.RetrySessionUseCase

data class SessionUiState(
    /** null = todavía no se sabe; la app muestra el splash. */
    val destination: SessionDestination? = null,
    val showRetry: Boolean = false,
) : UiState

sealed interface SessionIntent : UiIntent {
    data object Retry : SessionIntent
}

sealed interface SessionEffect : UiEffect

/**
 * Única fuente de verdad de la navegación por sesión: login, registro, elegir modo y cerrar sesión
 * solo cambian la sesión; este ViewModel traduce ese cambio a un destino.
 */
class SessionViewModel(
    observeDestination: ObserveSessionDestinationUseCase,
    observeState: ObserveSessionStateUseCase,
    private val retry: RetrySessionUseCase,
) : MviViewModel<SessionUiState, SessionIntent, SessionEffect>(SessionUiState()) {

    init {
        launchInScope { observeDestination().collect { d -> setState { copy(destination = d) } } }
        launchInScope {
            observeState().collect { s -> setState { copy(showRetry = s == SessionState.Unavailable) } }
        }
    }

    override suspend fun handleIntent(intent: SessionIntent) = when (intent) {
        SessionIntent.Retry -> retry()
    }
}
