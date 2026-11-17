package com.barberia.app

import com.barberia.app.di.BookingTimeOffAppointments
import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.CancelByStaffUseCase
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** N2-b: el catálogo pregunta a booking qué citas choca un día libre y las cancela. */
class TimeOffAppointmentsTest {
    private val cdmx = ZoneId.of("America/Mexico_City")
    private val day = LocalDate.of(2026, 12, 24)
    private val clock = Clock.fixed(Instant.parse("2026-12-20T17:00:00Z"), ZoneOffset.UTC)
    private val repo = FakeBookingRepository()
    private val bridge = BookingTimeOffAppointments(repo, CancelByStaffUseCase(repo, clock))

    private suspend fun book(id: String, barber: String, start: Int, date: LocalDate = day) = repo.book(
        Appointment(
            id, "t1", "cli-$id", "Cliente", barber, barber, "s1", "Corte", Money(100), date, start, start + 30,
            AppointmentStatus.BOOKED, date.atStartOfDay(cdmx).plusMinutes(start.toLong()).toInstant()
        )
    )

    @Test fun `cuenta solo las citas activas de ese barbero y ese dia`() = runTest {
        book("a1", "b1", 600)
        book("a2", "b1", 900)
        book("a3", "b2", 600) // otro barbero
        book("a4", "b1", 600, day.plusDays(1)) // otro día
        assertEquals(AppResult.Success(2), bridge.affected("t1", "b1", TimeOff(day)))
    }

    @Test fun `salir temprano solo cuenta las que caen en el rango`() = runTest {
        book("a1", "b1", 600) // 10:00
        book("a2", "b1", 900) // 15:00
        assertEquals(AppResult.Success(1), bridge.affected("t1", "b1", TimeOff(day, TimeRange(14 * 60, 20 * 60))))
    }

    @Test fun `cerrar la barberia cuenta a todos los barberos`() = runTest {
        book("a1", "b1", 600)
        book("a3", "b2", 600)
        assertEquals(AppResult.Success(2), bridge.affected("t1", null, TimeOff(day)))
    }

    @Test fun `cancela las que choca y deja las demas`() = runTest {
        book("a1", "b1", 600)
        book("a2", "b1", 900)
        book("a3", "b2", 600)
        assertEquals(AppResult.Success(2), bridge.cancel("t1", "b1", TimeOff(day)))
        assertEquals(
            mapOf("a1" to AppointmentStatus.CANCELLED, "a2" to AppointmentStatus.CANCELLED, "a3" to AppointmentStatus.BOOKED),
            repo.created.associate { it.id to it.status }
        )
        assertEquals(AppResult.Success(0), bridge.affected("t1", "b1", TimeOff(day)))
    }
}
