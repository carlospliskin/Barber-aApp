package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.WalkInRequest
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.BookWalkInUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.MarkNoShowUseCase
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N1: el personal agenda a un cliente que llama o llega al local (sin cuenta en la app). */
class WalkInTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val monday = LocalDate.of(2026, 11, 16)
    private fun clockAt(local: String) = Clock.fixed(
        monday.atTime(java.time.LocalTime.parse(local)).atZone(zone).toInstant(),
        ZoneId.of("UTC")
    )
    private val policy = BookingPolicy()

    private val luis = Barber("b1", "Luis", schedule = WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "14:00")))))
    private val corte = Service("s1", "Corte", 30, Money(15_000))
    private val catalogRepo = FakeCatalogRepository(Catalog(listOf(corte), listOf(luis), 1))
    private val bookings = FakeBookingRepository()

    private fun book(now: String = "10:07") =
        BookWalkInUseCase(catalogRepo, bookings, policy, clockAt(now)) { "cita-${bookings.created.size + 1}" }

    private fun request(start: String = "10:00", name: String = "Pedro", phone: String? = "55 1234 5678") =
        WalkInRequest("t1", zone, name, phone, "b1", "s1", monday, TimeRange.parse(start))

    @Test fun `N1 la cita queda sin cuenta, con nombre y telefono`() = runTest {
        val a = (book()(request()) as AppResult.Success).value
        assertNull(a.clientUid)
        assertTrue(a.isWalkIn)
        assertEquals("Pedro", a.clientName)
        assertEquals("5512345678", a.clientPhone) // solo dígitos
        assertEquals(Instant.parse("2026-11-16T16:00:00Z"), a.startsAt)
        assertEquals((600 until 630 step 5).toSet(), bookings.day("b1", monday).keys)
    }

    @Test fun `N1 el que llega al local se atiende en el turno actual (sin anticipacion minima)`() = runTest {
        assertTrue(book("10:07")(request("10:00")) is AppResult.Success) // empezó hace 7 min
        assertEquals(AppResult.Failure(BookingFailure.SlotUnavailable), book("10:20")(request("10:00").copy(startMinute = 600)))
    }

    @Test fun `N1 los horarios del personal incluyen el turno actual`() = runTest {
        val slots = (
            GetAvailableSlotsUseCase(
                catalogRepo,
                bookings,
                policy,
                clockAt("10:07")
            )("t1", zone, "b1", "s1", monday, forStaff = true) as AppResult.Success
            ).value
        assertEquals("10:00", TimeRange.label(slots.first()))
        val client = (
            GetAvailableSlotsUseCase(
                catalogRepo,
                bookings,
                policy,
                clockAt("10:07")
            )("t1", zone, "b1", "s1", monday) as AppResult.Success
            ).value
        assertEquals("10:45", TimeRange.label(client.first())) // al cliente: 30 min de anticipación
    }

    @Test fun `N1 el nombre es obligatorio y el telefono opcional pero valido`() = runTest {
        assertEquals(AppResult.Failure(BookingFailure.InvalidClient), book()(request(name = "  ")))
        assertEquals(AppResult.Failure(BookingFailure.InvalidClient), book()(request(phone = "llámame")))
        assertEquals(AppResult.Failure(BookingFailure.InvalidClient), book()(request(phone = "1".repeat(21))))
        assertNull((book()(request(phone = " ")) as AppResult.Success).value.clientPhone)
    }

    @Test fun `N1 no se encima con otra cita ni con un dia libre`() = runTest {
        book()(request("10:00"))
        assertEquals(AppResult.Failure(BookingFailure.SlotTaken), book()(request("10:15", name = "Otro")))
        catalogRepo.stored.value = catalogRepo.stored.value.copy(barbers = listOf(luis.copy(timeOff = listOf(TimeOff(monday)))))
        assertEquals(AppResult.Failure(BookingFailure.SlotUnavailable), book()(request("12:00")))
    }

    @Test fun `N1 una cita sin cuenta no crea ficha y atenderla no cuenta visitas`() = runTest {
        val a = (book()(request()) as AppResult.Success).value
        assertTrue(bookings.clients.isEmpty())
        assertEquals(AppResult.Success(Unit), MarkCompletedUseCase(bookings, clockAt("10:40"))(a))
        assertEquals(AppointmentStatus.COMPLETED, bookings.created.single().status)
        assertTrue(bookings.clients.isEmpty())
    }

    @Test fun `N1 un no-show sin cuenta no penaliza a nadie`() = runTest {
        val a = (book()(request()) as AppResult.Success).value
        val outcome = (MarkNoShowUseCase(bookings, clockAt("10:30"))(a) as AppResult.Success).value
        assertEquals(false, outcome.penalized)
        assertEquals(AppointmentStatus.NO_SHOW, bookings.created.single().status)
        assertTrue(bookings.clients.isEmpty())
    }
}
