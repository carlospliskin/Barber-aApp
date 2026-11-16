package com.barberia.feature.booking.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.testing.FakeJoinedStore
import com.barberia.feature.booking.domain.usecase.BookAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.CancelAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.EnsureShareCodeUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.JoinBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.LeaveBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyAppointmentsUseCase
import com.barberia.feature.booking.presentation.book.BookingEffect
import com.barberia.feature.booking.presentation.book.BookingIntent
import com.barberia.feature.booking.presentation.book.BookingViewModel
import com.barberia.feature.booking.presentation.client.ClientHomeIntent
import com.barberia.feature.booking.presentation.client.ClientHomeViewModel
import com.barberia.feature.booking.presentation.owner.ShareCodeViewModel
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BookingPresentationTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Mexico_City")
    private val clock = Clock.fixed(Instant.parse("2026-11-16T14:00:00Z"), ZoneId.of("UTC")) // lunes 08:00 CDMX
    private val monday = LocalDate.of(2026, 11, 16)
    private val policy = BookingPolicy()
    private val gold = Barbershop("t1", "Barber Gold", zone, shareCode = null)

    private val luis = Barber("b1", "Luis", schedule = WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "12:00")))))
    private val corte = Service("s1", "Corte", 30, Money(15_000))
    private val barba = Service("s2", "Barba", 60, Money(9_000))
    private val catalog = FakeCatalogRepository(Catalog(listOf(corte, barba), listOf(luis), 1))
    private val bookings = FakeBookingRepository().apply { barbershops["t1"] = gold; shareCodes["ABC234"] = "t1" }
    private val store = FakeJoinedStore()
    private val session = FakeSessionRepository(SessionState.SignedIn(user()))
    private var n = 0
    private val reminders = com.barberia.feature.booking.domain.testing.FakeReminderScheduler()

    private fun clientHome() = ClientHomeViewModel(
        ObserveCurrentUserUseCase(session),
        ObserveJoinedBarbershopUseCase(bookings, store),
        JoinBarbershopUseCase(bookings, store),
        LeaveBarbershopUseCase(bookings, store),
        ObserveMyAppointmentsUseCase(bookings, clock),
        CancelAppointmentUseCase(bookings, policy, clock),
        clock,
        com.barberia.feature.booking.domain.usecase.SyncRemindersUseCase(reminders, clock),
        com.barberia.feature.booking.domain.usecase.CancelReminderUseCase(reminders),
    )

    private fun booking() = BookingViewModel(
        ObserveCurrentUserUseCase(session),
        ObserveJoinedBarbershopUseCase(bookings, store),
        ObserveCatalogUseCase(catalog),
        GetAvailableSlotsUseCase(catalog, bookings, policy, clock),
        BookAppointmentUseCase(catalog, bookings, policy, clock) { "cita-${++n}" },
        policy,
        clock,
    )

    // ── Home del cliente ──────────────────────────────────────────────────────
    @Test fun `sin barberia pide el codigo y al unirse la muestra`() {
        val vm = clientHome()
        assertNull(vm.state.value.shop)
        vm.onIntent(ClientHomeIntent.CodeChanged("abc234"))
        vm.onIntent(ClientHomeIntent.Join)
        assertEquals("Barber Gold", vm.state.value.shop?.name)
    }

    @Test fun `codigo invalido se muestra sin salir`() {
        val vm = clientHome()
        vm.onIntent(ClientHomeIntent.CodeChanged("ZZZ999"))
        vm.onIntent(ClientHomeIntent.Join)
        assertEquals(BookingFailure.InvalidCode, vm.state.value.failure)
        assertNull(store.saved["uid-1"])
    }

    @Test fun `mis citas y cancelar`() = runTest {
        store.save("uid-1", "t1")
        val vm = clientHome()
        val b = booking()
        b.onIntent(BookingIntent.SelectService("s1"))
        b.onIntent(BookingIntent.SelectBarber("b1"))
        b.onIntent(BookingIntent.SelectDate(monday))
        b.onIntent(BookingIntent.SelectSlot(600))
        b.onIntent(BookingIntent.Confirm)
        assertEquals(1, vm.state.value.appointments.size)

        vm.onIntent(ClientHomeIntent.Cancel("cita-1"))
        assertEquals(AppointmentStatus.CANCELLED, bookings.created.single().status)
    }


    // ── Reservar ──────────────────────────────────────────────────────────────
    @Test fun `solo ofrece servicios y barberos reservables y 14 dias desde hoy en la zona de la barberia`() {
        store.saved["uid-1"] = "t1"
        val vm = booking()
        assertEquals(listOf("s1", "s2"), vm.state.value.services.map { it.id })
        assertEquals(listOf("b1"), vm.state.value.barbers.map { it.id })
        assertEquals(monday, vm.state.value.dates.first())
        assertEquals(14, vm.state.value.dates.size)
    }

    @Test fun `elegir servicio, barbero y dia carga las horas`() {
        store.saved["uid-1"] = "t1"
        val vm = booking()
        vm.onIntent(BookingIntent.SelectService("s1"))
        vm.onIntent(BookingIntent.SelectBarber("b1"))
        vm.onIntent(BookingIntent.SelectDate(monday))
        assertEquals(listOf("10:00", "10:15", "10:30", "10:45", "11:00", "11:15", "11:30"), vm.state.value.slots.map(TimeRange::label))
    }

    @Test fun `B16 cambiar de servicio limpia la hora y recalcula`() {
        store.saved["uid-1"] = "t1"
        val vm = booking()
        vm.onIntent(BookingIntent.SelectService("s1"))
        vm.onIntent(BookingIntent.SelectBarber("b1"))
        vm.onIntent(BookingIntent.SelectDate(monday))
        vm.onIntent(BookingIntent.SelectSlot(TimeRange.parse("11:30")))
        vm.onIntent(BookingIntent.SelectService("s2")) // 60 min: 11:30 ya no cabe
        assertNull(vm.state.value.selectedSlot)
        assertTrue(TimeRange.parse("11:30") !in vm.state.value.slots)
    }

    @Test fun `confirmar reserva y avisa`() = runTest {
        store.saved["uid-1"] = "t1"
        val vm = booking()
        vm.onIntent(BookingIntent.SelectService("s1"))
        vm.onIntent(BookingIntent.SelectBarber("b1"))
        vm.onIntent(BookingIntent.SelectDate(monday))
        vm.onIntent(BookingIntent.SelectSlot(600))
        vm.effects.test {
            vm.onIntent(BookingIntent.Confirm)
            assertEquals(BookingEffect.Booked, awaitItem())
        }
        assertEquals("Ana Pérez", bookings.created.single().clientName)
    }

    @Test fun `B15 si otro tomo la hora, avisa y recarga las horas`() {
        store.saved["uid-1"] = "t1"
        val vm = booking()
        vm.onIntent(BookingIntent.SelectService("s1"))
        vm.onIntent(BookingIntent.SelectBarber("b1"))
        vm.onIntent(BookingIntent.SelectDate(monday))
        vm.onIntent(BookingIntent.SelectSlot(600))
        // Otro cliente gana 10:00 justo antes de confirmar
        kotlinx.coroutines.runBlocking {
            BookAppointmentUseCase(catalog, bookings, policy, clock) { "otra" }(
                com.barberia.feature.booking.domain.model.BookingRequest("t1", zone, "otro", "Beto", "b1", "s1", monday, 600),
            )
        }
        vm.onIntent(BookingIntent.Confirm)
        assertEquals(BookingFailure.SlotTaken, vm.state.value.failure)
        assertNull(vm.state.value.selectedSlot)
        assertTrue(600 !in vm.state.value.slots)
    }

    @Test fun `U2 los dias que el barbero no trabaja no se pueden elegir`() {
        store.saved["uid-1"] = "t1"
        val vm = booking()
        vm.onIntent(BookingIntent.SelectBarber("b1"))
        assertEquals(setOf(DayOfWeek.MONDAY), vm.state.value.workingDays)
        vm.onIntent(BookingIntent.SelectDate(monday.plusDays(1))) // martes: no trabaja
        assertNull(vm.state.value.selectedDate)
    }

    @Test fun `R3 R5 mis citas programa el recordatorio y cancelar lo borra`() = runTest {
        store.save("uid-1", "t1")
        val vm = clientHome()
        val b = booking()
        b.onIntent(BookingIntent.SelectService("s1"))
        b.onIntent(BookingIntent.SelectBarber("b1"))
        b.onIntent(BookingIntent.SelectDate(monday))
        b.onIntent(BookingIntent.SelectSlot(TimeRange.parse("11:30"))) // 11:30 CDMX = 17:30 UTC; ahora 14:00 UTC
        b.onIntent(BookingIntent.Confirm)
        assertEquals(setOf("cita-1"), reminders.scheduled.keys) // aviso a las 14:30 UTC (3 h antes)

        vm.onIntent(ClientHomeIntent.Cancel("cita-1"))
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test fun `U4 mis citas no muestra las canceladas`() = runTest {
        store.save("uid-1", "t1")
        val vm = clientHome()
        val b = booking()
        b.onIntent(BookingIntent.SelectService("s1"))
        b.onIntent(BookingIntent.SelectBarber("b1"))
        b.onIntent(BookingIntent.SelectDate(monday))
        b.onIntent(BookingIntent.SelectSlot(600))
        b.onIntent(BookingIntent.Confirm)
        vm.onIntent(ClientHomeIntent.Cancel("cita-1"))
        assertTrue(vm.state.value.appointments.isEmpty())
    }

    // ── Código del dueño ──────────────────────────────────────────────────────
    @Test fun `X1 sin correo verificado no intenta crear el codigo y lo crea al verificar`() {
        val owner = FakeSessionRepository(
            SessionState.SignedIn(user(StaffRole.OWNER).copy(uid = "t1", staffTenantId = "t1", emailVerified = false)),
        )
        val vm = ShareCodeViewModel(ObserveCurrentUserUseCase(owner), EnsureShareCodeUseCase(bookings, Random(3)))
        assertEquals(0, bookings.claims) // no se gasta una escritura que las reglas rechazarían
        assertNull(vm.state.value.failure)

        owner.sessionFlow.value = SessionState.SignedIn(user(StaffRole.OWNER).copy(uid = "t1", staffTenantId = "t1", emailVerified = true))
        assertEquals(6, vm.state.value.code?.length)
    }

    @Test fun `R7 el duenio ve su codigo para compartir`() {
        val owner = FakeSessionRepository(SessionState.SignedIn(user(StaffRole.OWNER).copy(uid = "t1", staffTenantId = "t1", emailVerified = true)))
        val vm = ShareCodeViewModel(ObserveCurrentUserUseCase(owner), EnsureShareCodeUseCase(bookings, Random(3)))
        assertEquals(bookings.barbershops["t1"]?.shareCode, vm.state.value.code)
        assertEquals(6, vm.state.value.code?.length)
    }
}
