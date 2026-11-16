package com.barberia.feature.auth.presentation.session

import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.usecase.ObserveSessionDestinationUseCase
import com.barberia.feature.auth.domain.usecase.RetrySessionUseCase
import com.barberia.feature.auth.domain.usecase.SignOutUseCase

/** `destination == null` = todavía no se sabe; la app se queda donde está (splash al inicio). */
data class SessionUiState(val destination: SessionDestination? = null) : UiState

sealed interface SessionIntent : UiIntent {
    data object Retry : SessionIntent

    /** H3: desde "sin conexión" también se puede salir. */
    data object SignOut : SessionIntent
}

sealed interface SessionEffect : UiEffect

/**
 * Única fuente de verdad de la navegación por sesión: login, registro, elegir modo y cerrar sesión
 * solo cambian la sesión; este ViewModel traduce ese cambio a un destino.
 */
class SessionViewModel(
    observeDestination: ObserveSessionDestinationUseCase,
    private val retry: RetrySessionUseCase,
    private val signOut: SignOutUseCase
) : MviViewModel<SessionUiState, SessionIntent, SessionEffect>(SessionUiState()) {

    init {
        launchInScope { observeDestination().collect { d -> setState { copy(destination = d) } } }
    }

    override suspend fun handleIntent(intent: SessionIntent) = when (intent) {
        SessionIntent.Retry -> retry()
        SessionIntent.SignOut -> signOut()
    }
}
