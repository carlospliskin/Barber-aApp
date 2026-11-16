package com.barberia.feature.booking.presentation.client

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.usecase.CancelAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.CancelReminderUseCase
import com.barberia.feature.booking.domain.usecase.SyncRemindersUseCase
import com.barberia.feature.booking.domain.usecase.JoinBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.LeaveBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyAppointmentsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Clock
import java.time.LocalDateTime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import com.barberia.feature.booking.presentation.common.asBookingFailure
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class ClientHomeState(
    val uid: String? = null,
    val shop: Barbershop? = null,
    val loading: Boolean = true,
    val code: String = "",
    val joining: Boolean = false,
    val appointments: List<Appointment> = emptyList(),
    val cancellingId: String? = null,
    val failure: BookingFailure? = null,
    /** Hora con la que se decide qué se ofrece (se actualiza al llegar las citas). */
    val now: java.time.Instant = java.time.Instant.EPOCH,
    val cancelWindow: java.time.Duration = java.time.Duration.ofHours(2),
) : UiState {
    /** B7: el cliente cancela hasta 2 h antes; después ya no se le ofrece (antes daba error al tocar). */
    fun canCancel(a: Appointment) =
        a.status == com.barberia.feature.booking.domain.model.AppointmentStatus.BOOKED && now.isBefore(a.startsAt.minus(cancelWindow))
}

sealed interface ClientHomeIntent : UiIntent {
    data class CodeChanged(val value: String) : ClientHomeIntent
    data object Join : ClientHomeIntent
    data object Leave : ClientHomeIntent
    data class Cancel(val appointmentId: String) : ClientHomeIntent
    data object DismissFailure : ClientHomeIntent
}

sealed interface ClientHomeEffect : UiEffect

@OptIn(ExperimentalCoroutinesApi::class)
class ClientHomeViewModel(
    observeUser: ObserveCurrentUserUseCase,
    observeJoined: ObserveJoinedBarbershopUseCase,
    private val join: JoinBarbershopUseCase,
    private val leave: LeaveBarbershopUseCase,
    observeMine: ObserveMyAppointmentsUseCase,
    private val cancel: CancelAppointmentUseCase,
    private val clock: Clock,
    private val syncReminders: SyncRemindersUseCase,
    private val cancelReminder: CancelReminderUseCase,
    policy: BookingPolicy,
) : MviViewModel<ClientHomeState, ClientHomeIntent, ClientHomeEffect>(
    ClientHomeState(now = clock.instant(), cancelWindow = java.time.Duration.ofHours(policy.cancelHoursBefore)),
) {

    init {
        launchInScope {
            observeUser()
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(null to null) else observeJoined(uid).map { uid to it }
                }
                .flatMapLatest { (uid, joined) ->
                    val shop = (joined as? AppResult.Success)?.value
                    setState {
                        copy(
                            uid = uid,
                            shop = shop,
                            loading = false,
                            failure = (joined as? AppResult.Failure)?.error ?: failure,
                        )
                    }
                    if (uid == null || shop == null) flowOf(emptyList()) else observeMine(shop.id, uid, shop.zone)
                }
                .catch { e -> setState { copy(failure = e.asBookingFailure(), appointments = emptyList()) } }
                .collect { list ->
                    setState { copy(appointments = list, now = clock.instant()) }
                    syncReminders(list) // R3: también en un teléfono nuevo o tras reinstalar
                }
        }
    }

    override suspend fun handleIntent(intent: ClientHomeIntent) {
        when (intent) {
            is ClientHomeIntent.CodeChanged -> setState { copy(code = intent.value.uppercase(), failure = null) }
            ClientHomeIntent.DismissFailure -> setState { copy(failure = null) }
            ClientHomeIntent.Leave -> {
                val uid = currentState.uid ?: return
                val shop = currentState.shop ?: return
                val today = LocalDateTime.ofInstant(clock.instant(), shop.zone).toLocalDate()
                val r = leave(uid, shop.id, today)
                if (r is AppResult.Failure) setState { copy(failure = r.error) }
            }
            ClientHomeIntent.Join -> {
                setState { copy(joining = true, failure = null) }
                val uid = currentState.uid ?: return
                val r = join(currentState.code, uid)
                setState {
                    when (r) {
                        is AppResult.Success -> copy(joining = false, shop = r.value, code = "")
                        is AppResult.Failure -> copy(joining = false, failure = r.error)
                    }
                }
            }
            is ClientHomeIntent.Cancel -> {
                val appt = currentState.appointments.find { it.id == intent.appointmentId } ?: return
                val shop = currentState.shop ?: return
                val uid = currentState.uid ?: return
                setState { copy(cancellingId = appt.id) }
                val r = cancel(appt, shop.zone, uid)
                if (r is AppResult.Success) cancelReminder(appt.id) // R5
                setState { copy(cancellingId = null, failure = (r as? AppResult.Failure)?.error) }
            }
        }
    }
}
