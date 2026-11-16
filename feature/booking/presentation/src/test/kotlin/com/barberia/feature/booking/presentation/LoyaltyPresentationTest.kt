package com.barberia.feature.booking.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.testing.FakeJoinedStore
import com.barberia.feature.booking.domain.usecase.ForgivePenaltyUseCase
import com.barberia.feature.booking.domain.usecase.ObserveClientsUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyLoyaltyUseCase
import com.barberia.feature.booking.domain.usecase.RedeemRewardUseCase
import com.barberia.feature.booking.presentation.clients.ClientsIntent
import com.barberia.feature.booking.presentation.clients.ClientsViewModel
import com.barberia.feature.booking.presentation.loyalty.LoyaltyViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoyaltyPresentationTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val now = Instant.parse("2026-11-16T17:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val loyalty = LoyaltyPolicy()
    private val repo = FakeBookingRepository().apply {
        barbershops["t1"] = Barbershop("t1", "Gold", ZoneId.of("America/Mexico_City"), null)
        clients["c1"] = ClientRecord("c1", "Ana", 12, 0, 0, null)
        clients["c2"] = ClientRecord("c2", "Beto", 2, 3, 0, now.plusSeconds(86_400))
    }

    private fun clientsVm() = ClientsViewModel(
        ObserveCurrentUserUseCase(FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER).copy(staffTenantId = "t1")))),
        ObserveClientsUseCase(repo),
        RedeemRewardUseCase(repo, loyalty),
        ForgivePenaltyUseCase(repo),
        loyalty,
        clock
    )

    @Test fun `P5 el duenio ve sus clientes, penalizados primero`() {
        val vm = clientsVm()
        assertEquals(listOf("Beto", "Ana"), vm.state.value.clients.map { it.name })
        assertTrue(vm.state.value.isPenalized(vm.state.value.clients[0]))
        assertEquals(1, vm.state.value.rewards(vm.state.value.clients[1]))
    }

    @Test fun `P4 canjear premio y P5 perdonar`() {
        val vm = clientsVm()
        vm.onIntent(ClientsIntent.Redeem("c1"))
        assertEquals(1, repo.clients["c1"]!!.rewardsUsed)
        vm.onIntent(ClientsIntent.Redeem("c1"))
        assertEquals(BookingFailure.NoRewardAvailable, vm.state.value.failure)
        vm.onIntent(ClientsIntent.Forgive("c2"))
        assertFalse(vm.state.value.isPenalized(vm.state.value.clients.first { it.uid == "c2" }))
    }

    @Test fun `P6 el cliente ve su progreso y su penalizacion`() {
        val store = FakeJoinedStore().apply { saved["c2"] = "t1" }
        val session = FakeSessionRepository(SessionState.SignedIn(user().copy(uid = "c2")))
        val vm = LoyaltyViewModel(
            ObserveCurrentUserUseCase(session),
            ObserveJoinedBarbershopUseCase(repo, store),
            ObserveMyLoyaltyUseCase(repo),
            loyalty,
            clock
        )
        val s = vm.state.value
        assertEquals(2, s.visitsTowardNext)
        assertEquals(10, s.visitsPerReward)
        assertEquals(now.plusSeconds(86_400), s.penalizedUntil)
        assertFalse(s.canBook) // U10: se avisa antes de entrar a reservar
    }
}
