package com.barberia.feature.booking.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
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
        markStatus(bookings, appointment, AppointmentStatus.COMPLETED, earliest = appointment.startsAt, clock)
}

class MarkNoShowUseCase(private val bookings: BookingRepository, private val clock: Clock) {
    suspend operator fun invoke(appointment: Appointment): BookingResult<Unit> = markStatus(
        bookings, appointment, AppointmentStatus.NO_SHOW,
        earliest = appointment.startsAt.plus(NO_SHOW_GRACE), clock,
    )

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
): BookingResult<Unit> {
    if (appointment.status != AppointmentStatus.BOOKED) return AppResult.Failure(BookingFailure.AlreadyClosed) // A6
    if (clock.instant().isBefore(earliest)) return AppResult.Failure(BookingFailure.TooEarly) // A4
    return bookings.setStatus(appointment, status)
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
