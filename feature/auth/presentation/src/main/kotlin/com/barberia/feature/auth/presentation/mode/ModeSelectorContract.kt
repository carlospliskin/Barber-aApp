package com.barberia.feature.auth.presentation.mode

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.model.SessionDestination

data class ModeSelectorState(
    val displayName: String = "",
    val availableModes: List<Capability> = emptyList(),
    val switchingTo: Capability? = null,
    val errorMessage: String? = null,
) : UiState

sealed interface ModeSelectorIntent : UiIntent {
    data object Load : ModeSelectorIntent
    data class Select(val mode: Capability) : ModeSelectorIntent
}

sealed interface ModeSelectorEffect : UiEffect {
    data class Navigate(val destination: SessionDestination) : ModeSelectorEffect
}
