package com.barberia.feature.booking.presentation.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.messageRes
import com.barberia.feature.booking.presentation.invite.RedeemInviteCard
import com.barberia.feature.booking.presentation.loyalty.LoyaltyCard
import com.barberia.feature.booking.presentation.loyalty.LoyaltyViewModel
import com.barberia.feature.booking.presentation.common.shortLabel
import com.barberia.feature.catalog.domain.model.TimeRange
import org.koin.androidx.compose.koinViewModel

@Composable
fun ClientHomeRoute(onBook: () -> Unit, onSignOut: () -> Unit, viewModel: ClientHomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AskNotificationPermissionOnce()
    ClientHomeScreen(state, viewModel::onIntent, onBook, onSignOut)
}

@Composable
fun ClientHomeScreen(
    state: ClientHomeState,
    onIntent: (ClientHomeIntent) -> Unit,
    onBook: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val shop = state.shop
        if (shop == null) {
            Text(stringResource(R.string.booking_join_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.booking_join_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            BarberTextField(
                state.code, { onIntent(ClientHomeIntent.CodeChanged(it)) }, stringResource(R.string.booking_code),
                error = state.failure?.let { stringResource(it.messageRes()) },
            )
            BarberButton(stringResource(R.string.booking_join), { onIntent(ClientHomeIntent.Join) }, isLoading = state.joining)
        } else {
            Text(shop.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            val loyalty: LoyaltyViewModel = koinViewModel()
            val loyaltyState by loyalty.state.collectAsStateWithLifecycle()
            LoyaltyCard(viewModel = loyalty)
            BarberButton(stringResource(R.string.booking_book), onBook, enabled = loyaltyState.canBook) // U10
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.booking_my_appointments), style = MaterialTheme.typography.titleMedium)
            state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
            if (state.appointments.isEmpty()) {
                Text(stringResource(R.string.booking_no_appointments), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.appointments.forEach { AppointmentCard(it, state.cancellingId == it.id, onIntent) }
            TextButton(onClick = { onIntent(ClientHomeIntent.Leave) }) { Text(stringResource(R.string.booking_change_shop)) }
        }
        RedeemInviteCard()
        TextButton(onClick = onSignOut) { Text(stringResource(R.string.booking_sign_out)) }
    }
}

@Composable
private fun AppointmentCard(a: Appointment, cancelling: Boolean, onIntent: (ClientHomeIntent) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.booking_appt_line, a.serviceName, a.date.shortLabel(), TimeRange.label(a.startMinute)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.booking_with, a.barberName, a.price.label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a.status == com.barberia.feature.booking.domain.model.AppointmentStatus.BOOKED) {
                TextButton(onClick = { onIntent(ClientHomeIntent.Cancel(a.id)) }, enabled = !cancelling) {
                    Text(stringResource(R.string.booking_cancel), color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(stringResource(R.string.booking_cancelled), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
