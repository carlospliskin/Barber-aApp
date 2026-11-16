package com.barberia.feature.auth.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffAccess
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeAuthRepository
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.CheckStaffAccessUseCase
import com.barberia.feature.auth.domain.usecase.LeaveStaffUseCase
import com.barberia.feature.auth.presentation.account.StaffAccessIntent
import com.barberia.feature.auth.presentation.account.StaffAccessViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class StaffAccessViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val session = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.BARBER).copy(staffTenantId = "t1")))
    private fun vm() = StaffAccessViewModel(CheckStaffAccessUseCase(auth, session), LeaveStaffUseCase(auth, session))

    @Test fun `revisa al abrir y no muestra nada si sigue vigente`() {
        val vm = vm()
        assertEquals(StaffAccess.ACTIVE, vm.state.value.access)
        assertEquals(listOf("staffAccess:t1"), auth.calls)
    }

    @Test fun `avisa si la barberia ya no existe o si le quitaron el acceso`() {
        auth.access = StaffAccess.SHOP_GONE
        assertEquals(StaffAccess.SHOP_GONE, vm().state.value.access)
        auth.access = StaffAccess.REVOKED
        assertEquals(StaffAccess.REVOKED, vm().state.value.access)
    }

    @Test fun `volver a ser cliente suelta el rol`() {
        auth.access = StaffAccess.REVOKED
        val vm = vm()
        vm.onIntent(StaffAccessIntent.Leave)
        assertEquals("leaveStaff", auth.calls.last())
        assertFalse(vm.state.value.leaving)
    }
}
