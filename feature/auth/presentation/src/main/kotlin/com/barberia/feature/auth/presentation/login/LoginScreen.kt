package com.barberia.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.designsystem.theme.BarberTheme
import com.barberia.feature.auth.domain.model.SessionDestination
import org.koin.androidx.compose.koinViewModel

/** Conecta el ViewModel con la pantalla; la pantalla en sí es sin estado y previsualizable. */
@Composable
fun LoginRoute(
    onNavigate: (SessionDestination) -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.Navigate -> onNavigate(effect.destination)
            }
        }
    }
    LoginScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun LoginScreen(state: LoginState, onIntent: (LoginIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Barbería", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "Reserva tu estilo",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        BarberTextField(
            value = state.email,
            onValueChange = { onIntent(LoginIntent.EmailChanged(it)) },
            label = "Email",
            keyboardType = KeyboardType.Email,
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(12.dp))
        BarberTextField(
            value = state.password,
            onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
            label = "Contraseña",
            isPassword = true,
            enabled = !state.isLoading,
        )
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        BarberButton(
            text = "Iniciar sesión",
            onClick = { onIntent(LoginIntent.Submit) },
            enabled = state.canSubmit,
            isLoading = state.isLoading,
        )
    }
}

@Preview
@Composable
private fun LoginScreenPreview() = BarberTheme {
    LoginScreen(LoginState(email = "ana@mail.com", errorMessage = "Email o contraseña incorrectos")) {}
}
