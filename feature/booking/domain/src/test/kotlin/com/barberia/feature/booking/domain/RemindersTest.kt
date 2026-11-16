package com.barberia.feature.booking.domain

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.reminderTime
import com.barberia.feature.booking.domain.testing.FakeReminderScheduler
import com.barberia.feature.booking.domain.usecase.SyncRemindersUseCase
import com.barberia.feature.catalog.domain.model.Money
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemindersTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))

    private fun appt(id: String, startsIn: Duration, status: AppointmentStatus = AppointmentStatus.BOOKED) = Appointment(
        id, "t1", "cli", "Ana", "b1", "Luis", "s1", "Corte", Money(15_000), LocalDate.of(2026, 11, 16), 600, 630,
        status, now.plus(startsIn)
    )

    @Test fun `R1 se avisa 3 horas antes (todavia se puede cancelar)`() =
        assertEquals(now.plus(Duration.ofHours(21)), reminderTime(appt("a", Duration.ofHours(24)), now))

    @Test fun `R1 si faltan menos de 3 horas no hay recordatorio`() = assertNull(reminderTime(appt("a", Duration.ofHours(2)), now))

    @Test fun `una cita no vigente no tiene recordatorio`() =
        assertNull(reminderTime(appt("a", Duration.ofHours(24), AppointmentStatus.CANCELLED), now))

    @Test fun `R2 R6 al disparar solo se avisa si la cita sigue vigente y todavia no empieza`() {
        val a = appt("a", Duration.ofHours(3))
        assertEquals(true, com.barberia.feature.booking.domain.model.shouldNotify(a, now))
        assertEquals(false, com.barberia.feature.booking.domain.model.shouldNotify(a.copy(status = AppointmentStatus.CANCELLED), now))
        // el aviso llegó tarde
        assertEquals(false, com.barberia.feature.booking.domain.model.shouldNotify(a, now.plus(Duration.ofHours(3))))
    }

    @Test fun `R3 sincronizar programa solo las citas que lo necesitan, sin duplicar`() = runTest {
        val scheduler = FakeReminderScheduler()
        val sync = SyncRemindersUseCase(scheduler, clock)
        val list = listOf(appt("a", Duration.ofHours(24)), appt("b", Duration.ofHours(1)), appt("c", Duration.ofDays(3)))
        sync(list)
        sync(list) // abrir el home otra vez no duplica
        assertEquals(
            mapOf("a" to now.plus(Duration.ofHours(21)), "c" to now.plus(Duration.ofDays(3)).minus(Duration.ofHours(3))),
            scheduler.scheduled
        )
    }

    @Test fun `R5 cancelar borra el recordatorio`() = runTest {
        val scheduler = FakeReminderScheduler()
        SyncRemindersUseCase(scheduler, clock)(listOf(appt("a", Duration.ofHours(24))))
        scheduler.cancel("a")
        assertEquals(emptyMap<String, Instant>(), scheduler.scheduled)
    }
}
