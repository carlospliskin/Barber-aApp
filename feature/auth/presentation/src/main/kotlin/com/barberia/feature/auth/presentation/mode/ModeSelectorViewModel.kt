package com.barberia.feature.auth.presentation.mode

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.destination
import com.barberia.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.SwitchModeUseCase
import com.barberia.feature.auth.presentation.common.userMessage

class ModeSelectorViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val switchMode: SwitchModeUseCase,
) : MviViewModel<ModeSelectorState, ModeSelectorIntent, ModeSelectorEffect>(ModeSelectorState()) {

    override suspend fun handleIntent(intent: ModeSelectorIntent) {
        when (intent) {
            ModeSelectorIntent.Load -> load()
            is ModeSelectorIntent.Select -> select(intent.mode)
        }
    }

    private suspend fun load() {
        val user = getCurrentUser() ?: return sendEffect(ModeSelectorEffect.Navigate(SessionDestination.Login))
        setState {
            copy(
                displayName = user.displayName,
                availableModes = Capability.entries.filter { it in user.capabilities },
            )
        }
    }

    private suspend fun select(mode: Capability) {
        if (currentState.switchingTo != null) return
        setState { copy(switchingTo = mode, errorMessage = null) }
        when (val result = switchMode(mode)) {
            is AppResult.Success -> {
                setState { copy(switchingTo = null) }
                sendEffect(ModeSelectorEffect.Navigate(result.value.destination(afterModeSwitch = true)))
            }
            is AppResult.Failure -> setState { copy(switchingTo = null, errorMessage = result.error.userMessage()) }
        }
    }
}
