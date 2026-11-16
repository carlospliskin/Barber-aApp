package com.barberia.feature.booking.presentation.walkin

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberChip
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.mvi.CollectEffects
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.WalkInRequest
import com.barberia.feature.booking.domain.usecase.BookWalkInUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.messageRes
import com.barberia.feature.booking.presentation.common.shortLabel
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ── MVI ──────────────────────────────────────────────────────────────────────

data class WalkInState(
    val tenantId: String? = null,
    val zone: ZoneId? = null,
    /** Dueño: elige barbero. Barbero: solo en su propia agenda (las reglas lo exigen). */
    val canChooseBarber: Boolean = false,
    val services: List<Service> = emptyList(),
    val barbers: List<Barber> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val name: String = "",
    val phone: String = "",
    val selectedServiceId: String? = null,
    val selectedBarberId: String? = null,
    val selectedDate: LocalDate? = null,
    val slots: List<Int> = emptyList(),
    val slotsLoading: Boolean = false,
    val selectedSlot: Int? = null,
    val isBooking: Boolean = false,
    val failure: BookingFailure? = null,
) : UiState {
    fun isSelectable(date: LocalDate) =
        selectedBarberId == null || barbers.find { it.id == selectedBarberId }?.worksOn(date) == true

    val canConfirm: Boolean get() = selectedSlot != null && name.isNotBlank() && !isBooking
}

sealed interface WalkInIntent : UiIntent {
    data class NameChanged(val value: String) : WalkInIntent
    data class PhoneChanged(val value: String) : WalkInIntent
    data class SelectService(val id: String) : WalkInIntent
    data class SelectBarber(val id: String) : WalkInIntent
    data class SelectDate(val date: LocalDate) : WalkInIntent
    data class SelectSlot(val minute: Int) : WalkInIntent
    data object Confirm : WalkInIntent
}

sealed interface WalkInEffect : UiEffect {
    data object Booked : WalkInEffect
}

/** N1: el personal agenda a quien llama o llega al local (sin cuenta en la app). */
class WalkInViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val getBarbershop: GetBarbershopUseCase,
    private val observeCatalog: ObserveCatalogUseCase,
    private val getSlots: GetAvailableSlotsUseCase,
    private val book: BookWalkInUseCase,
    private val clock: Clock,
) : MviViewModel<WalkInState, WalkInIntent, WalkInEffect>(WalkInState()) {

    init {
        launchInScope {
            observeUser()
                .map { u -> Triple(u?.staffTenantId, u?.staffRole, u?.staffBarberId) }
                .distinctUntilChanged()
                .collect { (tenantId, role, barberId) ->
                    if (tenantId == null || role == null) return@collect
                    val zone = (getBarbershop(tenantId) as? AppResult.Success)?.value?.zone ?: DEFAULT_ZONE
                    val today = LocalDateTime.ofInstant(clock.instant(), zone).toLocalDate()
                    val isOwner = role == StaffRole.OWNER
                    setState {
                        copy(
                            tenantId = tenantId, zone = zone, canChooseBarber = isOwner,
                            selectedBarberId = if (isOwner) null else barberId,
                            dates = (0 until DAYS_SHOWN).map { today.plusDays(it.toLong()) }, selectedDate = today,
                        )
                    }
                    observeCatalog(tenantId)
                        .catch { setState { copy(failure = BookingFailure.NoConnection) } }
                        .first()
                        .let { c -> setState { copy(services = c.bookableServices, barbers = c.bookableBarbers) } }
                }
        }
    }

    override suspend fun handleIntent(intent: WalkInIntent) {
        when (intent) {
            is WalkInIntent.NameChanged -> setState { copy(name = intent.value, failure = null) }
            is WalkInIntent.PhoneChanged -> setState { copy(phone = intent.value, failure = null) }
            is WalkInIntent.SelectService -> select { copy(selectedServiceId = intent.id) }
            is WalkInIntent.SelectBarber -> if (currentState.canChooseBarber) select { copy(selectedBarberId = intent.id) }
            is WalkInIntent.SelectDate -> if (currentState.isSelectable(intent.date)) select { copy(selectedDate = intent.date) }
            is WalkInIntent.SelectSlot -> setState { copy(selectedSlot = intent.minute.takeIf { it in slots }) }
            WalkInIntent.Confirm -> confirm()
        }
    }

    private suspend fun select(change: WalkInState.() -> WalkInState) {
        setState { change().copy(selectedSlot = null, slots = emptyList(), failure = null) }
        loadSlots()
    }

    private suspend fun loadSlots() {
        val s = currentState
        val tenantId = s.tenantId ?: return
        val zone = s.zone ?: return
        val serviceId = s.selectedServiceId ?: return
        val barberId = s.selectedBarberId ?: return
        val date = s.selectedDate ?: return
        setState { copy(slotsLoading = true) }
        val r = getSlots(tenantId, zone, barberId, serviceId, date, forStaff = true)
        setState {
            when (r) {
                is AppResult.Success -> copy(slotsLoading = false, slots = r.value)
                is AppResult.Failure -> copy(slotsLoading = false, failure = r.error)
            }
        }
    }

    private suspend fun confirm() {
        val s = currentState
        if (!s.canConfirm) return
        val req = WalkInRequest(
            s.tenantId ?: return, s.zone ?: return, s.name, s.phone.ifBlank { null },
            s.selectedBarberId ?: return, s.selectedServiceId ?: return, s.selectedDate ?: return, s.selectedSlot ?: return,
        )
        setState { copy(isBooking = true, failure = null) }
        when (val r = book(req)) {
            is AppResult.Success -> {
                setState { copy(isBooking = false) }
                sendEffect(WalkInEffect.Booked)
            }
            is AppResult.Failure -> {
                setState { copy(isBooking = false, failure = r.error, selectedSlot = null) }
                if (r.error == BookingFailure.SlotTaken || r.error == BookingFailure.SlotUnavailable) loadSlots()
            }
        }
    }

    private companion object {
        const val DAYS_SHOWN = 14
        val DEFAULT_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun WalkInRoute(onDone: () -> Unit, onBack: () -> Unit, viewModel: WalkInViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == WalkInEffect.Booked) onDone() }
    WalkInScreen(state, viewModel::onIntent, onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalkInScreen(state: WalkInState, onIntent: (WalkInIntent) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.booking_back)) }
            Text(stringResource(R.string.walkin_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.walkin_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        BarberTextField(state.name, { onIntent(WalkInIntent.NameChanged(it)) }, stringResource(R.string.walkin_name))
        BarberTextField(
            state.phone, { onIntent(WalkInIntent.PhoneChanged(it)) }, stringResource(R.string.walkin_phone),
            keyboardType = KeyboardType.Phone,
        )
        Text(stringResource(R.string.booking_step_service), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.services.forEach { s ->
                BarberChip("${s.name} · ${s.durationMinutes} min", s.id == state.selectedServiceId, { onIntent(WalkInIntent.SelectService(s.id)) })
            }
        }
        if (state.canChooseBarber) {
            Text(stringResource(R.string.booking_step_barber), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.barbers.forEach { b ->
                    BarberChip(b.name, b.id == state.selectedBarberId, { onIntent(WalkInIntent.SelectBarber(b.id)) })
                }
            }
        }
        Text(stringResource(R.string.booking_step_day), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.dates) { d ->
                BarberChip(d.shortLabel(), d == state.selectedDate, { onIntent(WalkInIntent.SelectDate(d)) }, enabled = state.isSelectable(d))
            }
        }
        if (state.selectedServiceId != null && state.selectedBarberId != null) {
            Text(stringResource(R.string.booking_step_time), style = MaterialTheme.typography.titleMedium)
            when {
                state.slotsLoading -> CircularProgressIndicator()
                state.slots.isEmpty() -> Text(stringResource(R.string.booking_no_slots), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.slots.forEach { m ->
                        BarberChip(TimeRange.label(m), m == state.selectedSlot, { onIntent(WalkInIntent.SelectSlot(m)) })
                    }
                }
            }
        }
        state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        val slot = state.selectedSlot
        val date = state.selectedDate
        if (slot != null && date != null) {
            BarberButton(
                stringResource(R.string.walkin_confirm, date.shortLabel(), TimeRange.label(slot)),
                { onIntent(WalkInIntent.Confirm) },
                isLoading = state.isBooking,
                enabled = state.canConfirm,
            )
        }
    }
}
