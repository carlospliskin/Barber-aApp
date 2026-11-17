package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.ShopZones
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.ChangeShopZoneUseCase
import com.barberia.feature.catalog.domain.model.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** N5: el dueño elige la zona horaria de su barbería. */
class ShopZoneTest {
    private val cdmx = ZoneId.of("America/Mexico_City")
    private val tijuana = ZoneId.of("America/Tijuana")
    private val clock = Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneId.of("UTC"))
    private val repo = FakeBookingRepository().apply { barbershops["t1"] = Barbershop("t1", "Gold", cdmx, null) }
    private val change = ChangeShopZoneUseCase(repo, clock)

    private fun appt(date: LocalDate, status: AppointmentStatus = AppointmentStatus.BOOKED) = Appointment(
        "a-$date", "t1", "cli", "Ana", "b1", "Luis", "s1", "Corte", Money(100), date, 600, 630, status,
        date.atTime(10, 0).atZone(cdmx).toInstant()
    )

    @Test fun `cambia la zona si no hay citas por venir`() = runTest {
        repo.book(appt(LocalDate.of(2026, 11, 10))) // ya pasó
        assertEquals(AppResult.Success(Unit), change("t1", tijuana))
        assertEquals(tijuana, repo.barbershops["t1"]!!.zone)
    }

    @Test fun `con citas por venir no se cambia (sus horas quedarían mal)`() = runTest {
        repo.book(appt(LocalDate.of(2026, 11, 20)))
        assertEquals(AppResult.Failure(BookingFailure.HasActiveAppointments), change("t1", tijuana))
        assertEquals(cdmx, repo.barbershops["t1"]!!.zone)
    }

    @Test fun `una cita de hoy cuenta como por venir`() = runTest {
        repo.book(appt(LocalDate.of(2026, 11, 16)))
        assertEquals(AppResult.Failure(BookingFailure.HasActiveAppointments), change("t1", tijuana))
    }

    @Test fun `solo zonas de la lista`() = runTest {
        assertEquals(AppResult.Failure(BookingFailure.InvalidZone), change("t1", ZoneId.of("Asia/Tokyo")))
    }

    @Test fun `la misma zona no escribe nada`() = runTest {
        repo.book(appt(LocalDate.of(2026, 11, 20)))
        assertEquals(AppResult.Success(Unit), change("t1", cdmx))
        assertEquals(0, repo.zoneWrites)
    }

    @Test fun `la lista empieza con las zonas de Mexico y trae la de la Ciudad de Mexico`() {
        assertEquals(cdmx, ShopZones.ALLOWED.first())
        assertTrue(ShopZones.ALLOWED.all { it.id.startsWith("America/") || it.id.startsWith("Europe/") })
    }

    @Test fun `la lista de las reglas de Firestore es la misma`() {
        val rules = java.io.File("../../../firebase/firestore.rules").readText()
        val fromRules = Regex("function validZone\\(z\\) \\{\\s*return z in \\[([^\\]]+)]").find(rules)!!.groupValues[1]
            .split(',').map { it.trim().trim('\'') }.filter { it.isNotEmpty() }
        assertEquals(ShopZones.ALLOWED.map { it.id }, fromRules)
    }
}
