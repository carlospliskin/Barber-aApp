package com.barberia.feature.booking.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.catalog.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

class GetBarbershopUseCase(private val bookings: BookingRepository) {
    suspend operator fun invoke(tenantId: String) = bookings.barbershop(tenantId)
}

class ObserveAgendaUseCase(private val bookings: BookingRepository) {
    /** A2/A3: agenda de un día, ordenada por hora. */
    operator fun invoke(tenantId: String, date: LocalDate, barberId: String?): Flow<List<Appointment>> =
        bookings.observeDay(tenantId, date, barberId).map { list -> list.sortedBy { it.startMinute } }
}

class MarkCompletedUseCase(private val bookings: BookingRepository, private val clock: Clock) {
    suspend operator fun invoke(appointment: Appointment): BookingResult<Unit> =
        markStatus(
            bookings, appointment, AppointmentStatus.COMPLETED, earliest = appointment.startsAt, clock,
            updateClient = appointment.clientUid?.let { uid ->
                { c: ClientRecord? -> (c ?: ClientRecord.new(uid, appointment.clientName)).afterCompleted() } // P3
            },
        )
}

/** Resultado de marcar un no-show: si penalizó y cuántas citas futuras canceló (F7-3). */
data class NoShowOutcome(val penalized: Boolean, val cancelledFuture: Int, val failedToCancel: Int)

class MarkNoShowUseCase(
    private val bookings: BookingRepository,
    private val clock: Clock,
    private val loyalty: LoyaltyPolicy = LoyaltyPolicy(),
) {
    suspend operator fun invoke(appointment: Appointment): BookingResult<NoShowOutcome> {
        val now = clock.instant()
        var before: ClientRecord? = null
        var after: ClientRecord? = null
        val uid = appointment.clientUid
        val marked = markStatus(
            bookings, appointment, AppointmentStatus.NO_SHOW,
            earliest = appointment.startsAt.plus(NO_SHOW_GRACE), clock,
            // N1: sin cuenta no hay ficha ni penalización
            updateClient = uid?.let {
                { c: ClientRecord? ->
                    before = c
                    (c ?: ClientRecord.new(uid, appointment.clientName)).afterNoShow(now, loyalty).also { after = it } // P1
                }
            },
        )
        if (marked is AppResult.Failure) return marked
        if (uid == null) return AppResult.Success(NoShowOutcome(penalized = false, cancelledFuture = 0, failedToCancel = 0))
        val justPenalized = after?.let { it.isPenalized(now) && it.penaltyUntil != before?.penaltyUntil } == true
        if (!justPenalized) return AppResult.Success(NoShowOutcome(penalized = false, cancelledFuture = 0, failedToCancel = 0))

        // F7-3: se cancelan sus citas futuras en esta barbería (con cualquier barbero). Se liberan los horarios.
        val future = when (val r = bookings.activeAppointments(appointment.tenantId, uid)) {
            is AppResult.Success -> r.value.filter { it.id != appointment.id && it.startsAt.isAfter(now) }
            is AppResult.Failure -> return AppResult.Success(NoShowOutcome(penalized = true, cancelledFuture = 0, failedToCancel = 1))
        }
        val results = future.map { bookings.cancel(it) }
        return AppResult.Success(
            NoShowOutcome(
                penalized = true,
                cancelledFuture = results.count { it is AppResult.Success },
                failedToCancel = results.count { it is AppResult.Failure },
            ),
        )
    }

    companion object {
        /** A4: tolerancia antes de marcar que el cliente no llegó. */
        val NO_SHOW_GRACE: Duration = Duration.ofMinutes(10)
    }
}

class CancelByStaffUseCase(private val bookings: BookingRepository, private val clock: Clock) {
    /**
     * A5: el personal cancela y se liberan los bloques.
     * W4: solo antes de la hora de inicio; después se marca atendida o no-show (si no, se esconden no-shows).
     */
    suspend operator fun invoke(appointment: Appointment): BookingResult<Unit> {
        if (appointment.status != AppointmentStatus.BOOKED) return AppResult.Failure(BookingFailure.AlreadyClosed)
        if (!clock.instant().isBefore(appointment.startsAt)) return AppResult.Failure(BookingFailure.CancelTooLate)
        return bookings.cancel(appointment)
    }
}

private suspend fun markStatus(
    bookings: BookingRepository,
    appointment: Appointment,
    status: AppointmentStatus,
    earliest: java.time.Instant,
    clock: Clock,
    updateClient: ((ClientRecord?) -> ClientRecord)?,
): BookingResult<Unit> {
    if (appointment.status != AppointmentStatus.BOOKED) return AppResult.Failure(BookingFailure.AlreadyClosed) // A6
    if (clock.instant().isBefore(earliest)) return AppResult.Failure(BookingFailure.TooEarly) // A4
    return bookings.setStatus(appointment, status, updateClient)
}

/** A7: resumen del día. Las canceladas no cuentan. */
data class DaySummary(
    val total: Int,
    val pending: Int,
    val completed: Int,
    val noShows: Int,
    /** Ingresos de las completadas. */
    val earned: Money,
    /** Completadas + pendientes (lo que se espera cobrar si todos llegan). */
    val expected: Money,
)

fun summarize(appointments: List<Appointment>): DaySummary {
    val active = appointments.filter { it.status != AppointmentStatus.CANCELLED }
    val completed = active.filter { it.status == AppointmentStatus.COMPLETED }
    val pending = active.filter { it.status == AppointmentStatus.BOOKED }
    return DaySummary(
        total = active.size,
        pending = pending.size,
        completed = completed.size,
        noShows = active.count { it.status == AppointmentStatus.NO_SHOW },
        earned = Money(completed.sumOf { it.price.cents }),
        expected = Money((completed + pending).sumOf { it.price.cents }),
    )
}

/**
 * F7-2: deshacer "atendida" o "no llegó" marcado por error. Solo el dueño (lo exigen las reglas y la
 * pantalla solo se lo ofrece a él) y dentro de 15 min. La cita vuelve a BOOKED y la ficha se corrige
 * en la misma transacción. Las citas que una penalización canceló (F7-3) no se restauran.
 */
class UndoMarkUseCase(
    private val bookings: BookingRepository,
    private val policy: LoyaltyPolicy,
    private val clock: Clock,
) {
    suspend operator fun invoke(appointment: Appointment): BookingResult<Unit> {
        val status = appointment.status
        if (status != AppointmentStatus.COMPLETED && status != AppointmentStatus.NO_SHOW) {
            return AppResult.Failure(BookingFailure.AlreadyClosed)
        }
        if (!appointment.canUndo(clock.instant())) return AppResult.Failure(BookingFailure.UndoExpired)
        val uid = appointment.clientUid
        if (uid != null && status == AppointmentStatus.COMPLETED) {
            val c = (bookings.client(appointment.tenantId, uid) as? AppResult.Success)?.value
            if (c != null && c.rewardsUsed * policy.visitsPerReward > c.completed - 1) {
                return AppResult.Failure(BookingFailure.RewardAlreadyUsed)
            }
        }
        val update = uid?.let {
            { c: ClientRecord? ->
                val current = c ?: ClientRecord.new(it, appointment.clientName)
                if (status == AppointmentStatus.COMPLETED) current.undoCompleted() else current.undoNoShow(policy)
            }
        }
        return when (val r = bookings.setStatus(appointment, AppointmentStatus.BOOKED, update)) {
            // Reloj del teléfono adelantado: el servidor ya cerró el plazo.
            is AppResult.Failure -> if (r.error == BookingFailure.TooEarly) AppResult.Failure(BookingFailure.UndoExpired) else r
            is AppResult.Success -> r
        }
    }
}
