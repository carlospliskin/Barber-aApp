package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.CancelByStaffUseCase
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.MarkNoShowUseCase
import com.barberia.feature.booking.domain.usecase.ObserveAgendaUseCase
import com.barberia.feature.booking.domain.usecase.summarize
import com.barberia.feature.catalog.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AgendaUseCasesTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val start = Instant.parse("2026-11-16T16:00:00Z") // 10:00 CDMX
    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), ZoneId.of("UTC"))
    private val repo = FakeBookingRepository()

    private fun appt(id: String, barber: String = "b1", status: AppointmentStatus = AppointmentStatus.BOOKED, cents: Long = 15_000) =
        Appointment(id, "t1", "cli", "Ana", barber, "Luis", "s1", "Corte", Money(cents), monday, 600, 630, status, start)

    @Test fun `A2 A3 agenda del dia, de un barbero o de todos`() = runTest {
        repo.book(appt("a1", "b1"))
        repo.book(appt("a2", "b2").copy(startMinute = 700, endMinute = 730))
        assertEquals(listOf("a1"), ObserveAgendaUseCase(repo)("t1", monday, barberId = "b1").first().map { it.id })
        assertEquals(listOf("a1", "a2"), ObserveAgendaUseCase(repo)("t1", monday, barberId = null).first().map { it.id })
        assertEquals(monday, repo.lastAgendaDate)
    }

    @Test fun `A4 completar solo despues de la hora de inicio`() = runTest {
        val a = appt("a1")
        assertEquals(AppResult.Failure(BookingFailure.TooEarly), MarkCompletedUseCase(repo, clockAt("2026-11-16T15:59:00Z"))(a))
        assertEquals(AppResult.Success(Unit), MarkCompletedUseCase(repo, clockAt("2026-11-16T16:00:00Z"))(a))
        assertEquals(AppointmentStatus.COMPLETED, repo.lastStatus)
    }

    @Test fun `A4 no-show con 10 minutos de tolerancia`() = runTest {
        val a = appt("a1")
        assertEquals(AppResult.Failure(BookingFailure.TooEarly), MarkNoShowUseCase(repo, clockAt("2026-11-16T16:09:00Z"))(a))
        assertEquals(AppResult.Success(Unit), MarkNoShowUseCase(repo, clockAt("2026-11-16T16:10:00Z"))(a))
    }

    @Test fun `A6 un estado final no cambia`() = runTest {
        val done = appt("a1", status = AppointmentStatus.COMPLETED)
        val late = clockAt("2026-11-16T18:00:00Z")
        assertEquals(AppResult.Failure(BookingFailure.AlreadyClosed), MarkNoShowUseCase(repo, late)(done))
        assertEquals(AppResult.Failure(BookingFailure.AlreadyClosed), CancelByStaffUseCase(repo, late)(done))
    }

    @Test fun `W4 despues de la hora el personal no cancela (se usa atendida o no llego)`() = runTest {
        val a = appt("a1")
        assertEquals(
            AppResult.Failure(BookingFailure.CancelTooLate),
            CancelByStaffUseCase(repo, clockAt("2026-11-16T16:00:00Z"))(a),
        )
    }

    @Test fun `A5 el personal cancela antes de la hora y libera los bloques`() = runTest {
        val a = appt("a1")
        repo.book(a)
        assertEquals(AppResult.Success(Unit), CancelByStaffUseCase(repo, clockAt("2026-11-16T15:59:00Z"))(a))
        assertEquals(emptyMap<Int, String>(), repo.day("b1", monday))
    }

    @Test fun `A7 resumen del dia`() {
        val s = summarize(
            listOf(
                appt("a", cents = 15_000),
                appt("b", status = AppointmentStatus.COMPLETED, cents = 20_000),
                appt("c", status = AppointmentStatus.NO_SHOW),
                appt("d", status = AppointmentStatus.CANCELLED),
            ),
        )
        assertEquals(3, s.total) // las canceladas no cuentan
        assertEquals(1, s.pending)
        assertEquals(1, s.completed)
        assertEquals(1, s.noShows)
        assertEquals(Money(20_000), s.earned)
        assertEquals(Money(35_000), s.expected) // completadas + pendientes
    }
}
