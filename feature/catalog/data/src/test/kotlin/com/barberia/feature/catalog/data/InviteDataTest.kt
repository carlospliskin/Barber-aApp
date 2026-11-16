package com.barberia.feature.catalog.data

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.catalog.data.gateway.InviteGateway
import com.barberia.feature.catalog.data.repository.FirestoreInviteRepository
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.StaffInvite
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class InviteDataTest {
    private val invite = StaffInvite("MVK234", "t1", "b1", "Luis", Instant.parse("2026-11-23T16:00:00Z"), usedBy = null)

    private class FakeGateway : InviteGateway {
        val docs = mutableMapOf<String, Map<String, Any?>>()
        var redeemed: Map<String, Any?>? = null
        var error: GatewayError? = null
        override suspend fun get(code: String) = docs[code]
        override suspend fun createIfAbsent(code: String, data: Map<String, Any?>): Boolean {
            error?.let { throw it }
            if (code in docs) return false
            docs[code] = data
            return true
        }
        override suspend fun redeem(code: String, uid: String, profileChanges: Map<String, Any?>) {
            error?.let { throw it }
            redeemed = profileChanges + ("uid" to uid) + ("code" to code)
        }
        val revokedDocs = mutableMapOf<String, String>()
        override suspend fun invitesOfTenant(tenantId: String) = docs.values.filter { it["tenantId"] == tenantId }
        override suspend fun revoke(tenantId: String, uid: String, barberId: String) {
            error?.let { throw it }
            revokedDocs["tenants/$tenantId/revoked/$uid"] = barberId
        }
    }

    private val gw = FakeGateway()
    private val repo = FirestoreInviteRepository(gw)

    @Test fun `crear y leer ida y vuelta`() = runTest {
        assertEquals(AppResult.Success(true), repo.create(invite))
        assertEquals(AppResult.Success(invite), repo.find("MVK234"))
        assertEquals(AppResult.Success(false), repo.create(invite)) // código ocupado
    }

    @Test fun `aceptar escribe exactamente lo que piden las reglas en el perfil`() = runTest {
        repo.redeem(invite, "tono")
        assertEquals(
            mapOf(
                "staffTenantId" to "t1", "staffRole" to "BARBER", "staffBarberId" to "b1",
                "staffInviteCode" to "MVK234", "uid" to "tono", "code" to "MVK234",
            ),
            gw.redeemed,
        )
    }

    @Test fun `si las reglas rechazan al aceptar es invitacion invalida`() = runTest {
        gw.error = GatewayError.PermissionDenied()
        assertEquals(AppResult.Failure(CatalogFailure.InviteInvalid), repo.redeem(invite, "tono"))
    }

    // ── A16 quitar acceso ─────────────────────────────────────────────────────
    @Test fun `A16 cuentas ligadas son las que usaron invitaciones de ese barbero`() = runTest {
        gw.docs["A"] = mapOf("tenantId" to "t1", "barberId" to "b1", "usedBy" to "luis")
        gw.docs["B"] = mapOf("tenantId" to "t1", "barberId" to "b1", "usedBy" to null)
        gw.docs["C"] = mapOf("tenantId" to "t1", "barberId" to "b2", "usedBy" to "pepe")
        assertEquals(com.barberia.core.common.AppResult.Success(setOf("luis")), repo.linkedAccounts("t1", "b1"))
    }

    @Test fun `A16 revocar escribe en la barberia`() = runTest {
        repo.revoke("t1", "luis", "b1")
        assertEquals(mapOf("tenants/t1/revoked/luis" to "b1"), gw.revokedDocs)
    }
}
