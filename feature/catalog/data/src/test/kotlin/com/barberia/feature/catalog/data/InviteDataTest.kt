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
}
