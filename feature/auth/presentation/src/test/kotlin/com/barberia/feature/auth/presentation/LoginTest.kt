package com.barberia.feature.auth.presentation

import app.cash.turbine.test
import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.testing.FakeAuthRepository
import com.barberia.feature.auth.domain.usecase.SendPasswordResetUseCase
import com.barberia.feature.auth.domain.usecase.SignInUseCase
import com.barberia.feature.auth.presentation.login.LoginEffect
import com.barberia.feature.auth.presentation.login.LoginIntent
import com.barberia.feature.auth.presentation.login.LoginMutation
import com.barberia.feature.auth.presentation.login.LoginState
import com.barberia.feature.auth.presentation.login.LoginViewModel
import com.barberia.feature.auth.presentation.login.reduce
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private fun vm() = LoginViewModel(SignInUseCase(auth), SendPasswordResetUseCase(auth))

    // ── Reducer puro (hueco 5) ────────────────────────────────────────────────
    @Test fun `escribir limpia el error de ese campo y el mensaje general`() {
        val s = LoginState(fieldErrors = setOf(FieldError.EMAIL_INVALID, FieldError.PASSWORD_EMPTY), failure = AuthFailure.NoConnection)
        val r = s.reduce(LoginMutation.EmailChanged("a"))
        assertEquals("a", r.email)
        assertEquals(setOf(FieldError.PASSWORD_EMPTY), r.fieldErrors)
        assertEquals(null, r.failure)
    }

    @Test fun `un fallo de validacion va a los campos, no al mensaje general`() {
        val r = LoginState(isLoading = true).reduce(
            LoginMutation.Failed(AuthFailure.Validation(setOf(FieldError.EMAIL_INVALID)))
        )
        assertFalse(r.isLoading)
        assertEquals(setOf(FieldError.EMAIL_INVALID), r.fieldErrors)
        assertEquals(null, r.failure)
    }

    @Test fun `exito borra la contrasena del estado`() {
        val r = LoginState(email = "a@b.co", password = "x", isLoading = true).reduce(LoginMutation.Succeeded)
        assertEquals("", r.password)
        assertFalse(r.isLoading)
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────
    @Test fun `enviar llama al caso de uso y la navegacion la decide la sesion, no el login`() = runTest {
        val vm = vm()
        vm.onIntent(LoginIntent.EmailChanged("ana@mail.com"))
        vm.onIntent(LoginIntent.PasswordChanged("secreto"))
        vm.effects.test {
            vm.onIntent(LoginIntent.Submit)
            expectNoEvents()
        }
        assertEquals(listOf("signIn:ana@mail.com"), auth.calls)
    }

    @Test fun `credenciales invalidas se muestran como mensaje general`() = runTest {
        auth.result = AppResult.Failure(AuthFailure.InvalidCredentials)
        val vm = vm()
        vm.onIntent(LoginIntent.EmailChanged("ana@mail.com"))
        vm.onIntent(LoginIntent.PasswordChanged("x"))
        vm.onIntent(LoginIntent.Submit)
        assertEquals(AuthFailure.InvalidCredentials, vm.state.value.failure)
    }

    @Test fun `olvide mi contrasena avisa que se envio el correo (A1)`() = runTest {
        val vm = vm()
        vm.onIntent(LoginIntent.EmailChanged("ana@mail.com"))
        vm.effects.test {
            vm.onIntent(LoginIntent.ForgotPassword)
            assertEquals(LoginEffect.PasswordResetSent, awaitItem())
        }
    }

    @Test fun `olvide mi contrasena sin email valido marca el campo`() = runTest {
        val vm = vm()
        vm.onIntent(LoginIntent.ForgotPassword)
        assertTrue(FieldError.EMAIL_INVALID in vm.state.value.fieldErrors)
        assertTrue(auth.calls.isEmpty())
    }

    @Test fun `ir a registro es un efecto`() = runTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(LoginIntent.GoToRegister)
            assertEquals(LoginEffect.OpenRegister, awaitItem())
        }
    }
}
