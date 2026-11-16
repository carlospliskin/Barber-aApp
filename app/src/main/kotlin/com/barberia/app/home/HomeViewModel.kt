package com.barberia.app.home

import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.SignOutUseCase

data class HomeState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object SignOut : HomeIntent
}

sealed interface HomeEffect : UiEffect

/** Home provisional hasta portar feature:home-owner / home-client. Cerrar sesión navega solo (por la sesión). */
class HomeViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val signOut: SignOutUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        launchInScope {
            observeUser().collect { u ->
                if (u != null) setState { copy(loaded = true, displayName = u.displayName, email = u.email, emailVerified = u.emailVerified) }
            }
        }
    }

    override suspend fun handleIntent(intent: HomeIntent) = when (intent) {
        HomeIntent.SignOut -> signOut()
    }
}
