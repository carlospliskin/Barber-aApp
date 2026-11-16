package com.barberia.feature.booking.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.RetrySessionUseCase
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.usecase.CancelByStaffUseCase
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.MarkNoShowUseCase
import com.barberia.feature.booking.domain.usecase.ObserveAgendaUseCase
import com.barberia.feature.booking.presentation.agenda.AgendaIntent
import com.barberia.feature.booking.presentation.agenda.AgendaViewModel
import com.barberia.feature.booking.presentation.invite.RedeemInviteIntent
import com.barberia.feature.booking.presentation.invite.RedeemInviteViewModel
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.StaffInvite
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.testing.FakeInviteRepository
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.barberia.feature.catalog.domain.usecase.RedeemBarberInviteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Mexico_City")
    private val clock = Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneId.of("UTC")) // lunes 11:00 CDMX
    private val monday = LocalDate.of(2026, 11, 16)
    private val bookings = FakeBookingRepository().apply { barbershops["t1"] = Barbershop("t1", "Gold", zone, null) }
    private val catalog = FakeCatalogRepository(Catalog(emptyList(), listOf(Barber("b1", "Luis"), Barber("b2", "Pepe")), 1))

    private fun appt(id: String, barber: String, startMin: Int, startsAt: String) = Appointment(
        id, "t1", "cli", "Ana", barber, barber, "s1", "Corte", Money(15_000), monday, startMin, startMin + 30,
        AppointmentStatus.BOOKED, Instant.parse(startsAt),
    )

    init {
        runBlocking {
            bookings.book(appt("a1", "b1", 600, "2026-11-16T16:00:00Z")) // 10:00, ya pasó
            bookings.book(appt("a2", "b2", 720, "2026-11-16T18:00:00Z")) // 12:00, todavía no
        }
    }

    private fun vm(role: StaffRole, barberId: String? = null) = AgendaViewModel(
        ObserveCurrentUserUseCase(
            FakeSessionRepository(SessionState.SignedIn(user(role).copy(staffTenantId = "t1", staffBarberId = barberId))),
        ),
        GetBarbershopUseCase(bookings),
        ObserveCatalogUseCase(catalog),
        ObserveAgendaUseCase(bookings),
        MarkCompletedUseCase(bookings, clock),
        MarkNoShowUseCase(bookings, clock),
        CancelByStaffUseCase(bookings, clock),
        clock,
    )

    @Test fun `A2 el duenio ve a todos y filtra por barbero`() {
        val vm = vm(StaffRole.OWNER)
        assertEquals(monday, vm.state.value.date)
        assertEquals(listOf("a1", "a2"), vm.state.value.appointments.map { it.id })
        assertTrue(vm.state.value.canFilter)
        vm.onIntent(AgendaIntent.FilterBarber("b2"))
        assertEquals(listOf("a2"), vm.state.value.appointments.map { it.id })
    }

    @Test fun `A2 el barbero solo ve lo suyo y no puede filtrar`() {
        val vm = vm(StaffRole.BARBER, barberId = "b1")
        assertEquals(listOf("a1"), vm.state.value.appointments.map { it.id })
        vm.onIntent(AgendaIntent.FilterBarber("b2"))
        assertEquals(listOf("a1"), vm.state.value.appointments.map { it.id })
    }

    @Test fun `A4 completar una cita que ya empezo y no una futura`() {
        val vm = vm(StaffRole.OWNER)
        vm.onIntent(AgendaIntent.Complete("a1"))
        assertEquals(AppointmentStatus.COMPLETED, vm.state.value.appointments.first { it.id == "a1" }.status)
        vm.onIntent(AgendaIntent.Complete("a2"))
        assertEquals(BookingFailure.TooEarly, vm.state.value.failure)
    }

    @Test fun `U7 solo se ofrecen atendida y no-show cuando ya se pueden usar`() {
        val vm = vm(StaffRole.OWNER) // 11:00: a1 empezó 10:00, a2 empieza 12:00
        val a1 = vm.state.value.appointments.first { it.id == "a1" }
        val a2 = vm.state.value.appointments.first { it.id == "a2" }
        assertTrue(vm.state.value.canComplete(a1))
        assertTrue(vm.state.value.canNoShow(a1)) // ya pasaron los 10 min de tolerancia
        assertEquals(false, vm.state.value.canComplete(a2))
        assertEquals(false, vm.state.value.canNoShow(a2))
    }

    @Test fun `F7-3 al tercer no-show avisa cuantas citas futuras se cancelaron`() {
        runBlocking {
            bookings.clients["cli"] = com.barberia.feature.booking.domain.model.ClientRecord("cli", "Ana", 0, 2, 0, null)
            bookings.book(appt("f1", "b2", 600, "2026-11-20T16:00:00Z").copy(date = monday.plusDays(4)))
        }
        val vm = vm(StaffRole.OWNER)
        vm.onIntent(AgendaIntent.NoShow("a1"))
        // a2 (hoy 12:00, con b2) y f1 (día 20): ambas futuras del mismo cliente, con cualquier barbero
        assertEquals(2, vm.state.value.penaltyNotice?.cancelledFuture)
        assertEquals(AppointmentStatus.CANCELLED, vm.state.value.appointments.first { it.id == "a2" }.status)
    }

    @Test fun `X2 si alguna cita futura no se pudo cancelar se avisa`() {
        val outcome = com.barberia.feature.booking.domain.usecase.NoShowOutcome(penalized = true, cancelledFuture = 1, failedToCancel = 2)
        val state = com.barberia.feature.booking.presentation.agenda.AgendaState(penaltyNotice = outcome)
        assertTrue(state.hasPendingCancellations)
    }

    @Test fun `A7 resumen y cambiar de dia`() {
        val vm = vm(StaffRole.OWNER)
        assertEquals(2, vm.state.value.summary.total)
        assertEquals(Money(30_000), vm.state.value.summary.expected)
        vm.onIntent(AgendaIntent.NextDay)
        assertEquals(monday.plusDays(1), vm.state.value.date)
        assertTrue(vm.state.value.appointments.isEmpty())
    }

    // ── Aceptar invitación (A1/A9) ────────────────────────────────────────────
    private val invites = FakeInviteRepository().apply {
        stored["MVK234"] = StaffInvite("MVK234", "t1", "b1", "Luis", Instant.parse("2026-11-20T00:00:00Z"), null)
    }

    @Test fun `A9 aceptar invitacion refresca la sesion para que aparezca el modo barbero`() {
        val session = FakeSessionRepository(SessionState.SignedIn(user()))
        val vm = RedeemInviteViewModel(ObserveCurrentUserUseCase(session), RedeemBarberInviteUseCase(invites, FakeCatalogRepository(Catalog(emptyList(), listOf(Barber("b1", "Luis")), 1)), clock), RetrySessionUseCase(session))
        vm.onIntent(RedeemInviteIntent.CodeChanged("mvk234"))
        vm.onIntent(RedeemInviteIntent.Redeem)
        assertEquals("Luis", vm.state.value.joinedAs)
        assertEquals(listOf("refresh"), session.calls)
    }

    @Test fun `A8 invitacion invalida se muestra`() {
        val session = FakeSessionRepository(SessionState.SignedIn(user()))
        val vm = RedeemInviteViewModel(ObserveCurrentUserUseCase(session), RedeemBarberInviteUseCase(invites, FakeCatalogRepository(Catalog(emptyList(), listOf(Barber("b1", "Luis")), 1)), clock), RetrySessionUseCase(session))
        vm.onIntent(RedeemInviteIntent.CodeChanged("ZZZ999"))
        vm.onIntent(RedeemInviteIntent.Redeem)
        assertEquals(CatalogFailure.InviteInvalid, vm.state.value.failure)
        assertNull(vm.state.value.joinedAs)
    }
}
