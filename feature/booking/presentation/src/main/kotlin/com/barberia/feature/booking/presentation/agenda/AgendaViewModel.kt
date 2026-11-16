package com.barberia.feature.booking.presentation.agenda

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.usecase.CancelByStaffUseCase
import com.barberia.feature.booking.domain.usecase.DaySummary
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.MarkNoShowUseCase
import com.barberia.feature.booking.domain.usecase.NoShowOutcome
import com.barberia.feature.booking.domain.usecase.ObserveAgendaUseCase
import com.barberia.feature.booking.domain.usecase.UndoMarkUseCase
import com.barberia.feature.booking.domain.usecase.summarize
import com.barberia.feature.booking.presentation.common.asBookingFailure
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class AgendaState(
    val tenantId: String? = null,
    /** Dueño: puede ver a todos y filtrar. Barbero: solo lo suyo (A2). */
    val canFilter: Boolean = false,
    val barbers: List<Barber> = emptyList(),
    val barberFilter: String? = null,
    val date: LocalDate? = null,
    val appointments: List<Appointment> = emptyList(),
    val summary: DaySummary = summarize(emptyList()),
    val busyId: String? = null,
    val failure: BookingFailure? = null,
    /** A16-d: sin acceso a la barbería (no se ofrece agendar). Aparte de [failure], que se puede cerrar. */
    val noAccess: Boolean = false,
    /** F7-3: resultado del último no-show que penalizó (para avisar cuántas citas se cancelaron). */
    val penaltyNotice: NoShowOutcome? = null,
    /** Momento en que se calculó la vista; decide qué acciones se ofrecen (U7). */
    val now: Instant = Instant.EPOCH
) : UiState {
    /** X2: quedaron citas futuras del penalizado sin cancelar (p. ej. sin red). */
    val hasPendingCancellations: Boolean get() = (penaltyNotice?.failedToCancel ?: 0) > 0

    fun canComplete(a: Appointment) = a.status == AppointmentStatus.BOOKED && !now.isBefore(a.startsAt)

    /** W4: cancelar solo antes de la hora. */
    fun canCancel(a: Appointment) = a.status == AppointmentStatus.BOOKED && now.isBefore(a.startsAt)

    /** F7-2: solo el dueño (el que filtra) y dentro de 15 min. */
    fun canUndo(a: Appointment) = canFilter && a.canUndo(now)

    fun canNoShow(a: Appointment) = a.status == AppointmentStatus.BOOKED && !now.isBefore(a.startsAt.plus(MarkNoShowUseCase.NO_SHOW_GRACE))
}

sealed interface AgendaIntent : UiIntent {
    data object PreviousDay : AgendaIntent
    data object NextDay : AgendaIntent
    data object Today : AgendaIntent
    data class FilterBarber(val barberId: String?) : AgendaIntent
    data class Complete(val id: String) : AgendaIntent
    data class NoShow(val id: String) : AgendaIntent
    data class Cancel(val id: String) : AgendaIntent
    data class Undo(val id: String) : AgendaIntent
    data object DismissFailure : AgendaIntent
}

sealed interface AgendaEffect : UiEffect

private data class Query(val tenantId: String, val date: LocalDate, val barberId: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val getBarbershop: GetBarbershopUseCase,
    observeCatalog: ObserveCatalogUseCase,
    observeAgenda: ObserveAgendaUseCase,
    private val complete: MarkCompletedUseCase,
    private val noShow: MarkNoShowUseCase,
    private val cancel: CancelByStaffUseCase,
    private val undo: UndoMarkUseCase,
    private val clock: Clock
) : MviViewModel<AgendaState, AgendaIntent, AgendaEffect>(AgendaState()) {

    private var zone: ZoneId = ZoneId.of("America/Mexico_City")
    private val query = MutableStateFlow<Query?>(null)

    init {
        launchInScope {
            observeUser()
                .map { u -> Triple(u?.staffTenantId, u?.staffRole, u?.staffBarberId) }
                .distinctUntilChanged()
                .collect { (tenantId, role, barberId) ->
                    if (tenantId == null || role == null) return@collect
                    (getBarbershop(tenantId) as? AppResult.Success)?.value?.let { zone = it.zone }
                    val isOwner = role == StaffRole.OWNER
                    val filter = if (isOwner) null else barberId
                    setState { copy(tenantId = tenantId, canFilter = isOwner, barberFilter = filter, date = today()) }
                    query.value = Query(tenantId, today(), filter)
                }
        }
        launchInScope {
            query.flatMapLatest { q -> if (q == null) emptyFlow() else observeCatalog(q.tenantId).catch { } }
                .collect { c -> setState { copy(barbers = c.barbers) } }
        }
        launchInScope {
            // F7-2-c/U7: con la agenda abierta, la hora avanza sola (aparece "Atendida", se va "Deshacer").
            while (true) {
                delay(TICK_MS)
                setState { copy(now = clock.instant()) }
            }
        }
        launchInScope {
            // A3: solo las citas del día consultado.
            query.flatMapLatest { q ->
                if (q == null) return@flatMapLatest emptyFlow()
                // El catch va adentro: tras un error, cambiar de día o de barbero vuelve a cargar.
                observeAgenda(q.tenantId, q.date, q.barberId).catch { e ->
                    // A16: si se pierde el acceso no se dejan a la vista las citas que quedaron en caché.
                    val f = e.asBookingFailure()
                    setState {
                        copy(
                            failure = f,
                            noAccess = f == BookingFailure.NoAccess,
                            appointments = emptyList(),
                            summary = summarize(emptyList())
                        )
                    }
                }
            }
                .collect { list ->
                    setState {
                        copy(
                            appointments = list,
                            summary = summarize(list),
                            now = clock.instant(),
                            noAccess = false,
                            failure = failure.takeUnless { it == BookingFailure.NoConnection || it == BookingFailure.NoAccess }
                        )
                    }
                }
        }
    }

    override suspend fun handleIntent(intent: AgendaIntent) {
        when (intent) {
            AgendaIntent.PreviousDay -> moveTo { it.minusDays(1) }

            AgendaIntent.NextDay -> moveTo { it.plusDays(1) }

            AgendaIntent.Today -> moveTo { today() }

            is AgendaIntent.FilterBarber -> if (currentState.canFilter) {
                setState { copy(barberFilter = intent.barberId) }
                query.value = query.value?.copy(barberId = intent.barberId)
            }

            is AgendaIntent.Complete -> act(intent.id) { complete(it) }

            is AgendaIntent.NoShow -> act(intent.id) { a ->
                when (val r = noShow(a)) {
                    is AppResult.Success -> {
                        if (r.value.penalized) setState { copy(penaltyNotice = r.value) }
                        AppResult.Success(Unit)
                    }

                    is AppResult.Failure -> r
                }
            }

            is AgendaIntent.Cancel -> act(intent.id) { cancel(it) }

            is AgendaIntent.Undo -> if (currentState.canFilter) {
                act(intent.id) { a ->
                    undo(a).also { r ->
                        // si se deshizo el no-show que penalizó, el aviso de penalización ya no es cierto
                        if (r is AppResult.Success && a.status == AppointmentStatus.NO_SHOW) setState { copy(penaltyNotice = null) }
                    }
                }
            }

            AgendaIntent.DismissFailure -> setState { copy(failure = null, penaltyNotice = null) }
        }
    }

    private fun today(): LocalDate = LocalDateTime.ofInstant(clock.instant(), zone).toLocalDate()

    private fun moveTo(change: (LocalDate) -> LocalDate) {
        val current = currentState.date ?: return
        val next = change(current)
        setState { copy(date = next) }
        query.value = query.value?.copy(date = next)
    }

    private suspend fun act(id: String, action: suspend (Appointment) -> BookingResult<Unit>) {
        val appointment = currentState.appointments.find { it.id == id } ?: return
        setState { copy(busyId = id, failure = null) }
        // E1: un error inesperado no deja los botones de esa cita bloqueados para siempre.
        val r = try {
            action(appointment)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onUnexpectedError(e)
            AppResult.Failure(BookingFailure.Unknown(e.message))
        }
        setState { copy(busyId = null, failure = (r as? AppResult.Failure)?.error) }
    }

    private companion object {
        const val TICK_MS = 60_000L
    }
}
