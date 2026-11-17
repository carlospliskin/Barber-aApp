package com.barberia.feature.booking.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.ChangeShopZoneUseCase
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.presentation.owner.ShopZoneIntent
import com.barberia.feature.booking.presentation.owner.ShopZoneViewModel
import com.barberia.feature.catalog.domain.model.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** N5: tarjeta del dueño para elegir la zona horaria. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopZonePresentationTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val cdmx = ZoneId.of("America/Mexico_City")
    private val tijuana = ZoneId.of("America/Tijuana")
    private val clock = Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneId.of("UTC"))
    private val repo = FakeBookingRepository().apply { barbershops["uid-1"] = Barbershop("uid-1", "Gold", cdmx, null) }

    private fun vm(role: StaffRole = StaffRole.OWNER) = ShopZoneViewModel(
        ObserveCurrentUserUseCase(FakeSessionRepository(SessionState.SignedIn(user(role).copy(emailVerified = true)))),
        GetBarbershopUseCase(repo),
        ChangeShopZoneUseCase(repo, clock)
    )

    @Test fun `muestra la zona actual y la cambia`() {
        val vm = vm()
        assertEquals(cdmx, vm.state.value.zone)
        vm.onIntent(ShopZoneIntent.Select(tijuana))
        assertEquals(tijuana, vm.state.value.zone)
        assertFalse(vm.state.value.saving)
        assertNull(vm.state.value.failure)
    }

    @Test fun `con citas por venir explica por que no y deja la zona como estaba`() {
        runBlocking {
            repo.book(
                Appointment(
                    "a1", "uid-1", "cli", "Ana", "b1", "Luis", "s1", "Corte", Money(100), LocalDate.of(2026, 11, 20), 600, 630,
                    AppointmentStatus.BOOKED, Instant.parse("2026-11-20T16:00:00Z")
                )
            )
        }
        val vm = vm()
        vm.onIntent(ShopZoneIntent.Select(tijuana))
        assertEquals(cdmx, vm.state.value.zone)
        assertEquals(BookingFailure.HasActiveAppointments, vm.state.value.failure)
    }

    @Test fun `un barbero no ve la zona (no la puede cambiar)`() = assertNull(vm(StaffRole.BARBER).state.value.zone)
}
