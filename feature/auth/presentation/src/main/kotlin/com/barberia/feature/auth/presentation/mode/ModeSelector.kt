package com.barberia.feature.auth.presentation.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.theme.BarberTheme
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.SelectModeUseCase
import com.barberia.feature.auth.presentation.R
import com.barberia.feature.auth.presentation.common.labelRes
import com.barberia.feature.auth.presentation.common.messageRes
import org.koin.androidx.compose.koinViewModel

data class ModeSelectorState(
    val displayName: String = "",
    val modes: List<AppMode> = emptyList(),
    val isSaving: Boolean = false,
    val failure: AuthFailure? = null,
) : UiState

sealed interface ModeSelectorIntent : UiIntent {
    data class Select(val mode: AppMode) : ModeSelectorIntent
}

sealed interface ModeSelectorEffect : UiEffect

/** Elegir modo es local (no gasta escrituras en Firestore); la navegación la hace la sesión. */
class ModeSelectorViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val selectMode: SelectModeUseCase,
) : MviViewModel<ModeSelectorState, ModeSelectorIntent, ModeSelectorEffect>(ModeSelectorState()) {

    init {
        launchInScope {
            observeUser().collect { user ->
                setState { copy(displayName = user?.displayName.orEmpty(), modes = user?.availableModes.orEmpty()) }
            }
        }
    }

    override suspend fun handleIntent(intent: ModeSelectorIntent) {
        when (intent) {
            is ModeSelectorIntent.Select -> {
                setState { copy(isSaving = true, failure = null) }
                val result = selectMode(intent.mode)
                setState { copy(isSaving = false, failure = (result as? AppResult.Failure)?.error) }
            }
        }
    }
}

@Composable
fun ModeSelectorRoute(viewModel: ModeSelectorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModeSelectorScreen(state, viewModel::onIntent)
}

@Composable
fun ModeSelectorScreen(state: ModeSelectorState, onIntent: (ModeSelectorIntent) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.auth_greeting, state.displayName), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.auth_choose_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        state.modes.forEach { mode ->
            BarberButton(stringResource(mode.labelRes()), { onIntent(ModeSelectorIntent.Select(mode)) }, enabled = !state.isSaving)
            Spacer(Modifier.height(12.dp))
        }
        state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
    }
}

@Preview
@Composable
private fun ModeSelectorPreview() = BarberTheme {
    ModeSelectorScreen(ModeSelectorState("Ana", listOf(AppMode.OWNER, AppMode.BARBER, AppMode.CLIENT))) {}
}
