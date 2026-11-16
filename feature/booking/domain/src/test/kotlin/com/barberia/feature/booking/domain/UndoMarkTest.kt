package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.UndoMarkUseCase
import com.barberia.feature.catalog.domain.model.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** F7-2: deshacer "atendida" o "no llegó" (solo el dueño, dentro de 15 min). */
class UndoMarkTest {
    private val marked = Instant.parse("2026-11-16T16:40:00Z")
    private fun clockAt(minutesAfter: Long) = Clock.fixed(marked.plusSeconds(minutesAfter * 60), ZoneId.of("UTC"))
    private val repo = FakeBookingRepository()
    private val policy = LoyaltyPolicy()

    private fun appt(status: AppointmentStatus, uid: String? = "cli") = Appointment(
        "a1", "t1", uid, "Ana", "b1", "Luis", "s1", "Corte", Money(100), LocalDate.of(2026, 11, 16), 600, 630, status,
        Instant.parse("2026-11-16T16:00:00Z"), markedAt = marked,
    )

    private fun undo(minutesAfter: Long) = UndoMarkUseCase(repo, policy, clockAt(minutesAfter))

    @Test fun `deshacer atendida resta la visita y vuelve a reservada`() = runTest {
        repo.clients["cli"] = ClientRecord("cli", "Ana", completed = 5, noShows = 0, rewardsUsed = 0, penaltyUntil = null)
        assertEquals(AppResult.Success(Unit), undo(5)(appt(AppointmentStatus.COMPLETED)))
        assertEquals(AppointmentStatus.BOOKED, repo.lastStatus)
        assertEquals(4, repo.clients.getValue("cli").completed)
    }

    @Test fun `deshacer el no-show que penalizo quita la penalizacion`() = runTest {
        repo.clients["cli"] = ClientRecord("cli", "Ana", 5, noShows = 3, rewardsUsed = 0, penaltyUntil = marked.plusSeconds(86_400 * 14))
        undo(5)(appt(AppointmentStatus.NO_SHOW))
        val c = repo.clients.getValue("cli")
        assertEquals(2, c.noShows)
        assertNull(c.penaltyUntil)
    }

    @Test fun `deshacer un no-show que no penalizo deja la penalizacion como estaba`() = runTest {
        val until = marked.plusSeconds(86_400 * 5)
        repo.clients["cli"] = ClientRecord("cli", "Ana", 5, noShows = 4, rewardsUsed = 0, penaltyUntil = until)
        undo(5)(appt(AppointmentStatus.NO_SHOW))
        assertEquals(until, repo.clients.getValue("cli").penaltyUntil)
    }

    @Test fun `despues de 15 minutos ya no se puede`() = runTest {
        assertEquals(AppResult.Failure(BookingFailure.UndoExpired), undo(15)(appt(AppointmentStatus.COMPLETED)))
        assertNull(repo.lastStatus)
    }

    @Test fun `no si ya canjeo un premio con esa visita`() = runTest {
        repo.clients["cli"] = ClientRecord("cli", "Ana", completed = 10, noShows = 0, rewardsUsed = 1, penaltyUntil = null)
        assertEquals(AppResult.Failure(BookingFailure.RewardAlreadyUsed), undo(5)(appt(AppointmentStatus.COMPLETED)))
        assertNull(repo.lastStatus)
    }

    @Test fun `una reservada o cancelada no se deshace`() = runTest {
        assertEquals(AppResult.Failure(BookingFailure.AlreadyClosed), undo(5)(appt(AppointmentStatus.BOOKED)))
        assertEquals(AppResult.Failure(BookingFailure.AlreadyClosed), undo(5)(appt(AppointmentStatus.CANCELLED)))
    }

    @Test fun `sin cuenta solo cambia la cita`() = runTest {
        assertEquals(AppResult.Success(Unit), undo(5)(appt(AppointmentStatus.COMPLETED, uid = null)))
        assertTrue(repo.clients.isEmpty())
    }

    @Test fun `canUndo dice si todavia se puede (para mostrar el boton)`() {
        assertTrue(appt(AppointmentStatus.COMPLETED).canUndo(marked.plusSeconds(14 * 60)))
        assertFalse(appt(AppointmentStatus.COMPLETED).canUndo(marked.plusSeconds(15 * 60)))
        assertFalse(appt(AppointmentStatus.BOOKED).canUndo(marked))
        assertFalse(appt(AppointmentStatus.COMPLETED).copy(markedAt = null).canUndo(marked))
    }
}
