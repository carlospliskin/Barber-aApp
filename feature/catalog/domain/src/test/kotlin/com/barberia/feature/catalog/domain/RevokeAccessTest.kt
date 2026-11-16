package com.barberia.feature.catalog.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.model.StaffInvite
import com.barberia.feature.catalog.domain.testing.FakeInviteRepository
import com.barberia.feature.catalog.domain.usecase.RevokeBarberAccessUseCase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A16: el dueño quita el acceso de la app a las cuentas ligadas a un barbero del catálogo. */
class RevokeAccessTest {
    private val invites = FakeInviteRepository()
    private val revoke = RevokeBarberAccessUseCase(invites)

    private fun invite(code: String, barber: String, usedBy: String?, tenant: String = "t1") =
        StaffInvite(code, tenant, barber, "Luis", Instant.parse("2026-11-20T00:00:00Z"), usedBy)

    @Test fun `revoca todas las cuentas que usaron una invitacion de ese barbero`() = runTest {
        listOf(
            invite("AAA234", "b1", "luis"),
            invite("BBB234", "b1", "luis-nuevo"), // cambió de teléfono y de cuenta
            invite("CCC234", "b1", null), // sin usar: nadie que revocar
            invite("DDD234", "b2", "pepe"),
            invite("EEE234", "b1", "otro", tenant = "t2")
        ).forEach { invites.stored[it.code] = it }

        assertEquals(AppResult.Success(2), revoke("t1", "b1"))
        assertEquals(setOf(Triple("t1", "luis", "b1"), Triple("t1", "luis-nuevo", "b1")), invites.revoked)
    }

    @Test fun `sin cuentas ligadas no escribe nada`() = runTest {
        assertEquals(AppResult.Success(0), revoke("t1", "b1"))
        assertEquals(emptySet<Triple<String, String, String>>(), invites.revoked)
    }
}
