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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.theme.BarberTheme
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.model.SessionDestination
import org.koin.androidx.compose.koinViewModel

@Composable
fun ModeSelectorRoute(
    onNavigate: (SessionDestination) -> Unit,
    viewModel: ModeSelectorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(ModeSelectorIntent.Load)
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModeSelectorEffect.Navigate -> onNavigate(effect.destination)
            }
        }
    }
    ModeSelectorScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun ModeSelectorScreen(state: ModeSelectorState, onIntent: (ModeSelectorIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Hola, ${state.displayName}", style = MaterialTheme.typography.headlineMedium)
        Text(
            "¿Cómo quieres entrar?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        state.availableModes.forEach { mode ->
            BarberButton(
                text = mode.label(),
                onClick = { onIntent(ModeSelectorIntent.Select(mode)) },
                enabled = state.switchingTo == null,
                isLoading = state.switchingTo == mode,
            )
            Spacer(Modifier.height(12.dp))
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun Capability.label() = when (this) {
    Capability.OWNER -> "Dueño"
    Capability.BARBER -> "Barbero"
    Capability.CLIENT -> "Cliente"
}

@Preview
@Composable
private fun ModeSelectorPreview() = BarberTheme {
    ModeSelectorScreen(ModeSelectorState("Barber Gold", listOf(Capability.OWNER, Capability.CLIENT))) {}
}
