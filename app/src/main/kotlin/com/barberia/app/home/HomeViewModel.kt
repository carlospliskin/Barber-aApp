package com.barberia.app.home

import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.LogoutUseCase

data class HomeState(val displayName: String = "", val initials: String = "") : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Logout : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object LoggedOut : HomeEffect
}

/** Home provisional: se reemplaza por los módulos feature:home-owner / home-client al portarlos. */
class HomeViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val logout: LogoutUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> getCurrentUser()?.let { user ->
                setState { copy(displayName = user.displayName, initials = user.initials) }
            }
            HomeIntent.Logout -> {
                logout()
                sendEffect(HomeEffect.LoggedOut)
            }
        }
    }
}
