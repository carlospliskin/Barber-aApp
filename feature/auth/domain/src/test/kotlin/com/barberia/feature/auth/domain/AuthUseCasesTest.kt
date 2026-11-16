package com.barberia.feature.auth.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import com.barberia.feature.auth.domain.testing.*
import com.barberia.feature.auth.domain.usecase.ObserveSessionDestinationUseCase
import com.barberia.feature.auth.domain.usecase.RegisterClientUseCase
import com.barberia.feature.auth.domain.usecase.RegisterOwnerUseCase
import com.barberia.feature.auth.domain.usecase.SelectModeUseCase
import com.barberia.feature.auth.domain.usecase.SendPasswordResetUseCase
import com.barberia.feature.auth.domain.usecase.SignInUseCase
import com.barberia.feature.auth.domain.usecase.SignOutUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCasesTest {

    // ── Iniciar sesión ────────────────────────────────────────────────────────
    @Test fun `login limpia espacios del email`() = runTest {
        val auth = FakeAuthRepository()
        SignInUseCase(auth)("  ana@mail.com ", "secreto")
        assertEquals(listOf("signIn:ana@mail.com"), auth.calls)
    }

    @Test fun `login invalido no llega al repositorio y dice que campo fallo`() = runTest {
        val auth = FakeAuthRepository()
        val result = SignInUseCase(auth)("ana", "")
        assertEquals(
            AppResult.Failure(AuthFailure.Validation(setOf(FieldError.EMAIL_INVALID, FieldError.PASSWORD_EMPTY))),
            result
        )
        assertTrue(auth.calls.isEmpty())
    }

    // ── Registro ──────────────────────────────────────────────────────────────
    @Test fun `registro de duenio valido normaliza datos`() = runTest {
        val auth = FakeAuthRepository()
        RegisterOwnerUseCase(auth)(
            OwnerRegistration(" ana@mail.com", "12345678", " Ana ", " Barber Gold ", " 55 1234 5678 ")
        )
        assertEquals(
            OwnerRegistration("ana@mail.com", "12345678", "Ana", "Barber Gold", "5512345678"),
            auth.lastOwner
        )
    }

    @Test fun `el email se guarda en minusculas como lo guarda Firebase (si no, las reglas rechazan el perfil)`() = runTest {
        val auth = FakeAuthRepository()
        RegisterClientUseCase(auth)(ClientRegistration("Ana@Mail.COM", "12345678", "Ana", "5512345678"))
        assertEquals("ana@mail.com", auth.lastClient?.email)
        SignInUseCase(auth)("Ana@Mail.COM", "x")
        assertEquals("signIn:ana@mail.com", auth.calls.last())
    }

    @Test fun `registro de duenio sin telefono es valido`() = runTest {
        val auth = FakeAuthRepository()
        val r = RegisterOwnerUseCase(auth)(OwnerRegistration("ana@mail.com", "12345678", "Ana", "Gold", null))
        assertEquals(AppResult.Success(Unit), r)
    }

    @Test fun `registro de cliente exige telefono`() = runTest {
        val auth = FakeAuthRepository()
        val r = RegisterClientUseCase(auth)(ClientRegistration("ana@mail.com", "12345678", "Ana", ""))
        assertEquals(AppResult.Failure(AuthFailure.Validation(setOf(FieldError.PHONE_INVALID))), r)
        assertTrue(auth.calls.isEmpty())
    }

    // ── Recuperar contraseña (A1) ─────────────────────────────────────────────
    @Test fun `recuperar contrasena valida el email`() = runTest {
        val auth = FakeAuthRepository()
        assertEquals(
            AppResult.Failure(AuthFailure.Validation(setOf(FieldError.EMAIL_INVALID))),
            SendPasswordResetUseCase(auth)("nada")
        )
        SendPasswordResetUseCase(auth)(" ana@mail.com ")
        assertEquals(listOf("reset:ana@mail.com"), auth.calls)
    }

    // ── Verificación de correo (A14) ─────────────────────────────────────────
    @Test fun `A14 reenviar correo de verificacion`() = runTest {
        val auth = FakeAuthRepository()
        assertEquals(AppResult.Success(Unit), com.barberia.feature.auth.domain.usecase.ResendVerificationEmailUseCase(auth)())
        assertEquals(listOf("resendVerification"), auth.calls)
    }

    @Test fun `A14 A15 comprobar si ya verifico recarga el usuario`() = runTest {
        val auth = FakeAuthRepository().apply { verified = true }
        assertEquals(AppResult.Success(true), com.barberia.feature.auth.domain.usecase.CheckEmailVerifiedUseCase(auth)())
        assertEquals(listOf("reloadUser"), auth.calls)
    }

    // ── Elegir modo (A4) ──────────────────────────────────────────────────────
    @Test fun `elegir un modo permitido lo guarda`() = runTest {
        val session = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER)))
        assertEquals(AppResult.Success(Unit), SelectModeUseCase(session)(AppMode.CLIENT))
        assertEquals(AppMode.CLIENT, session.modeFlow.value)
    }

    @Test fun `no se puede elegir un modo que el usuario no tiene`() = runTest {
        val session = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.BARBER)))
        assertEquals(AppResult.Failure(AuthFailure.ModeNotAllowed), SelectModeUseCase(session)(AppMode.OWNER))
        assertEquals(null, session.modeFlow.value)
    }

    @Test fun `sin sesion no se puede elegir modo`() = runTest {
        assertEquals(
            AppResult.Failure(AuthFailure.NotSignedIn),
            SelectModeUseCase(FakeSessionRepository())(AppMode.CLIENT)
        )
    }

    // ── Cerrar sesión (A5) ────────────────────────────────────────────────────
    @Test fun `cerrar sesion borra el modo antes de salir`() = runTest {
        val auth = FakeAuthRepository()
        val session = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER)), AppMode.OWNER)
        SignOutUseCase(auth, session)()
        assertEquals(listOf("saveMode:null"), session.calls)
        assertEquals(listOf("signOut"), auth.calls)
    }

    // ── Destino observable (huecos 1 y 8) ─────────────────────────────────────
    @Test fun `el destino reacciona a la sesion y al modo sin repetir valores`() = runTest {
        val session = FakeSessionRepository(SessionState.Loading)
        val observe = ObserveSessionDestinationUseCase(session)

        assertEquals(null, observe().first())

        session.sessionFlow.value = SessionState.SignedIn(user(StaffRole.OWNER))
        assertEquals(SessionDestination.ModeSelection, observe().first())

        session.modeFlow.value = AppMode.OWNER
        assertEquals(SessionDestination.OwnerHome, observe().first())

        session.sessionFlow.value = SessionState.SignedOut
        assertEquals(SessionDestination.Login, observe().first())
    }

    @Test fun `el destino no emite duplicados`() = runTest {
        val session = FakeSessionRepository(SessionState.SignedOut)
        val observe = ObserveSessionDestinationUseCase(session)
        val values = mutableListOf<SessionDestination?>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            observe().collect { values += it }
        }
        session.modeFlow.value = AppMode.CLIENT // sigue en Login
        assertEquals(listOf(SessionDestination.Login), values)
    }
}
