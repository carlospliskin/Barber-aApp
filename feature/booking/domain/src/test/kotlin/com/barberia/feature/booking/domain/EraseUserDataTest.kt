package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.testing.FakeAccountDataRepository
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.testing.FakeJoinedStore
import com.barberia.feature.booking.domain.testing.FakeReminderScheduler
import com.barberia.feature.booking.domain.usecase.EraseUserDataUseCase
import com.barberia.feature.catalog.domain.model.Money
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A7: lo que un usuario deja en las barberías se borra o se anonimiza al eliminar su cuenta. */
class EraseUserDataTest {
    private val now = Instant.parse("2026-11-16T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val bookings = FakeBookingRepository()
    private val data = FakeAccountDataRepository(bookings)
    private val reminders = FakeReminderScheduler()
    private val joined = FakeJoinedStore()
    private val erase = EraseUserDataUseCase(bookings, data, reminders, joined, BookingPolicy(), clock)

    private var minute = 0
    private fun appt(
        id: String,
        tenant: String,
        inHours: Long,
        status: AppointmentStatus = AppointmentStatus.BOOKED,
        uid: String = "cli"
    ): Appointment {
        val start = now.plus(Duration.ofHours(inHours))
        minute += 30
        return Appointment(
            id, tenant, uid, "Ana", "b1", "Luis", "s1", "Corte", Money(100),
            start.atZone(ZoneId.of("UTC")).toLocalDate(), minute, minute + 30, status, start
        )
    }

    @Test fun `cancela las que aun puede cancelar, anonimiza todas y borra sus fichas`() = runTest {
        bookings.book(appt("futura", "t1", inHours = 48))
        bookings.book(appt("pronto", "t1", inHours = 1)) // ya no se puede cancelar: queda para el personal
        bookings.book(appt("vieja", "t2", inHours = -48, status = AppointmentStatus.COMPLETED))
        bookings.book(appt("ajena", "t1", inHours = 24, uid = "otro"))
        reminders.schedule("t1", "futura", now)
        reminders.schedule("t1", "pronto", now)
        joined.save("cli", "t1")

        assertEquals(AppResult.Success(Unit), erase("cli", ownedTenantId = null))

        val status = bookings.created.associate { it.id to it.status }
        assertEquals(AppointmentStatus.CANCELLED, status["futura"])
        assertEquals(AppointmentStatus.BOOKED, status["pronto"])
        assertEquals(AppointmentStatus.BOOKED, status["ajena"])
        assertEquals(setOf("futura", "pronto", "vieja"), data.anonymized.toSet())
        assertEquals(setOf("t1" to "cli", "t2" to "cli"), data.deletedClientRecords.toSet())
        assertTrue(reminders.scheduled.isEmpty())
        assertNull(joined.tenantId("cli").first())
        assertTrue(data.deletedShops.isEmpty())
    }

    @Test fun `la ficha se borra antes de anonimizar (si se corta, el reintento la vuelve a encontrar)`() = runTest {
        bookings.book(appt("vieja", "t1", inHours = -48, status = AppointmentStatus.COMPLETED))
        erase("cli", null)
        assertEquals(listOf("deleteClientRecords", "anonymize"), data.calls)
    }

    @Test fun `el duenio borra primero su barberia`() = runTest {
        bookings.book(appt("vieja", "t2", inHours = -48, status = AppointmentStatus.COMPLETED))
        assertEquals(AppResult.Success(Unit), erase("cli", ownedTenantId = "cli"))
        assertEquals("deleteBarbershop", data.calls.first())
        assertEquals(listOf("cli"), data.deletedShops)
    }

    @Test fun `si falla se detiene y lo dice`() = runTest {
        bookings.book(appt("vieja", "t1", inHours = -48, status = AppointmentStatus.COMPLETED))
        data.failure = BookingFailure.NoConnection
        assertEquals(AppResult.Failure(BookingFailure.NoConnection), erase("cli", null))
        assertTrue(data.anonymized.isEmpty())
    }

    @Test fun `recorre todas las paginas`() = runTest {
        repeat(EraseUserDataUseCase.PAGE_SIZE + 5) {
            bookings.book(appt("v$it", "t1", inHours = -48L - it, status = AppointmentStatus.COMPLETED))
        }
        assertEquals(AppResult.Success(Unit), erase("cli", null))
        assertEquals(EraseUserDataUseCase.PAGE_SIZE + 5, data.anonymized.size)
        assertEquals(3, data.pagesRequested) // 2 con citas + 1 vacía
    }

    @Test fun `una cita anonimizada se distingue de una sin cuenta (que siempre tiene nombre)`() {
        val anon = appt("x", "t1", inHours = 1).copy(clientUid = null, clientName = "")
        assertTrue(anon.isFromDeletedAccount)
        assertTrue(!anon.copy(clientName = "Pedro").isFromDeletedAccount)
        assertTrue(!appt("y", "t1", inHours = 1).isFromDeletedAccount)
    }
}
