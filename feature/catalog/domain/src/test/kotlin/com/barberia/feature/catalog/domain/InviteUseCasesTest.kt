package com.barberia.feature.catalog.domain

import com.barberia.core.common.AppResult
import com.barberia.core.common.ShortCodes
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.StaffInvite
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.testing.FakeInviteRepository
import com.barberia.feature.catalog.domain.usecase.CreateBarberInviteUseCase
import com.barberia.feature.catalog.domain.usecase.RedeemBarberInviteUseCase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InviteUseCasesTest {
    private val now = Instant.parse("2026-11-16T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val catalog = FakeCatalogRepository(Catalog(emptyList(), listOf(Barber("b1", "Luis"), Barber("b2", "Pepe", active = false)), 1))
    private val invites = FakeInviteRepository()

    @Test fun `A1 el duenio invita a un barbero activo de su catalogo, vence en 7 dias`() = runTest {
        val r = CreateBarberInviteUseCase(invites, catalog, clock, Random(5))("t1", "b1") as AppResult.Success
        assertNotNull(ShortCodes.normalize(r.value.code))
        assertEquals(StaffInvite(r.value.code, "t1", "b1", "Luis", now.plus(Duration.ofDays(7)), usedBy = null), r.value)
    }

    @Test fun `no se invita a un barbero inactivo o inexistente`() = runTest {
        val create = CreateBarberInviteUseCase(invites, catalog, clock, Random(5))
        assertEquals(AppResult.Failure(CatalogFailure.NotFound), create("t1", "b2"))
        assertEquals(AppResult.Failure(CatalogFailure.NotFound), create("t1", "zz"))
    }

    @Test fun `si el codigo existe prueba otro`() = runTest {
        invites.taken += ShortCodes.generate(Random(5))
        val r = CreateBarberInviteUseCase(invites, catalog, clock, Random(5))("t1", "b1") as AppResult.Success
        assertEquals(2, invites.attempts)
        assertNotNull(r.value.code)
    }

    private val valid = StaffInvite("MVK234", "t1", "b1", "Luis", now.plusSeconds(60), usedBy = null)

    @Test fun `A1 aceptar invitacion valida`() = runTest {
        invites.stored["MVK234"] = valid
        assertEquals(
            AppResult.Success(valid),
            RedeemBarberInviteUseCase(invites, catalog, clock)(" mvk-234 ", uid = "tono", alreadyStaff = false)
        )
        assertEquals("tono" to "MVK234", invites.redeemed)
    }

    @Test fun `W2 si el duenio desactivo al barbero la invitacion ya no sirve`() = runTest {
        invites.stored["MVK234"] = valid.copy(barberId = "b2") // b2 está inactivo
        assertEquals(
            AppResult.Failure(CatalogFailure.InviteInvalid),
            RedeemBarberInviteUseCase(invites, catalog, clock)("MVK234", "tono", alreadyStaff = false)
        )
        assertEquals(null, invites.redeemed)
    }

    @Test fun `A8 invitacion usada, vencida, inexistente o persona ya de personal`() = runTest {
        val redeem = RedeemBarberInviteUseCase(invites, catalog, clock)
        assertEquals(AppResult.Failure(CatalogFailure.InviteInvalid), redeem("NADA22", "tono", false))
        invites.stored["MVK234"] = valid.copy(usedBy = "otro")
        assertEquals(AppResult.Failure(CatalogFailure.InviteInvalid), redeem("MVK234", "tono", false))
        invites.stored["MVK234"] = valid.copy(expiresAt = now.minusSeconds(1))
        assertEquals(AppResult.Failure(CatalogFailure.InviteExpired), redeem("MVK234", "tono", false))
        invites.stored["MVK234"] = valid
        assertEquals(AppResult.Failure(CatalogFailure.AlreadyStaff), redeem("MVK234", "tono", true))
        assertEquals(null, invites.redeemed)
    }
}
