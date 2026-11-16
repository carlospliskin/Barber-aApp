package com.barberia.feature.booking.presentation.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberChip
import com.barberia.core.mvi.CollectEffects
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.messageRes
import com.barberia.feature.booking.presentation.common.shortLabel
import com.barberia.feature.catalog.domain.model.TimeRange
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookingRoute(onDone: () -> Unit, onBack: () -> Unit, viewModel: BookingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == BookingEffect.Booked) onDone() }
    BookingScreen(state, viewModel::onIntent, onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingScreen(state: BookingState, onIntent: (BookingIntent) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.booking_back)) }
            Text(state.shop?.name ?: stringResource(R.string.booking_title), style = MaterialTheme.typography.titleLarge)
        }
        if (state.shop != null && (state.services.isEmpty() || state.barbers.isEmpty())) {
            Text(stringResource(R.string.booking_no_catalog), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Step(stringResource(R.string.booking_step_service)) {
            state.services.forEach { s ->
                BarberChip(
                    text = "${s.name} · ${s.durationMinutes} min · ${s.price.label}",
                    selected = s.id == state.selectedServiceId,
                    onClick = { onIntent(BookingIntent.SelectService(s.id)) },
                )
            }
        }
        Step(stringResource(R.string.booking_step_barber)) {
            state.barbers.forEach { b ->
                BarberChip(
                    text = b.name,
                    selected = b.id == state.selectedBarberId,
                    onClick = { onIntent(BookingIntent.SelectBarber(b.id)) },
                )
            }
        }
        Text(stringResource(R.string.booking_step_day), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.dates) { d ->
                BarberChip(
                    text = d.shortLabel(),
                    selected = d == state.selectedDate,
                    onClick = { onIntent(BookingIntent.SelectDate(d)) },
    enabled = state.isSelectable(d), // U2
                )
            }
        }
        if (state.selectedDate != null && state.selectedServiceId != null && state.selectedBarberId != null) {
            Text(stringResource(R.string.booking_step_time), style = MaterialTheme.typography.titleMedium)
            when {
                state.slotsLoading -> CircularProgressIndicator()
                state.slots.isEmpty() ->
                    Text(stringResource(R.string.booking_no_slots), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.slots.forEach { m ->
                        BarberChip(
                    text = TimeRange.label(m),
                    selected = m == state.selectedSlot,
                    onClick = { onIntent(BookingIntent.SelectSlot(m)) },
                )
                    }
                }
            }
        }
        state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        val slot = state.selectedSlot
        val date = state.selectedDate
        if (slot != null && date != null) {
            BarberButton(
                stringResource(R.string.booking_confirm, date.shortLabel(), TimeRange.label(slot)),
                { onIntent(BookingIntent.Confirm) },
                isLoading = state.isBooking,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
