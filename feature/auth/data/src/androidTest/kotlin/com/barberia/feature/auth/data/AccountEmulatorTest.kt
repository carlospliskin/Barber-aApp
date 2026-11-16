package com.barberia.feature.auth.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.barberia.core.common.AppResult
import com.barberia.core.firebase.CachePurge
import com.barberia.core.firebase.PurgeFlag
import com.barberia.core.firebase.testing.FirebaseEmulator
import com.barberia.core.firebase.testing.FirebaseEmulator.read
import com.barberia.feature.auth.data.firebase.FirebaseAuthGateway
import com.barberia.feature.auth.data.firebase.FirestoreProfileGateway
import com.barberia.feature.auth.data.gateway.ModeStore
import com.barberia.feature.auth.data.repository.FirebaseAccountRepository
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Q1: cuenta real (Auth + perfil + barbería) contra el emulador con las reglas reales. */
@RunWith(AndroidJUnit4::class)
class AccountEmulatorTest {
    private class MemoryModes : ModeStore {
        override val mode = MutableStateFlow<String?>(null)
        override suspend fun save(mode: String?) {
            this.mode.value = mode
        }
    }
    private class MemoryFlag : PurgeFlag {
        var value = false
        override fun get() = value
        override fun set(value: Boolean) {
            this.value = value
        }
    }

    private val flag = MemoryFlag()
    private val purge = CachePurge(flag)

    private fun repo() = FirebaseAccountRepository(
        FirebaseAuthGateway(FirebaseEmulator.auth, purge),
        FirestoreProfileGateway(FirebaseEmulator.db, purge),
        MemoryModes(),
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    @Before fun reset() = FirebaseEmulator.reset()

    @Test fun registrar_duenio_crea_perfil_y_barberia_en_un_batch() = runTest(timeout = 60.seconds) {
        val repo = repo()
        val r = repo.registerOwner(OwnerRegistration("dueno@test.com", "secret123", "Carlos", "Barber Gold", "5512345678"))
        assertEquals(AppResult.Success(Unit), r)
        val uid = FirebaseEmulator.auth.currentUser!!.uid
        assertEquals("OWNER", read("users/$uid")!!["staffRole"])
        assertEquals(uid, read("tenants/$uid")!!["ownerUid"])
        val user = (repo.session.first { it is SessionState.SignedIn } as SessionState.SignedIn).user
        assertEquals(StaffRole.OWNER, user.staffRole)
    }

    @Test fun a7_eliminar_cuenta_borra_perfil_y_cuenta_y_pide_limpiar_cache() = runTest(timeout = 60.seconds) {
        val repo = repo()
        repo.registerClient(ClientRegistration("ana@test.com", "secret123", "Ana", "5511112222"))
        val uid = FirebaseEmulator.auth.currentUser!!.uid
        repo.session.first { it is SessionState.SignedIn }

        assertTrue(repo.reauthenticate("mala") is AppResult.Failure)
        assertEquals(AppResult.Success(Unit), repo.reauthenticate("secret123"))
        assertEquals(AppResult.Success(Unit), repo.deleteAccount())

        assertNull(read("users/$uid"))
        assertNull(FirebaseEmulator.auth.currentUser)
        assertTrue("A16-f: el siguiente arranque limpia la caché", flag.value)
        // la cuenta ya no existe: no se puede volver a entrar
        assertTrue(repo.signIn("ana@test.com", "secret123") is AppResult.Failure)
    }
}
