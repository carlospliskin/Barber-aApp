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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.app.R
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.presentation.account.StaffAccessCard
import com.barberia.feature.auth.presentation.common.labelRes
import com.barberia.feature.auth.presentation.verify.VerifyEmailCard
import com.barberia.feature.booking.presentation.client.ClientHomeRoute
import com.barberia.feature.booking.presentation.owner.ShareCodeCard
import com.barberia.feature.booking.presentation.owner.ShopZoneCard
import org.koin.androidx.compose.koinViewModel

/** Home por modo: cliente → reservas; dueño → código, agenda y catálogo; barbero → agenda. */
@Composable
fun HomeRoute(
    mode: AppMode,
    onOpenCatalog: () -> Unit,
    onBook: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenClients: () -> Unit,
    onDeleteAccount: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    if (mode == AppMode.CLIENT) {
        ClientHomeRoute(onBook = onBook, onSignOut = { viewModel.onIntent(HomeIntent.SignOut) }, onDeleteAccount = onDeleteAccount)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return // X1: no se decide nada (ni se crean códigos) hasta conocer al usuario
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // U8: etiqueta en español (antes mostraba el nombre interno: "barber").
        Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(state.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // A14: el dueño opera solo con el correo verificado (las reglas también lo exigen).
        val ownerLocked = mode == AppMode.OWNER && !state.emailVerified
        if (!state.emailVerified && mode != AppMode.OWNER) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_check_email), color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(24.dp))
        if (ownerLocked) {
            VerifyEmailCard()
            Spacer(Modifier.height(16.dp))
        } else if (mode == AppMode.OWNER) {
            ShareCodeCard()
            Spacer(Modifier.height(12.dp))
            ShopZoneCard()
            Spacer(Modifier.height(16.dp))
        } else if (mode == AppMode.BARBER) {
            StaffAccessCard() // A7-b/A16: solo aparece si perdió el acceso
            Spacer(Modifier.height(16.dp))
        }
        BarberButton(text = stringResource(R.string.home_agenda), onClick = onOpenAgenda, enabled = !ownerLocked)
        Spacer(Modifier.height(12.dp))
        if (mode == AppMode.OWNER) {
            BarberButton(text = stringResource(R.string.home_catalog), onClick = onOpenCatalog, enabled = !ownerLocked)
            Spacer(Modifier.height(12.dp))
            BarberButton(text = stringResource(R.string.home_clients), onClick = onOpenClients, enabled = !ownerLocked)
            Spacer(Modifier.height(12.dp))
        }
        BarberButton(text = stringResource(R.string.home_sign_out), onClick = { viewModel.onIntent(HomeIntent.SignOut) })
        TextButton(onClick = onDeleteAccount) {
            Text(stringResource(R.string.home_delete_account), color = MaterialTheme.colorScheme.error)
        }
    }
}
