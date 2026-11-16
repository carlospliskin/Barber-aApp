package com.barberia.feature.auth.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeAuthRepository
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.FakeUserDataEraser
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.DeleteAccountUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A7: eliminar cuenta y datos (Google Play lo exige). Sin Cloud Functions: todo desde la app. */
class DeleteAccountTest {

    private fun signedIn(role: StaffRole? = null) = FakeSessionRepository(SessionState.SignedIn(user(role)))

    @Test fun `confirma la contrasenia, borra los datos y al final la cuenta`() = runTest {
        val auth = FakeAuthRepository()
        val eraser = FakeUserDataEraser()
        val session = signedIn()

        val r = DeleteAccountUseCase(auth, session, eraser)("secreto1")

        assertEquals(AppResult.Success(Unit), r)
        assertEquals(listOf("reauthenticate", "deleteAccount"), auth.calls)
        assertEquals(listOf("uid-1" to null), eraser.erased)
        assertTrue("saveMode:null" in session.calls) // el siguiente usuario no hereda el modo
    }

    @Test fun `el duenio borra tambien su barberia`() = runTest {
        val eraser = FakeUserDataEraser()
        DeleteAccountUseCase(FakeAuthRepository(), signedIn(StaffRole.OWNER), eraser)("secreto1")
        assertEquals(listOf("uid-1" to "uid-1"), eraser.erased)
    }

    @Test fun `sin contrasenia no hace nada`() = runTest {
        val auth = FakeAuthRepository()
        val eraser = FakeUserDataEraser()
        val r = DeleteAccountUseCase(auth, signedIn(), eraser)("  ")
        assertEquals(AppResult.Failure(AuthFailure.Validation(setOf(FieldError.PASSWORD_EMPTY))), r)
        assertTrue(auth.calls.isEmpty())
        assertTrue(eraser.erased.isEmpty())
    }

    @Test fun `contrasenia incorrecta no borra nada`() = runTest {
        val auth = FakeAuthRepository().apply { reauthResult = AppResult.Failure(AuthFailure.InvalidCredentials) }
        val eraser = FakeUserDataEraser()
        val r = DeleteAccountUseCase(auth, signedIn(), eraser)("mala")
        assertEquals(AppResult.Failure(AuthFailure.InvalidCredentials), r)
        assertTrue(eraser.erased.isEmpty())
        assertEquals(listOf("reauthenticate"), auth.calls)
    }

    @Test fun `si falla el borrado de datos la cuenta sigue (se puede reintentar)`() = runTest {
        val auth = FakeAuthRepository()
        val eraser = FakeUserDataEraser(AppResult.Failure(AuthFailure.NoConnection))
        val r = DeleteAccountUseCase(auth, signedIn(), eraser)("secreto1")
        assertEquals(AppResult.Failure(AuthFailure.NoConnection), r)
        assertEquals(listOf("reauthenticate"), auth.calls)
    }

    @Test fun `sin sesion no hace nada`() = runTest {
        val auth = FakeAuthRepository()
        val r = DeleteAccountUseCase(auth, FakeSessionRepository(), FakeUserDataEraser())("secreto1")
        assertEquals(AppResult.Failure(AuthFailure.NotSignedIn), r)
        assertTrue(auth.calls.isEmpty())
    }
}
