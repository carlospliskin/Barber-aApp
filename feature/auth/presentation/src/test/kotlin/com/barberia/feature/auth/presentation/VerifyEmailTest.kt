package com.barberia.feature.auth.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeAuthRepository
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.CheckEmailVerifiedUseCase
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.ResendVerificationEmailUseCase
import com.barberia.feature.auth.presentation.verify.VerifyEmailIntent
import com.barberia.feature.auth.presentation.verify.VerifyEmailViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class VerifyEmailTest {
    @get:Rule val main = MainDispatcherRule()

    private var now = Instant.parse("2026-11-16T17:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val auth = FakeAuthRepository()
    private val session = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER)))
    private fun vm() = VerifyEmailViewModel(
        ObserveCurrentUserUseCase(session),
        ResendVerificationEmailUseCase(auth),
        CheckEmailVerifiedUseCase(auth),
        clock,
    )

    @Test fun `A14 reenviar y no volver a reenviar antes de 60 segundos`() {
        val vm = vm()
        vm.onIntent(VerifyEmailIntent.Resend)
        assertTrue(vm.state.value.sent)
        vm.onIntent(VerifyEmailIntent.Resend) // muy pronto: no se manda otro (Firebase limita a 1000/día)
        assertEquals(listOf("resendVerification"), auth.calls)
        now = now.plus(Duration.ofSeconds(61))
        vm.onIntent(VerifyEmailIntent.Resend)
        assertEquals(2, auth.calls.size)
    }

    @Test fun `A14 todavia no verifica avisa que no`() {
        val vm = vm()
        vm.onIntent(VerifyEmailIntent.Check)
        assertTrue(vm.state.value.notYet)
        assertFalse(vm.state.value.checking)
    }

    @Test fun `A14 ya verifico`() {
        auth.verified = true
        val vm = vm()
        vm.onIntent(VerifyEmailIntent.Check)
        assertFalse(vm.state.value.notYet)
    }
}
