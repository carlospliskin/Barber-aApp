package com.barberia.feature.auth.data

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.auth.data.gateway.AccountInfo
import com.barberia.feature.auth.data.gateway.AuthGateway
import com.barberia.feature.auth.data.gateway.ModeStore
import com.barberia.feature.auth.data.gateway.ProfileGateway
import com.barberia.feature.auth.data.gateway.ProfileRecord
import com.barberia.feature.auth.data.gateway.TenantRecord
import com.barberia.feature.auth.data.repository.FirebaseAccountRepository
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAccountRepositoryTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────
    private class FakeAuth : AuthGateway {
        val uid = MutableStateFlow<String?>(null)
        var account: AccountInfo? = null
        var error: GatewayError? = null
        val calls = mutableListOf<String>()

        override val uidChanges = uid
        override fun currentAccount() = account
        override suspend fun signIn(email: String, password: String) {
            calls += "signIn"
            error?.let { throw it }
            account = AccountInfo("u1", email, null, false)
            uid.value = "u1"
        }
        override suspend fun createUser(email: String, password: String): String {
            calls += "createUser"
            error?.let { throw it }
            account = AccountInfo("u1", email, null, false)
            uid.value = "u1"
            return "u1"
        }
        override suspend fun sendEmailVerification() {
            calls += "verify"
        }
        var serverVerified = false
        override suspend fun reload(): Boolean {
            calls += "reload"
            account = account?.copy(emailVerified = serverVerified)
            return serverVerified
        }
        override suspend fun sendPasswordReset(email: String) {
            calls += "reset"
            error?.let { throw it }
        }
        override suspend fun deleteCurrentUser() {
            calls += "delete"
            uid.value = null
            account = null
        }
        override suspend fun reauthenticate(password: String) {
            calls += "reauth"
            error?.let { throw it }
        }
        override fun signOut() {
            calls += "signOut"
            uid.value = null
            account = null
        }
    }

    private class FakeProfiles : ProfileGateway {
        val profiles = mutableMapOf<String, ProfileRecord>()
        val tenants = mutableMapOf<String, TenantRecord>()
        var fetchError: GatewayError? = null
        var writeError: GatewayError? = null
        var fetchCount = 0
        var writeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun fetch(uid: String): ProfileRecord? {
            fetchCount++
            fetchError?.let { throw it }
            return profiles[uid]
        }
        override suspend fun createOwner(uid: String, profile: ProfileRecord, tenant: TenantRecord) {
            writeGate?.await()
            writeError?.let { throw it }
            profiles[uid] = profile
            tenants[uid] = tenant
        }
        override suspend fun createClient(uid: String, profile: ProfileRecord) {
            writeError?.let { throw it }
            profiles[uid] = profile
        }
        override suspend fun delete(uid: String) {
            writeError?.let { throw it }
            profiles.remove(uid)
        }
        val existingTenants = mutableSetOf<String>()
        val revoked = mutableSetOf<Pair<String, String>>()
        override suspend fun tenantExists(tenantId: String) = tenantId in existingTenants
        override suspend fun isRevoked(tenantId: String, uid: String) = (tenantId to uid) in revoked
        override suspend fun leaveStaff(uid: String) {
            writeError?.let { throw it }
            profiles[uid] = profiles.getValue(uid).copy(staffTenantId = null, staffRole = null, staffBarberId = null)
        }
    }

    private class FakeModeStore : ModeStore {
        override val mode = MutableStateFlow<String?>(null)
        override suspend fun save(mode: String?) {
            this.mode.value = mode
        }
    }

    private val auth = FakeAuth()
    private val profiles = FakeProfiles()
    private val modes = FakeModeStore()

    private fun TestScope.repo(): FirebaseAccountRepository =
        FirebaseAccountRepository(auth, profiles, modes, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    private val owner = OwnerRegistration("ana@mail.com", "12345678", "Ana", "Barber Gold", "5512345678")

    // ── Sesión ────────────────────────────────────────────────────────────────
    @Test fun `sin usuario la sesion es SignedOut`() = runTest {
        assertEquals(SessionState.SignedOut, repo().session.first { it != SessionState.Loading })
    }

    @Test fun `login carga el perfil de Firestore y arma el usuario con su rol`() = runTest {
        profiles.profiles["u1"] = ProfileRecord("Ana", "ana@mail.com", null, "u1", "OWNER")
        val repo = repo()

        assertEquals(AppResult.Success(Unit), repo.signIn("ana@mail.com", "x"))

        val state = repo.session.first { it is SessionState.SignedIn } as SessionState.SignedIn
        assertEquals(StaffRole.OWNER, state.user.staffRole)
        assertEquals("u1", state.user.staffTenantId)
        assertEquals(state.user, repo.currentUser())
    }

    @Test fun `A10 un barbero trae el barbero del catalogo al que esta ligado`() = runTest {
        profiles.profiles["u1"] = ProfileRecord("Luis", "luis@mail.com", null, "t1", "BARBER", staffBarberId = "b1")
        val repo = repo()
        repo.signIn("luis@mail.com", "x")
        val state = repo.session.first { it is SessionState.SignedIn } as SessionState.SignedIn
        assertEquals("b1", state.user.staffBarberId)
        assertEquals(StaffRole.BARBER, state.user.staffRole)
    }

    @Test fun `usuario sin documento de perfil se trata como cliente`() = runTest {
        val repo = repo()
        repo.signIn("ana@mail.com", "x")
        val state = repo.session.first { it is SessionState.SignedIn } as SessionState.SignedIn
        assertEquals(null, state.user.staffRole)
        assertEquals("ana", state.user.displayName) // cae al usuario del email
    }

    @Test fun `sin red y sin cache el perfil queda Unavailable y se puede reintentar (A8)`() = runTest {
        profiles.fetchError = GatewayError.Network()
        val repo = repo()
        repo.signIn("ana@mail.com", "x")
        assertEquals(SessionState.Unavailable, repo.session.first { it != SessionState.Loading })

        profiles.fetchError = null
        profiles.profiles["u1"] = ProfileRecord("Ana", "ana@mail.com", null, null, null)
        repo.refresh()
        assertTrue(repo.session.first() is SessionState.SignedIn)
    }

    @Test fun `cerrar sesion vuelve a SignedOut`() = runTest {
        val repo = repo()
        repo.signIn("ana@mail.com", "x")
        repo.signOut()
        assertEquals(SessionState.SignedOut, repo.session.first())
        assertEquals(null, repo.currentUser())
    }

    // ── Errores ───────────────────────────────────────────────────────────────
    @Test fun `errores del gateway se traducen a fallos de dominio`() = runTest {
        val cases = mapOf(
            GatewayError.InvalidCredentials() to AuthFailure.InvalidCredentials,
            GatewayError.EmailInUse() to AuthFailure.EmailAlreadyInUse,
            GatewayError.WeakPassword() to AuthFailure.WeakPassword,
            GatewayError.Network() to AuthFailure.NoConnection,
            GatewayError.TooManyRequests() to AuthFailure.TooManyRequests
        )
        val repo = repo()
        cases.forEach { (error, expected) ->
            auth.error = error
            assertEquals(AppResult.Failure(expected), repo.signIn("ana@mail.com", "x"))
        }
    }

    // ── Registro ──────────────────────────────────────────────────────────────
    @Test fun `registro de duenio crea perfil y barberia con el uid como id (reglas sin lecturas extra)`() = runTest {
        val repo = repo()
        assertEquals(AppResult.Success(Unit), repo.registerOwner(owner))

        assertEquals(ProfileRecord("Ana", "ana@mail.com", "5512345678", "u1", "OWNER"), profiles.profiles["u1"])
        assertEquals(TenantRecord("Barber Gold", "u1", "America/Mexico_City"), profiles.tenants["u1"])
        assertTrue("verify" in auth.calls) // A2
        val state = repo.session.first { it is SessionState.SignedIn } as SessionState.SignedIn
        assertEquals(StaffRole.OWNER, state.user.staffRole) // refresh tras escribir el perfil
    }

    @Test fun `si falla escribir el perfil se borra la cuenta creada (A9 sin quedar a medias)`() = runTest {
        profiles.writeError = GatewayError.Network()
        val repo = repo()

        assertEquals(AppResult.Failure(AuthFailure.NoConnection), repo.registerOwner(owner))
        assertTrue("delete" in auth.calls)
        assertEquals(SessionState.SignedOut, repo.session.first())
    }

    @Test fun `registro de cliente no crea barberia`() = runTest {
        repo().registerClient(ClientRegistration("ana@mail.com", "12345678", "Ana", "5512345678"))
        assertEquals(ProfileRecord("Ana", "ana@mail.com", "5512345678", null, null), profiles.profiles["u1"])
        assertTrue(profiles.tenants.isEmpty())
    }

    @Test fun `H1 mientras se escribe el perfil la sesion no se publica como cliente`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        profiles.writeGate = gate
        val repo = repo()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.registerOwner(owner) }

        assertEquals(SessionState.Loading, repo.session.value) // ni SignedOut ni cliente

        gate.complete(Unit)
        job.join()
        val state = repo.session.value as SessionState.SignedIn
        assertEquals(StaffRole.OWNER, state.user.staffRole)
    }

    @Test fun `H1 si el registro falla la sesion vuelve a SignedOut`() = runTest {
        profiles.writeError = GatewayError.Network()
        val repo = repo()
        repo.registerOwner(owner)
        assertEquals(SessionState.SignedOut, repo.session.value)
    }

    @Test fun `A15 al comprobar la verificacion se recarga y la sesion muestra el correo verificado`() = runTest {
        val repo = repo()
        repo.signIn("ana@mail.com", "x")
        assertEquals(false, (repo.session.first() as SessionState.SignedIn).user.emailVerified)

        auth.serverVerified = true // el usuario abrió el enlace del correo
        assertEquals(AppResult.Success(true), repo.reloadUser())
        assertTrue("reload" in auth.calls)
        assertEquals(true, (repo.session.first() as SessionState.SignedIn).user.emailVerified)
    }

    // ── Recuperar contraseña ──────────────────────────────────────────────────
    @Test fun `recuperar contrasena no revela si el email existe`() = runTest {
        auth.error = GatewayError.InvalidCredentials()
        assertEquals(AppResult.Success(Unit), repo().sendPasswordReset("nadie@mail.com"))
    }

    // ── Modo ──────────────────────────────────────────────────────────────────
    @Test fun `el modo se guarda y se lee como enum`() = runTest {
        val repo = repo()
        repo.signIn("ana@mail.com", "x")
        repo.saveMode(AppMode.BARBER)
        assertEquals(AppMode.BARBER, repo.selectedMode.first())
        modes.mode.value = "BASURA"
        assertEquals(null, repo.selectedMode.first())
    }

    @Test fun `H4 el modo guardado pertenece a un usuario`() = runTest {
        val repo = repo()
        repo.signIn("ana@mail.com", "x") // uid u1
        repo.saveMode(AppMode.CLIENT)
        assertEquals(AppMode.CLIENT, repo.selectedMode.first())

        modes.mode.value = "otro-uid:OWNER" // modo que dejó otra cuenta
        assertEquals(null, repo.selectedMode.first())
    }

    // ── A7 eliminar cuenta ────────────────────────────────────────────────────
    @Test fun `A7 contrasenia incorrecta al confirmar da InvalidCredentials`() = runTest {
        auth.error = GatewayError.InvalidCredentials(null)
        assertEquals(AppResult.Failure(AuthFailure.InvalidCredentials), repo().reauthenticate("mala"))
    }

    @Test fun `A7 borra el perfil y luego la cuenta, y la sesion queda cerrada`() = runTest {
        profiles.profiles["u1"] = ProfileRecord("Ana", "ana@mail.com", null, null, null)
        val repo = repo()
        repo.signIn("ana@mail.com", "x")

        assertEquals(AppResult.Success(Unit), repo.deleteAccount())

        assertTrue("u1" !in profiles.profiles)
        assertEquals("delete", auth.calls.last())
        assertEquals(SessionState.SignedOut, repo.session.first { it != SessionState.Loading })
    }

    @Test fun `A7 si no se pudo borrar el perfil no se borra la cuenta (se reintenta)`() = runTest {
        profiles.profiles["u1"] = ProfileRecord("Ana", "ana@mail.com", null, null, null)
        val repo = repo()
        repo.signIn("ana@mail.com", "x")
        profiles.writeError = GatewayError.Network(null)

        assertEquals(AppResult.Failure(AuthFailure.NoConnection), repo.deleteAccount())
        assertTrue("delete" !in auth.calls)
    }

    // ── A7-b / A16 ────────────────────────────────────────────────────────────
    @Test fun `A16 barberia borrada, acceso revocado o vigente`() = runTest {
        profiles.profiles["u1"] = ProfileRecord("Luis", "luis@mail.com", null, "t1", "BARBER", staffBarberId = "b1")
        val repo = repo()
        repo.signIn("luis@mail.com", "x")
        repo.session.first { it is SessionState.SignedIn }
        assertEquals(AppResult.Success(com.barberia.feature.auth.domain.model.StaffAccess.SHOP_GONE), repo.staffAccess("t1"))
        profiles.existingTenants += "t1"
        assertEquals(AppResult.Success(com.barberia.feature.auth.domain.model.StaffAccess.ACTIVE), repo.staffAccess("t1"))
        profiles.revoked += "t1" to "u1"
        assertEquals(AppResult.Success(com.barberia.feature.auth.domain.model.StaffAccess.REVOKED), repo.staffAccess("t1"))
    }

    @Test fun `A7-b soltar el rol vuelve a leer el perfil y queda como cliente`() = runTest {
        profiles.profiles["u1"] = ProfileRecord("Luis", "luis@mail.com", null, "t1", "BARBER", staffBarberId = "b1")
        val repo = repo()
        repo.signIn("luis@mail.com", "x")
        repo.session.first { it is SessionState.SignedIn }

        assertEquals(AppResult.Success(Unit), repo.leaveStaff())

        val user = (repo.session.first { (it as? SessionState.SignedIn)?.user?.staffRole == null } as SessionState.SignedIn).user
        assertEquals(listOf(AppMode.CLIENT), user.availableModes)
    }
}
