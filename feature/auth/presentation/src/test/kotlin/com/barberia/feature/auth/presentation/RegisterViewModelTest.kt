package com.barberia.feature.auth.presentation

import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import com.barberia.feature.auth.domain.testing.FakeAuthRepository
import com.barberia.feature.auth.domain.usecase.RegisterClientUseCase
import com.barberia.feature.auth.domain.usecase.RegisterOwnerUseCase
import com.barberia.feature.auth.presentation.register.AccountType
import com.barberia.feature.auth.presentation.register.RegisterIntent
import com.barberia.feature.auth.presentation.register.RegisterViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegisterViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private fun vm() = RegisterViewModel(RegisterOwnerUseCase(auth), RegisterClientUseCase(auth))

    private fun RegisterViewModel.fill(type: AccountType) {
        onIntent(RegisterIntent.TypeChanged(type))
        onIntent(RegisterIntent.EmailChanged("ana@mail.com"))
        onIntent(RegisterIntent.PasswordChanged("12345678"))
        onIntent(RegisterIntent.NameChanged("Ana"))
        onIntent(RegisterIntent.PhoneChanged("5512345678"))
    }

    @Test fun `cliente registra con sus datos`() = runTest {
        val vm = vm()
        vm.fill(AccountType.CLIENT)
        vm.onIntent(RegisterIntent.Submit)
        assertEquals(ClientRegistration("ana@mail.com", "12345678", "Ana", "5512345678"), auth.lastClient)
    }

    @Test fun `duenio registra la barberia`() = runTest {
        val vm = vm()
        vm.fill(AccountType.OWNER)
        vm.onIntent(RegisterIntent.BusinessNameChanged("Barber Gold"))
        vm.onIntent(RegisterIntent.Submit)
        assertEquals(OwnerRegistration("ana@mail.com", "12345678", "Ana", "Barber Gold", "5512345678"), auth.lastOwner)
    }

    @Test fun `duenio sin nombre de barberia marca ese campo y no envia`() = runTest {
        val vm = vm()
        vm.fill(AccountType.OWNER)
        vm.onIntent(RegisterIntent.Submit)
        assertEquals(setOf(FieldError.BUSINESS_NAME_REQUIRED), vm.state.value.fieldErrors)
        assertTrue(auth.calls.isEmpty())
    }

    @Test fun `cambiar a cliente quita el error de barberia`() = runTest {
        val vm = vm()
        vm.fill(AccountType.OWNER)
        vm.onIntent(RegisterIntent.Submit)
        vm.onIntent(RegisterIntent.TypeChanged(AccountType.CLIENT))
        assertEquals(emptySet<FieldError>(), vm.state.value.fieldErrors)
    }
}
