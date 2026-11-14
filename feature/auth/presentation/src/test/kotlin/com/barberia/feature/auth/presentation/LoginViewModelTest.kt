package com.barberia.feature.auth.presentation

import app.cash.turbine.test
import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.repository.AuthRepository
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.RegisterClientRequest
import com.barberia.feature.auth.domain.repository.RegisterOwnerRequest
import com.barberia.feature.auth.domain.usecase.LoginUseCase
import com.barberia.feature.auth.presentation.login.LoginEffect
import com.barberia.feature.auth.presentation.login.LoginIntent
import com.barberia.feature.auth.presentation.login.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private class FakeRepository(var loginResult: AuthResult<AuthUser>) : AuthRepository {
        override val currentUser: AuthUser? = null
        override suspend fun restoreSession(): AuthResult<AuthUser?> = AppResult.Success(null)
        override suspend fun login(email: String, password: String) = loginResult
        override suspend fun registerOwner(request: RegisterOwnerRequest) = error("no usado")
        override suspend fun registerClient(request: RegisterClientRequest) = error("no usado")
        override suspend fun switchMode(mode: Capability) = error("no usado")
        override suspend fun logout() = Unit
    }

    private val owner = AuthUser("u1", "t", "r", setOf(Capability.OWNER, Capability.CLIENT), Capability.OWNER)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun LoginViewModel.fill() {
        onIntent(LoginIntent.EmailChanged("ana@mail.com"))
        onIntent(LoginIntent.PasswordChanged("secreto"))
    }

    @Test fun `login exitoso de duenio navega al selector de modo`() = runTest {
        val vm = LoginViewModel(LoginUseCase(FakeRepository(AppResult.Success(owner))))
        vm.fill()

        vm.effects.test {
            vm.onIntent(LoginIntent.Submit)
            assertEquals(LoginEffect.Navigate(SessionDestination.ModeSelection), awaitItem())
        }
        assertFalse(vm.state.value.isLoading)
        assertEquals("", vm.state.value.password)
    }

    @Test fun `login fallido muestra el mensaje y escribir lo limpia`() = runTest {
        val vm = LoginViewModel(LoginUseCase(FakeRepository(AppResult.Failure(AuthFailure.InvalidCredentials))))
        vm.fill()

        vm.onIntent(LoginIntent.Submit)
        assertEquals("Email o contraseña incorrectos", vm.state.value.errorMessage)

        vm.onIntent(LoginIntent.PasswordChanged("otra"))
        assertEquals(null, vm.state.value.errorMessage)
    }

    @Test fun `no envia con campos vacios`() = runTest {
        val vm = LoginViewModel(LoginUseCase(FakeRepository(AppResult.Success(owner))))
        assertFalse(vm.state.value.canSubmit)
        vm.onIntent(LoginIntent.Submit)
        assertFalse(vm.state.value.isLoading)
    }
}
