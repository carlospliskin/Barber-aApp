package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.testing.FakeJoinedStore
import com.barberia.feature.booking.domain.usecase.EnsureShareCodeUseCase
import com.barberia.feature.booking.domain.usecase.JoinBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyAppointmentsUseCase
import com.barberia.feature.booking.domain.usecase.ShareCodes
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BarbershopUseCasesTest {
    private val repo = FakeBookingRepository()
    private val store = FakeJoinedStore()
    private val gold = Barbershop("t1", "Barber Gold", ZoneId.of("America/Mexico_City"), shareCode = null)

    @Test fun `R8 unirse con codigo guarda la barberia en el telefono`() = runTest {
        repo.barbershops["t1"] = gold
        repo.shareCodes["ABC234"] = "t1"
        assertEquals(AppResult.Success(gold), JoinBarbershopUseCase(repo, store)(" abc-234 ", uid = "ana"))
        assertEquals(gold, (ObserveJoinedBarbershopUseCase(repo, store)("ana").first() as AppResult.Success).value)
    }

    @Test fun `U3 otro usuario del mismo telefono no hereda la barberia`() = runTest {
        repo.barbershops["t1"] = gold
        repo.shareCodes["ABC234"] = "t1"
        JoinBarbershopUseCase(repo, store)("ABC234", uid = "ana")
        assertEquals(AppResult.Success(null), ObserveJoinedBarbershopUseCase(repo, store)("beto").first())
    }

    @Test fun `codigo que no existe o barberia borrada`() = runTest {
        assertEquals(AppResult.Failure(BookingFailure.InvalidCode), JoinBarbershopUseCase(repo, store)("ZZZ999", "ana"))
        repo.shareCodes["ABC234"] = "fantasma"
        assertEquals(AppResult.Failure(BookingFailure.InvalidCode), JoinBarbershopUseCase(repo, store)("ABC234", "ana"))
        assertEquals(null, store.saved["ana"])
    }

    @Test fun `V1 no se sale de la barberia con citas activas`() = runTest {
        repo.barbershops["t1"] = gold
        repo.shareCodes["ABC234"] = "t1"
        JoinBarbershopUseCase(repo, store)("ABC234", "ana")
        repo.activeOverride = 1
        assertEquals(
            AppResult.Failure(BookingFailure.HasActiveAppointments),
            com.barberia.feature.booking.domain.usecase.LeaveBarbershopUseCase(
                repo,
                store
            )("ana", "t1", java.time.LocalDate.of(2026, 11, 16))
        )
        assertEquals("t1", store.saved["ana"])
        repo.activeOverride = 0
        assertEquals(
            AppResult.Success(Unit),
            com.barberia.feature.booking.domain.usecase.LeaveBarbershopUseCase(
                repo,
                store
            )("ana", "t1", java.time.LocalDate.of(2026, 11, 16))
        )
        assertEquals(null, store.saved["ana"])
    }

    @Test fun `R7 el duenio obtiene su codigo y se crea solo la primera vez`() = runTest {
        repo.barbershops["t1"] = gold
        val ensure = EnsureShareCodeUseCase(repo, Random(1))
        val code = (ensure("t1") as AppResult.Success).value
        assertNotNull(ShareCodes.normalize(code))
        assertEquals(code, (ensure("t1") as AppResult.Success).value) // no crea otro
        assertEquals(1, repo.claims)
    }

    @Test fun `R7 si el codigo ya lo tiene otra barberia prueba otro`() = runTest {
        repo.barbershops["t1"] = gold
        val random = Random(1)
        repo.shareCodes[ShareCodes.generate(Random(1))] = "otra" // el primero que saldría ya está tomado
        val code = (EnsureShareCodeUseCase(repo, random)("t1") as AppResult.Success).value
        assertEquals("t1", repo.shareCodes[code])
        assertEquals(2, repo.claims)
    }

    @Test fun `R4 mis citas solo trae las de hoy en adelante`() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-11-16T14:00:00Z"), ZoneId.of("UTC"))
        ObserveMyAppointmentsUseCase(repo, clock)("t1", "cli", gold.zone).first()
        assertEquals(java.time.LocalDate.of(2026, 11, 16), repo.lastUpcomingFrom)
    }
}
