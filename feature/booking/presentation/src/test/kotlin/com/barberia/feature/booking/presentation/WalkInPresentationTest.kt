package com.barberia.feature.booking.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.BookWalkInUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.presentation.walkin.WalkInEffect
import com.barberia.feature.booking.presentation.walkin.WalkInIntent
import com.barberia.feature.booking.presentation.walkin.WalkInViewModel
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** N1: pantalla "Agendar cliente" del personal. */
@OptIn(ExperimentalCoroutinesApi::class)
class WalkInPresentationTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Mexico_City")
    private val clock = Clock.fixed(Instant.parse("2026-11-16T16:07:00Z"), ZoneId.of("UTC")) // lunes 10:07 CDMX
    private val monday = LocalDate.of(2026, 11, 16)
    private val policy = BookingPolicy()
    private val week = WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "12:00"))))
    private val catalog = FakeCatalogRepository(
        Catalog(
            listOf(Service("s1", "Corte", 30, Money(15_000))),
            listOf(Barber("b1", "Luis", schedule = week), Barber("b2", "Pepe", schedule = week)),
            1
        )
    )
    private val bookings = FakeBookingRepository().apply { barbershops["t1"] = Barbershop("t1", "Gold", zone, null) }
    private var n = 0

    private fun vm(role: StaffRole, barberId: String? = null) = WalkInViewModel(
        ObserveCurrentUserUseCase(
            FakeSessionRepository(SessionState.SignedIn(user(role).copy(staffTenantId = "t1", staffBarberId = barberId)))
        ),
        GetBarbershopUseCase(bookings),
        ObserveCatalogUseCase(catalog),
        GetAvailableSlotsUseCase(catalog, bookings, policy, clock),
        BookWalkInUseCase(catalog, bookings, policy, clock) { "w-${++n}" },
        clock
    )

    @Test fun `N1 el barbero agenda solo en su agenda y empieza en hoy`() {
        val vm = vm(StaffRole.BARBER, barberId = "b1")
        val s = vm.state.value
        assertEquals(false, s.canChooseBarber)
        assertEquals("b1", s.selectedBarberId)
        assertEquals(monday, s.selectedDate)
        vm.onIntent(WalkInIntent.SelectBarber("b2"))
        assertEquals("b1", vm.state.value.selectedBarberId)
    }

    @Test fun `N1 el duenio elige barbero`() {
        val vm = vm(StaffRole.OWNER)
        assertEquals(true, vm.state.value.canChooseBarber)
        assertNull(vm.state.value.selectedBarberId)
        vm.onIntent(WalkInIntent.SelectBarber("b2"))
        assertEquals("b2", vm.state.value.selectedBarberId)
    }

    @Test fun `N1 las horas incluyen el turno actual (el cliente ya esta en el local)`() {
        val vm = vm(StaffRole.BARBER, barberId = "b1")
        vm.onIntent(WalkInIntent.SelectService("s1"))
        assertEquals("10:00", TimeRange.label(vm.state.value.slots.first()))
    }

    @Test fun `N1 confirmar crea la cita sin cuenta y vuelve a la agenda`() = runTest {
        val vm = vm(StaffRole.BARBER, barberId = "b1")
        vm.onIntent(WalkInIntent.NameChanged("Pedro"))
        vm.onIntent(WalkInIntent.PhoneChanged("55 1234 5678"))
        vm.onIntent(WalkInIntent.SelectService("s1"))
        vm.onIntent(WalkInIntent.SelectSlot(600))
        vm.onIntent(WalkInIntent.Confirm)
        assertEquals(WalkInEffect.Booked, vm.effects.first())
        val a = bookings.created.single()
        assertNull(a.clientUid)
        assertEquals("Pedro", a.clientName)
        assertEquals("5512345678", a.clientPhone)
    }

    @Test fun `N1 sin nombre no se agenda`() {
        val vm = vm(StaffRole.BARBER, barberId = "b1")
        vm.onIntent(WalkInIntent.SelectService("s1"))
        vm.onIntent(WalkInIntent.SelectSlot(600))
        assertEquals(false, vm.state.value.canConfirm)
        vm.onIntent(WalkInIntent.Confirm)
        assertTrue(bookings.created.isEmpty())
        vm.onIntent(WalkInIntent.NameChanged("  "))
        vm.onIntent(WalkInIntent.Confirm)
        assertTrue(bookings.created.isEmpty())
    }

    @Test fun `N1 si la hora se ocupo, avisa y recarga`() {
        val vm = vm(StaffRole.BARBER, barberId = "b1")
        vm.onIntent(WalkInIntent.NameChanged("Pedro"))
        vm.onIntent(WalkInIntent.SelectService("s1"))
        vm.onIntent(WalkInIntent.SelectSlot(600))
        kotlinx.coroutines.runBlocking {
            BookWalkInUseCase(catalog, bookings, policy, clock) { "otra" }(
                com.barberia.feature.booking.domain.model.WalkInRequest("t1", zone, "Otro", null, "b1", "s1", monday, 600)
            )
        }
        vm.onIntent(WalkInIntent.Confirm)
        assertEquals(BookingFailure.SlotTaken, vm.state.value.failure)
        assertTrue(600 !in vm.state.value.slots)
    }
}
