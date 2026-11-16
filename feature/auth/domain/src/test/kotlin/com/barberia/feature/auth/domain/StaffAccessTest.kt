package com.barberia.feature.auth.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffAccess
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeAuthRepository
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.CheckStaffAccessUseCase
import com.barberia.feature.auth.domain.usecase.LeaveStaffUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A7-b / A16: el barbero se entera de que perdió el acceso y puede volver a ser solo cliente. */
class StaffAccessTest {
    private val barber = user(StaffRole.BARBER).copy(staffTenantId = "t1", staffBarberId = "b1")

    @Test fun `un barbero revisa si su barberia existe y si lo revocaron`() = runTest {
        val auth = FakeAuthRepository().apply { access = StaffAccess.REVOKED }
        val r = CheckStaffAccessUseCase(auth, FakeSessionRepository(SessionState.SignedIn(barber)))()
        assertEquals(AppResult.Success(StaffAccess.REVOKED), r)
        assertEquals(listOf("staffAccess:t1"), auth.calls)
    }

    @Test fun `dueño y cliente no gastan lecturas`() = runTest {
        val auth = FakeAuthRepository()
        assertEquals(AppResult.Success(StaffAccess.ACTIVE), CheckStaffAccessUseCase(auth, FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER))))())
        assertEquals(AppResult.Success(StaffAccess.ACTIVE), CheckStaffAccessUseCase(auth, FakeSessionRepository(SessionState.SignedIn(user())))())
        assertTrue(auth.calls.isEmpty())
    }

    @Test fun `soltar el rol olvida el modo barbero`() = runTest {
        val auth = FakeAuthRepository()
        val session = FakeSessionRepository(SessionState.SignedIn(barber))
        assertEquals(AppResult.Success(Unit), LeaveStaffUseCase(auth, session)())
        assertEquals(listOf("leaveStaff"), auth.calls)
        assertTrue("saveMode:null" in session.calls)
    }

    @Test fun `el modo se borra DESPUES de soltar el rol (borrarlo antes navega y cancela la pantalla a medias)`() = runTest {
        val session = FakeSessionRepository(SessionState.SignedIn(barber))
        val auth = FakeAuthRepository().apply { onCall = { session.calls += "auth:$it" } }
        LeaveStaffUseCase(auth, session)()
        assertEquals(listOf("auth:leaveStaff", "saveMode:null"), session.calls)
    }

    @Test fun `el dueño no suelta su rol`() = runTest {
        val auth = FakeAuthRepository()
        val r = LeaveStaffUseCase(auth, FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER))))()
        assertEquals(AppResult.Failure(com.barberia.feature.auth.domain.model.AuthFailure.ModeNotAllowed), r)
        assertTrue(auth.calls.isEmpty())
    }
}
