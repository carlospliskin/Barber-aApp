package com.barberia.feature.catalog.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.barberia.core.common.AppResult
import com.barberia.core.firebase.testing.FirebaseEmulator
import com.barberia.core.firebase.testing.FirebaseEmulator.read
import com.barberia.core.firebase.testing.FirebaseEmulator.seed
import com.barberia.feature.catalog.data.firebase.FirestoreInviteGateway
import com.barberia.feature.catalog.data.repository.FirestoreInviteRepository
import com.barberia.feature.catalog.domain.model.StaffInvite
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Q1 + A1/A16: invitar, aceptar, quitar acceso y volver con una invitación nueva, con el SDK y las reglas reales. */
@RunWith(AndroidJUnit4::class)
class InviteEmulatorTest {
    private val repo = FirestoreInviteRepository(FirestoreInviteGateway(FirebaseEmulator.db))

    @Before fun reset() = FirebaseEmulator.reset()

    private fun invite(code: String, tenant: String) =
        StaffInvite(code, tenant, "b1", "Luis", Instant.now().plus(Duration.ofDays(7)), usedBy = null)

    private fun clientProfile(email: String) = mapOf(
        "displayName" to "Luis", "email" to email, "phone" to null, "staffTenantId" to null, "staffRole" to null,
        "createdAt" to Instant.now(),
    )

    @Test fun invitar_aceptar_revocar_y_volver_con_invitacion_nueva() = runTest(timeout = 90.seconds) {
        val owner = FirebaseEmulator.signUp("dueno@test.com")
        FirebaseEmulator.verifyEmail() // A14: invitar exige correo verificado
        assertEquals(AppResult.Success(true), repo.create(invite("INV234", owner)))

        val luis = FirebaseEmulator.signUp("luis@test.com")
        seed("users/$luis", clientProfile("luis@test.com"))
        assertEquals(AppResult.Success(Unit), repo.redeem(invite("INV234", owner), luis))
        assertEquals("BARBER", read("users/$luis")!!["staffRole"])
        assertEquals(luis, read("staffInvites/INV234")!!["usedBy"])

        // A16: el dueño ve la cuenta ligada y le quita el acceso
        FirebaseEmulator.signIn("dueno@test.com")
        assertEquals(AppResult.Success(setOf(luis)), repo.linkedAccounts(owner, "b1"))
        assertEquals(AppResult.Success(Unit), repo.revoke(owner, luis, "b1"))
        assertTrue(read("tenants/$owner/revoked/$luis") != null)

        // Luis suelta el rol (A7-b) y vuelve con una invitación creada después de la revocación
        seed("users/$luis", clientProfile("luis@test.com"))
        assertEquals(AppResult.Success(true), repo.create(invite("NEW234", owner)))
        FirebaseEmulator.signIn("luis@test.com")
        assertEquals(AppResult.Success(Unit), repo.redeem(invite("NEW234", owner), luis))
        assertNull(read("tenants/$owner/revoked/$luis")) // la revocación se borró en el mismo batch
        assertEquals("BARBER", read("users/$luis")!!["staffRole"])
    }

    @Test fun una_invitacion_vieja_no_devuelve_el_acceso() = runTest(timeout = 90.seconds) {
        val owner = FirebaseEmulator.signUp("dueno@test.com")
        FirebaseEmulator.verifyEmail()
        repo.create(invite("OLD234", owner)) // creada ANTES de la revocación
        val luis = FirebaseEmulator.signUp("luis@test.com")
        seed("users/$luis", clientProfile("luis@test.com"))
        seed("tenants/$owner/revoked/$luis", mapOf("barberId" to "b1", "createdAt" to Instant.now().plusSeconds(5)))

        val r = repo.redeem(invite("OLD234", owner), luis)
        assertTrue(r is AppResult.Failure)
        assertEquals(null, read("users/$luis")!!["staffRole"]) // el batch no se aplicó a medias
        assertEquals(null, read("staffInvites/OLD234")!!["usedBy"])
    }
}
