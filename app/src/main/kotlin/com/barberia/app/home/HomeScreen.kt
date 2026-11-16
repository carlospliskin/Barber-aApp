package com.barberia.app.home

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.feature.auth.domain.model.AppMode
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(mode: AppMode, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Modo ${mode.name.lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(state.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.emailVerified) {
            Spacer(Modifier.height(8.dp))
            Text("Revisa tu correo para verificar la cuenta.", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Pantalla provisional: pendiente de portar (fases 4-7).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        BarberButton(text = "Cerrar sesión", onClick = { viewModel.onIntent(HomeIntent.SignOut) })
    }
}
