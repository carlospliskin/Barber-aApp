package com.barberia.app.home

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
import com.barberia.feature.booking.presentation.client.ClientHomeRoute
import com.barberia.feature.booking.presentation.owner.ShareCodeCard
import org.koin.androidx.compose.koinViewModel

/** Home por modo: cliente → reservas; dueño → código + catálogo; barbero → provisional (fase 6). */
@Composable
fun HomeRoute(mode: AppMode, onOpenCatalog: () -> Unit, onBook: () -> Unit, viewModel: HomeViewModel = koinViewModel()) {
    if (mode == AppMode.CLIENT) {
        ClientHomeRoute(onBook = onBook, onSignOut = { viewModel.onIntent(HomeIntent.SignOut) })
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Modo ${mode.name.lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(state.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.emailVerified) {
            Spacer(Modifier.height(8.dp))
            Text("Revisa tu correo para verificar la cuenta.", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(24.dp))
        if (mode == AppMode.OWNER) {
            ShareCodeCard()
            Spacer(Modifier.height(16.dp))
            BarberButton(text = "Catálogo", onClick = onOpenCatalog)
            Spacer(Modifier.height(12.dp))
        } else {
            Text("Agenda del barbero: pendiente (fase 6).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
        BarberButton(text = "Cerrar sesión", onClick = { viewModel.onIntent(HomeIntent.SignOut) })
    }
}
