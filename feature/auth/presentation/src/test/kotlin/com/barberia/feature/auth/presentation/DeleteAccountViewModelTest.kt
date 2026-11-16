package com.barberia.feature.auth.presentation

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
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.presentation.account.DeleteAccountIntent
import com.barberia.feature.auth.presentation.account.DeleteAccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeleteAccountViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val eraser = FakeUserDataEraser()
    private fun vm(role: StaffRole? = null): DeleteAccountViewModel {
        val session = FakeSessionRepository(SessionState.SignedIn(user(role)))
        return DeleteAccountViewModel(ObserveCurrentUserUseCase(session), DeleteAccountUseCase(auth, session, eraser))
    }

    @Test fun `el duenio ve que se borra su barberia`() {
        assertTrue(vm(StaffRole.OWNER).state.value.isOwner)
        assertFalse(vm().state.value.isOwner)
    }

    @Test fun `no se puede confirmar sin aceptar que es definitivo`() {
        val vm = vm()
        vm.onIntent(DeleteAccountIntent.PasswordChanged("secreto1"))
        assertFalse(vm.state.value.canConfirm)
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertTrue(auth.calls.isEmpty())
        vm.onIntent(DeleteAccountIntent.UnderstoodToggled)
        assertTrue(vm.state.value.canConfirm)
    }

    @Test fun `contrasenia vacia marca el campo`() {
        val vm = vm()
        vm.onIntent(DeleteAccountIntent.UnderstoodToggled)
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(setOf(FieldError.PASSWORD_EMPTY), vm.state.value.fieldErrors)
    }

    @Test fun `contrasenia incorrecta lo dice y no borra`() {
        auth.reauthResult = AppResult.Failure(AuthFailure.InvalidCredentials)
        val vm = vm()
        vm.onIntent(DeleteAccountIntent.UnderstoodToggled)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("mala"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(AuthFailure.InvalidCredentials, vm.state.value.failure)
        assertFalse(vm.state.value.deleting)
        assertTrue(eraser.erased.isEmpty())
    }

    @Test fun `al borrar no guarda la contrasenia (la sesion navega sola)`() {
        val vm = vm()
        vm.onIntent(DeleteAccountIntent.UnderstoodToggled)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("secreto1"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(listOf("reauthenticate", "deleteAccount"), auth.calls)
        assertEquals("", vm.state.value.password)
        assertNull(vm.state.value.failure)
    }
}
