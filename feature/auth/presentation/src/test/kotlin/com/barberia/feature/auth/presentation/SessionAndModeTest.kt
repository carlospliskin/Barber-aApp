package com.barberia.feature.auth.presentation

import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.ObserveSessionDestinationUseCase
import com.barberia.feature.auth.domain.usecase.ObserveSessionStateUseCase
import com.barberia.feature.auth.domain.usecase.RetrySessionUseCase
import com.barberia.feature.auth.domain.usecase.SelectModeUseCase
import com.barberia.feature.auth.presentation.mode.ModeSelectorIntent
import com.barberia.feature.auth.presentation.mode.ModeSelectorViewModel
import com.barberia.feature.auth.presentation.session.SessionIntent
import com.barberia.feature.auth.presentation.session.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionAndModeTest {
    @get:Rule val main = MainDispatcherRule()

    private fun sessionVm(repo: FakeSessionRepository) = SessionViewModel(
        ObserveSessionDestinationUseCase(repo),
        ObserveSessionStateUseCase(repo),
        RetrySessionUseCase(repo),
    )

    @Test fun `la sesion decide el destino y lo actualiza sola (hueco 1)`() {
        val repo = FakeSessionRepository(SessionState.Loading)
        val vm = sessionVm(repo)
        assertEquals(null, vm.state.value.destination)

        repo.sessionFlow.value = SessionState.SignedIn(user(StaffRole.OWNER))
        assertEquals(SessionDestination.ModeSelection, vm.state.value.destination)

        repo.sessionFlow.value = SessionState.SignedOut
        assertEquals(SessionDestination.Login, vm.state.value.destination)
    }

    @Test fun `sin perfil por falta de red muestra reintentar (A8 y hueco 4)`() {
        val repo = FakeSessionRepository(SessionState.Unavailable)
        val vm = sessionVm(repo)
        assertTrue(vm.state.value.showRetry)

        vm.onIntent(SessionIntent.Retry)
        assertEquals(listOf("refresh"), repo.calls)
    }

    @Test fun `el selector muestra los modos del usuario sin pedirlo desde la pantalla (hueco 3)`() {
        val repo = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.BARBER)))
        val vm = ModeSelectorViewModel(ObserveCurrentUserUseCase(repo), SelectModeUseCase(repo))
        assertEquals("Ana Pérez", vm.state.value.displayName)
        assertEquals(listOf(AppMode.BARBER, AppMode.CLIENT), vm.state.value.modes)
    }

    @Test fun `elegir modo lo guarda localmente`() {
        val repo = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER)))
        val vm = ModeSelectorViewModel(ObserveCurrentUserUseCase(repo), SelectModeUseCase(repo))
        vm.onIntent(ModeSelectorIntent.Select(AppMode.OWNER))
        assertEquals(AppMode.OWNER, repo.modeFlow.value)
        assertFalse(vm.state.value.isSaving)
    }

    @Test fun `modo no permitido muestra el error`() {
        val repo = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.BARBER)))
        val vm = ModeSelectorViewModel(ObserveCurrentUserUseCase(repo), SelectModeUseCase(repo))
        vm.onIntent(ModeSelectorIntent.Select(AppMode.OWNER))
        assertEquals(AuthFailure.ModeNotAllowed, vm.state.value.failure)
    }
}
