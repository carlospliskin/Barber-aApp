package com.barberia.feature.booking.presentation.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberChip
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.messageRes
import com.barberia.feature.booking.presentation.common.shortLabel
import com.barberia.feature.catalog.domain.model.TimeRange
import org.koin.androidx.compose.koinViewModel

@Composable
fun AgendaRoute(onBack: () -> Unit, onAddWalkIn: () -> Unit, viewModel: AgendaViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AgendaScreen(state, viewModel::onIntent, onBack, onAddWalkIn)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendaScreen(state: AgendaState, onIntent: (AgendaIntent) -> Unit, onBack: () -> Unit, onAddWalkIn: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.booking_back)) }
            Text(stringResource(R.string.agenda_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // N1: quien llama o llega al local
            if (!state.noAccess) { // A16-d
                TextButton(onClick = onAddWalkIn, enabled = state.tenantId != null) { Text(stringResource(R.string.agenda_add_walkin)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onIntent(AgendaIntent.PreviousDay) }) { Text("‹") }
            Text(state.date?.shortLabel().orEmpty(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onIntent(AgendaIntent.NextDay) }) { Text("›") }
            TextButton(onClick = { onIntent(AgendaIntent.Today) }) { Text(stringResource(R.string.agenda_today)) }
        }
        if (state.canFilter) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarberChip(stringResource(R.string.agenda_all), state.barberFilter == null, { onIntent(AgendaIntent.FilterBarber(null)) })
                state.barbers.forEach { b ->
                    BarberChip(b.name, state.barberFilter == b.id, { onIntent(AgendaIntent.FilterBarber(b.id)) })
                }
            }
        }
        val s = state.summary
        Text(
            pluralStringResource(R.plurals.agenda_count, s.total, s.total) + " · " +
                stringResource(R.string.agenda_summary, s.completed, s.noShows, s.earned.label, s.expected.label),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        state.penaltyNotice?.let { n ->
            Text(
                pluralStringResource(R.plurals.agenda_penalty_notice, n.cancelledFuture, n.cancelledFuture),
                color = MaterialTheme.colorScheme.error
            )
            if (state.hasPendingCancellations) {
                Text(stringResource(R.string.agenda_penalty_pending, n.failedToCancel), color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.appointments.isEmpty()) {
            Text(stringResource(R.string.agenda_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.appointments.forEach {
            AgendaCard(
                it,
                state.busyId == it.id,
                state.canComplete(it),
                state.canNoShow(it),
                state.canCancel(it),
                state.canUndo(it),
                onIntent
            )
        }
    }
}

@Composable
private fun AgendaCard(
    a: Appointment,
    busy: Boolean,
    canComplete: Boolean,
    canNoShow: Boolean,
    canCancel: Boolean,
    canUndo: Boolean,
    onIntent: (AgendaIntent) -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            val who = if (a.isFromDeletedAccount) stringResource(R.string.agenda_deleted_account) else a.clientName
            Text(
                "${TimeRange.label(a.startMinute)}–${TimeRange.label(a.endMinute)} · $who",
                style = MaterialTheme.typography.titleSmall
            )
            Text("${a.serviceName} · ${a.barberName} · ${a.price.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.isWalkIn) {
                Text(
                    listOfNotNull(stringResource(R.string.agenda_walkin_tag), a.clientPhone).joinToString(" · "),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when (a.status) {
                AppointmentStatus.BOOKED -> Row {
                    // U7: solo se ofrece cuando ya se puede (antes daba error).
                    if (canComplete) {
                        TextButton(onClick = { onIntent(AgendaIntent.Complete(a.id)) }, enabled = !busy) {
                            Text(stringResource(R.string.agenda_complete))
                        }
                    }
                    if (canNoShow) {
                        TextButton(onClick = { onIntent(AgendaIntent.NoShow(a.id)) }, enabled = !busy) {
                            Text(stringResource(R.string.agenda_no_show))
                        }
                    }
                    if (canCancel) {
                        TextButton(onClick = { onIntent(AgendaIntent.Cancel(a.id)) }, enabled = !busy) {
                            Text(stringResource(R.string.booking_cancel), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (a.status == AppointmentStatus.COMPLETED) {
                        Text(stringResource(R.string.agenda_status_completed), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.agenda_status_no_show), color = MaterialTheme.colorScheme.error)
                    }
                    // F7-2: corregir un toque por error (solo el dueño, 15 min)
                    if (canUndo) {
                        TextButton(onClick = { onIntent(AgendaIntent.Undo(a.id)) }, enabled = !busy) {
                            Text(stringResource(R.string.agenda_undo))
                        }
                    }
                }

                AppointmentStatus.CANCELLED -> Text(
                    stringResource(R.string.booking_cancelled),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
