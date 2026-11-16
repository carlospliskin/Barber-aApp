package com.barberia.feature.booking.presentation.book

import com.barberia.feature.booking.presentation.common.asBookingFailure
import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.BookingRequest
import com.barberia.feature.booking.domain.usecase.BookAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class BookingState(
    val shop: Barbershop? = null,
    val clientUid: String? = null,
    val clientName: String = "",
    val services: List<Service> = emptyList(),
    val barbers: List<Barber> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val selectedServiceId: String? = null,
    val selectedBarberId: String? = null,
    val selectedDate: LocalDate? = null,
    val slots: List<Int> = emptyList(),
    val slotsLoading: Boolean = false,
    val selectedSlot: Int? = null,
    val isBooking: Boolean = false,
    val failure: BookingFailure? = null,
) : UiState {
    val selectedService: Service? get() = services.find { it.id == selectedServiceId }

    /** U2: días que trabaja el barbero elegido (vacío = todavía no elige barbero). */
    val workingDays: Set<DayOfWeek>
        get() = barbers.find { it.id == selectedBarberId }?.schedule?.days?.keys.orEmpty()

    /** U2 + N2: ni días que no trabaja ni días libres completos. */
    fun isSelectable(date: LocalDate) =
        selectedBarberId == null || barbers.find { it.id == selectedBarberId }?.worksOn(date) == true
    val canConfirm: Boolean get() = selectedSlot != null && !isBooking
}

sealed interface BookingIntent : UiIntent {
    data class SelectService(val id: String) : BookingIntent
    data class SelectBarber(val id: String) : BookingIntent
    data class SelectDate(val date: LocalDate) : BookingIntent
    data class SelectSlot(val minute: Int) : BookingIntent
    data object Confirm : BookingIntent
    data object DismissFailure : BookingIntent
}

sealed interface BookingEffect : UiEffect {
    data object Booked : BookingEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModel(
    observeUser: ObserveCurrentUserUseCase,
    observeJoined: ObserveJoinedBarbershopUseCase,
    observeCatalog: ObserveCatalogUseCase,
    private val getSlots: GetAvailableSlotsUseCase,
    private val book: BookAppointmentUseCase,
    private val policy: BookingPolicy,
    private val clock: Clock,
) : MviViewModel<BookingState, BookingIntent, BookingEffect>(BookingState()) {

    init {
        launchInScope {
            observeUser().collect { u -> setState { copy(clientUid = u?.uid, clientName = u?.displayName.orEmpty()) } }
        }
        launchInScope {
            observeUser()
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid -> if (uid == null) emptyFlow() else observeJoined(uid) }
                .map { (it as? AppResult.Success)?.value }
                .flatMapLatest { shop ->
                    if (shop == null) return@flatMapLatest emptyFlow()
                    val today = LocalDateTime.ofInstant(clock.instant(), shop.zone).toLocalDate()
                    setState { copy(shop = shop, dates = (0 until DAYS_SHOWN).map { today.plusDays(it.toLong()) }) }
                    observeCatalog(shop.id).catch { e -> setState { copy(failure = e.asBookingFailure()) } }
                }
                .collect { c -> setState { copy(services = c.bookableServices, barbers = c.bookableBarbers) } }
        }
    }

    override suspend fun handleIntent(intent: BookingIntent) {
        when (intent) {
            // B16: cualquier cambio de servicio, barbero o día invalida la hora elegida.
            is BookingIntent.SelectService -> select { copy(selectedServiceId = intent.id) }
            is BookingIntent.SelectBarber -> select {
                val next = copy(selectedBarberId = intent.id)
                next.copy(selectedDate = selectedDate?.takeIf { next.isSelectable(it) })
            }
            is BookingIntent.SelectDate -> if (currentState.isSelectable(intent.date)) select { copy(selectedDate = intent.date) }
            is BookingIntent.SelectSlot -> setState { copy(selectedSlot = intent.minute.takeIf { it in slots }) }
            BookingIntent.DismissFailure -> setState { copy(failure = null) }
            BookingIntent.Confirm -> confirm()
        }
    }

    private suspend fun select(change: BookingState.() -> BookingState) {
        setState { change().copy(selectedSlot = null, slots = emptyList(), failure = null) }
        loadSlots()
    }

    private suspend fun loadSlots() {
        val s = currentState
        val shop = s.shop ?: return
        val serviceId = s.selectedServiceId ?: return
        val barberId = s.selectedBarberId ?: return
        val date = s.selectedDate ?: return
        setState { copy(slotsLoading = true) }
        val r = getSlots(shop.id, shop.zone, barberId, serviceId, date)
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
        val shop = s.shop ?: return
        val uid = s.clientUid ?: return
        setState { copy(isBooking = true, failure = null) }
        val r = book(
            BookingRequest(shop.id, shop.zone, uid, s.clientName, s.selectedBarberId!!, s.selectedServiceId!!, s.selectedDate!!, s.selectedSlot!!),
        )
        when (r) {
            is AppResult.Success -> {
                setState { copy(isBooking = false, selectedSlot = null) }
                sendEffect(BookingEffect.Booked)
            }
            is AppResult.Failure -> {
                setState { copy(isBooking = false, failure = r.error, selectedSlot = null) }
                if (r.error == BookingFailure.SlotTaken || r.error == BookingFailure.SlotUnavailable) loadSlots() // B15
            }
        }
    }

    private companion object {
        const val DAYS_SHOWN = 14
    }
}
