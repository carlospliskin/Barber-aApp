package com.barberia.feature.booking.presentation.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.usecase.ForgivePenaltyUseCase
import com.barberia.feature.booking.domain.usecase.ObserveClientsUseCase
import com.barberia.feature.booking.domain.usecase.RedeemRewardUseCase
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.asBookingFailure
import com.barberia.feature.booking.presentation.common.messageRes
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel

data class ClientsState(
    val tenantId: String? = null,
    val clients: List<ClientRecord> = emptyList(),
    val busyUid: String? = null,
    val failure: BookingFailure? = null,
    val now: Instant = Instant.EPOCH,
    val loyalty: LoyaltyPolicy = LoyaltyPolicy()
) : UiState {
    fun isPenalized(c: ClientRecord) = c.isPenalized(now)
    fun rewards(c: ClientRecord) = c.availableRewards(loyalty)
}

sealed interface ClientsIntent : UiIntent {
    data class Redeem(val uid: String) : ClientsIntent
    data class Forgive(val uid: String) : ClientsIntent
    data object DismissFailure : ClientsIntent
}

sealed interface ClientsEffect : UiEffect

/** P4/P5: clientes del dueño, premios y quitar penalizaciones. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModel(
    observeUser: ObserveCurrentUserUseCase,
    observeClients: ObserveClientsUseCase,
    private val redeem: RedeemRewardUseCase,
    private val forgive: ForgivePenaltyUseCase,
    loyalty: LoyaltyPolicy,
    private val clock: Clock
) : MviViewModel<ClientsState, ClientsIntent, ClientsEffect>(ClientsState(loyalty = loyalty)) {

    init {
        launchInScope {
            observeUser()
                .map { u -> u?.staffTenantId.takeIf { u?.staffRole == StaffRole.OWNER } }
                .distinctUntilChanged()
                .flatMapLatest { t ->
                    setState { copy(tenantId = t) }
                    // El catch va adentro: un error no corta las actualizaciones siguientes.
                    if (t == null) emptyFlow() else observeClients(t).catch { e -> setState { copy(failure = e.asBookingFailure()) } }
                }
                .collect { list -> setState { copy(clients = list, now = clock.instant()) } }
        }
    }

    override suspend fun handleIntent(intent: ClientsIntent) {
        when (intent) {
            is ClientsIntent.Redeem -> act(intent.uid) { t -> redeem(t, intent.uid) }
            is ClientsIntent.Forgive -> act(intent.uid) { t -> forgive(t, intent.uid) }
            ClientsIntent.DismissFailure -> setState { copy(failure = null) }
        }
    }

    private suspend fun act(uid: String, action: suspend (String) -> BookingResult<Unit>) {
        val tenantId = currentState.tenantId ?: return
        setState { copy(busyUid = uid, failure = null) }
        val r = action(tenantId)
        setState { copy(busyUid = null, failure = (r as? AppResult.Failure)?.error) }
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-MX"))

fun Instant.dayLabel(zone: ZoneId = ZoneId.of("America/Mexico_City")): String = atZone(zone).format(DATE)

@Composable
fun ClientsRoute(onBack: () -> Unit, viewModel: ClientsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.booking_back)) }
            Text(stringResource(R.string.clients_title), style = MaterialTheme.typography.titleLarge)
        }
        state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        if (state.clients.isEmpty()) {
            Text(stringResource(R.string.clients_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.clients.forEach { c ->
            val busy = state.busyUid == c.uid
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.clients_line, c.completed, c.noShows, state.rewards(c)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.isPenalized(c)) {
                        Text(
                            stringResource(R.string.clients_penalized, c.penaltyUntil!!.dayLabel()),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row {
                        if (state.rewards(c) > 0) {
                            TextButton(onClick = { viewModel.onIntent(ClientsIntent.Redeem(c.uid)) }, enabled = !busy) {
                                Text(stringResource(R.string.clients_redeem))
                            }
                        }
                        if (c.penaltyUntil != null || c.noShows > 0) {
                            TextButton(onClick = { viewModel.onIntent(ClientsIntent.Forgive(c.uid)) }, enabled = !busy) {
                                Text(stringResource(R.string.clients_forgive))
                            }
                        }
                    }
                }
            }
        }
    }
}
