package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.BookingRequest
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.BookAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.ForgivePenaltyUseCase
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.MarkNoShowUseCase
import com.barberia.feature.booking.domain.usecase.RedeemRewardUseCase
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LoyaltyTest {
    private val loyalty = LoyaltyPolicy(visitsPerReward = 10, noShowsForPenalty = 3, penaltyDays = 14)
    private val now = Instant.parse("2026-11-16T17:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val repo = FakeBookingRepository()
    private val monday = LocalDate.of(2026, 11, 16)

    private fun appt(id: String) = Appointment(
        id, "t1", "cli", "Ana", "b1", "Luis", "s1", "Corte", Money(15_000), monday, 600, 630,
        AppointmentStatus.BOOKED, Instant.parse("2026-11-16T16:00:00Z"),
    )

    // ── Reglas puras ─────────────────────────────────────────────────────────
    @Test fun `progreso y premios disponibles`() {
        val c = ClientRecord("cli", "Ana", completed = 23, noShows = 0, rewardsUsed = 1, penaltyUntil = null)
        assertEquals(1, c.availableRewards(loyalty)) // ganó 2, usó 1
        assertEquals(3, c.visitsTowardNext(loyalty)) // 23 % 10
    }

    @Test fun `penalizacion vigente`() {
        val c = ClientRecord("cli", "Ana", 0, 3, 0, penaltyUntil = now.plusSeconds(60))
        assertTrue(c.isPenalized(now))
        assertFalse(c.isPenalized(now.plusSeconds(61)))
    }

    // ── P3 / P7: atendida suma 1 junto con la cita ───────────────────────────
    @Test fun `P3 atendida crea o suma una visita al cliente`() = runTest {
        MarkCompletedUseCase(repo, clock)(appt("a1"))
        assertEquals(ClientRecord("cli", "Ana", 1, 0, 0, null), repo.clients["cli"])
        MarkCompletedUseCase(repo, clock)(appt("a2"))
        assertEquals(2, repo.clients["cli"]!!.completed)
    }

    // ── P1: penalización al tercer no-show ───────────────────────────────────
    @Test fun `P1 al tercer no-show penaliza 14 dias`() = runTest {
        repo.clients["cli"] = ClientRecord("cli", "Ana", 5, 2, 0, null)
        MarkNoShowUseCase(repo, clock, loyalty)(appt("a1"))
        val c = repo.clients["cli"]!!
        assertEquals(3, c.noShows)
        assertEquals(now.plus(Duration.ofDays(14)), c.penaltyUntil)
    }

    @Test fun `P1 antes del tercero no penaliza`() = runTest {
        MarkNoShowUseCase(repo, clock, loyalty)(appt("a1"))
        assertEquals(ClientRecord("cli", "Ana", 0, 1, 0, null), repo.clients["cli"])
    }

    // ── P2 (B4): penalizado no reserva ───────────────────────────────────────
    @Test fun `P2 penalizado no reserva y se le dice hasta cuando`() = runTest {
        val until = now.plus(Duration.ofDays(5))
        repo.clients["cli"] = ClientRecord("cli", "Ana", 0, 3, 0, until)
        val catalog = FakeCatalogRepository(
            Catalog(
                listOf(Service("s1", "Corte", 30, Money(100))),
                listOf(Barber("b1", "Luis", schedule = WeeklySchedule(mapOf(DayOfWeek.TUESDAY to listOf(TimeRange.of("10:00", "14:00")))))),
                1,
            ),
        )
        val r = BookAppointmentUseCase(catalog, repo, BookingPolicy(), clock) { "n1" }(
            BookingRequest("t1", ZoneId.of("America/Mexico_City"), "cli", "Ana", "b1", "s1", monday.plusDays(1), 600),
        )
        assertEquals(AppResult.Failure(BookingFailure.Penalized(until)), r)
    }

    // ── P4: premios ──────────────────────────────────────────────────────────
    @Test fun `P4 canjear premio solo si hay`() = runTest {
        repo.clients["cli"] = ClientRecord("cli", "Ana", 10, 0, 0, null)
        assertEquals(AppResult.Success(Unit), RedeemRewardUseCase(repo, loyalty)("t1", "cli"))
        assertEquals(1, repo.clients["cli"]!!.rewardsUsed)
        assertEquals(AppResult.Failure(BookingFailure.NoRewardAvailable), RedeemRewardUseCase(repo, loyalty)("t1", "cli"))
    }

    // ── P5: perdonar ─────────────────────────────────────────────────────────
    @Test fun `P5 perdonar quita penalizacion y reinicia no-shows`() = runTest {
        repo.clients["cli"] = ClientRecord("cli", "Ana", 7, 3, 1, now.plusSeconds(999))
        ForgivePenaltyUseCase(repo)("t1", "cli")
        assertEquals(ClientRecord("cli", "Ana", 7, 0, 1, null), repo.clients["cli"])
    }
}
