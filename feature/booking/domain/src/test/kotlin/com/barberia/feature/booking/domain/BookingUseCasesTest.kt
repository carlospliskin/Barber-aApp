package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.BookingRequest
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.BookAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.CancelAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.ShareCodes
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BookingUseCasesTest {
    private val zone = ZoneId.of("America/Mexico_City")
    // Lunes 16-nov-2026 08:00 en CDMX = 14:00 UTC
    private val clock = Clock.fixed(Instant.parse("2026-11-16T14:00:00Z"), ZoneId.of("UTC"))
    private val monday = LocalDate.of(2026, 11, 16)
    private val policy = BookingPolicy(maxActivePerClient = 2, cancelHoursBefore = 2)

    private val luis = Barber("b1", "Luis", schedule = WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "14:00")))))
    private val corte = Service("s1", "Corte", 30, Money(15_000))
    private val catalogRepo = FakeCatalogRepository(Catalog(listOf(corte), listOf(luis), 1))
    private val bookings = FakeBookingRepository()

    private val book = BookAppointmentUseCase(catalogRepo, bookings, policy, clock) { "cita-${bookings.created.size + 1}" }
    private fun request(start: String = "10:00", date: LocalDate = monday) =
        BookingRequest("t1", zone, "cliente-1", "Ana", "b1", "s1", date, TimeRange.parse(start))

    @Test fun `B6 la hora de la barberia manda, no la del telefono`() = runTest {
        // En UTC ya son las 14:00, pero en CDMX son las 08:00: 10:00 sigue disponible
        val slots = (GetAvailableSlotsUseCase(catalogRepo, bookings, policy, clock)("t1", zone, "b1", "s1", monday) as AppResult.Success).value
        assertEquals("10:00", TimeRange.label(slots.first()))
    }

    @Test fun `reservar guarda la cita con los datos del catalogo y sus bloques`() = runTest {
        val r = book(request()) as AppResult.Success
        assertEquals(
            Appointment(
                id = "cita-1", tenantId = "t1", clientUid = "cliente-1", clientName = "Ana", barberId = "b1",
                barberName = "Luis", serviceId = "s1", serviceName = "Corte", price = Money(15_000),
                date = monday, startMinute = 600, endMinute = 630, status = AppointmentStatus.BOOKED,
                // B10: 10:00 en CDMX = 16:00 UTC; las reglas lo usan para el límite de cancelación
                startsAt = Instant.parse("2026-11-16T16:00:00Z"),
            ),
            r.value,
        )
        assertEquals((600 until 630 step 5).toSet(), bookings.day("b1", monday).keys)
    }

    @Test fun `B3 si otro cliente tomo el horario antes, falla con SlotTaken`() = runTest {
        book(request("10:00"))
        assertEquals(AppResult.Failure(BookingFailure.SlotTaken), book(request("10:15").copy(clientUid = "otro")))
    }

    @Test fun `B1 hora que no esta en los horarios ofrecidos`() = runTest {
        assertEquals(AppResult.Failure(BookingFailure.SlotUnavailable), book(request("10:07")))
        assertEquals(AppResult.Failure(BookingFailure.SlotUnavailable), book(request("13:45"))) // termina 14:15
    }

    @Test fun `B9 servicio desactivado entre abrir y confirmar`() = runTest {
        catalogRepo.stored.value = Catalog(listOf(corte.copy(active = false)), listOf(luis), 2)
        assertEquals(AppResult.Failure(BookingFailure.ServiceUnavailable), book(request()))
    }

    @Test fun `B5 maximo dos citas activas por cliente`() = runTest {
        book(request("10:00"))
        book(request("11:00"))
        assertEquals(AppResult.Failure(BookingFailure.TooManyActive), book(request("12:00")))
    }

    @Test fun `B7 cancelar libera el horario`() = runTest {
        val cita = (book(request("10:00")) as AppResult.Success).value
        val r = CancelAppointmentUseCase(bookings, policy, clock)(cita, zone, byUid = "cliente-1")
        assertEquals(AppResult.Success(Unit), r)
        assertTrue(bookings.day("b1", monday).isEmpty())
        assertEquals(AppointmentStatus.CANCELLED, bookings.created.single().status)
    }

    @Test fun `B7 no se cancela con menos de 2 horas`() = runTest {
        val cita = (book(request("10:00")) as AppResult.Success).value // son las 08:00 → faltan 2 h justas
        val late = Clock.offset(clock, java.time.Duration.ofMinutes(1))
        assertEquals(
            AppResult.Failure(BookingFailure.CancelTooLate),
            CancelAppointmentUseCase(bookings, policy, late)(cita, zone, byUid = "cliente-1"),
        )
    }

    @Test fun `solo quien reservo la cancela`() = runTest {
        val cita = (book(request("10:00")) as AppResult.Success).value
        assertEquals(
            AppResult.Failure(BookingFailure.NotYours),
            CancelAppointmentUseCase(bookings, policy, clock)(cita, zone, byUid = "intruso"),
        )
    }

    @Test fun `B0 codigo para compartir de 6 caracteres sin letras confusas`() {
        val code = ShareCodes.generate(java.util.Random(7))
        assertEquals(6, code.length)
        assertTrue(code.none { it in "01OIL" })
        assertEquals("ABC234", ShareCodes.normalize(" abc-234 "))
        assertEquals(null, ShareCodes.normalize("abc"))
    }
}
