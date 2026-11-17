package com.barberia.app

import com.barberia.app.di.barbershopCalendar
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** N2-c: el catálogo calcula "hoy" con la zona de la barbería (dato de booking). */
class ShopCalendarTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-17T01:00:00Z"), ZoneOffset.UTC) // 16 nov, 19 h en CDMX
    private val repo = FakeBookingRepository()

    @Test fun `usa la zona guardada de la barberia`() = runTest {
        repo.barbershops["t1"] = Barbershop("t1", "Gold", ZoneId.of("Europe/Madrid"), null)
        assertEquals(LocalDate.of(2026, 11, 17), barbershopCalendar(repo, clock).today("t1"))
    }

    @Test fun `si no se puede leer la barberia usa la zona de alta (Ciudad de Mexico), no UTC`() = runTest {
        repo.barbershopFailure = BookingFailure.NoConnection
        assertEquals(LocalDate.of(2026, 11, 16), barbershopCalendar(repo, clock).today("t1"))
    }
}
