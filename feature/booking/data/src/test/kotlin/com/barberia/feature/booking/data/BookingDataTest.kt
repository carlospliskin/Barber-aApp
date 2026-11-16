package com.barberia.feature.booking.data

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.booking.data.gateway.BookingGateway
import com.barberia.feature.booking.data.gateway.SlotTakenException
import com.barberia.feature.booking.data.mapper.AppointmentMapper
import com.barberia.feature.booking.data.repository.FirestoreBookingRepository
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.catalog.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BookingDataTest {
    private val cita = Appointment(
        id = "a1", tenantId = "t", clientUid = "cli", clientName = "Ana", barberId = "b1", barberName = "Luis",
        serviceId = "s1", serviceName = "Corte", price = Money(15_000), date = LocalDate.of(2026, 11, 20),
        startMinute = 600, endMinute = 615, status = AppointmentStatus.BOOKED,
        startsAt = Instant.parse("2026-11-20T16:00:00Z"),
    )

    // ── Mapper ────────────────────────────────────────────────────────────────
    @Test fun `ida y vuelta`() =
        assertEquals(cita, AppointmentMapper.fromMap("a1", "t", AppointmentMapper.toMap(cita)))

    @Test fun `el documento lleva los bloques y el dia que exigen las reglas`() {
        val m = AppointmentMapper.toMap(cita)
        assertEquals(listOf("600", "605", "610"), m["slotKeys"])
        assertEquals("b1_2026-11-20", m["dayId"])
        assertEquals("2026-11-20", m["date"])
    }

    @Test fun `documento corrupto se descarta`() =
        assertEquals(null, AppointmentMapper.fromMap("x", "t", mapOf("status" to "BOOKED")))

    // ── Repositorio ───────────────────────────────────────────────────────────
    private class FakeGateway : BookingGateway {
        var day: Map<String, Any?>? = null
        val active = mutableListOf<Map<String, Any?>>()
        var bookError: Exception? = null
        val live = MutableStateFlow<List<Pair<String, Map<String, Any?>>>>(emptyList())
        var lastCancel: Set<String>? = null
        override suspend fun getDay(tenantId: String, dayId: String) = day
        override suspend fun activeAppointments(tenantId: String, clientUid: String) = active
        override suspend fun book(tenantId: String, apptId: String, appointment: Map<String, Any?>, dayId: String, keys: Set<String>) {
            bookError?.let { throw it }
        }
        override suspend fun cancel(tenantId: String, apptId: String, dayId: String, keys: Set<String>) {
            lastCancel = keys
        }
        var lastFrom: String? = null
        var tenant: Map<String, Any?>? = null
        val codes = mutableMapOf("ABC234" to "t")
        override fun observeUpcoming(tenantId: String, clientUid: String, fromDate: String): kotlinx.coroutines.flow.Flow<List<Pair<String, Map<String, Any?>>>> {
            lastFrom = fromDate
            return live
        }
        override suspend fun shareCode(code: String): String? = codes[code]
        override suspend fun tenant(tenantId: String) = tenant
        override suspend fun claimShareCode(tenantId: String, code: String): Boolean {
            if (code in codes) return false
            codes[code] = tenantId
            return true
        }
    }

    private val gw = FakeGateway()
    private val repo = FirestoreBookingRepository(gw)

    @Test fun `bloques ocupados del dia como minutos`() = runTest {
        gw.day = mapOf("booked" to mapOf("600" to "a1", "605" to "a1", "basura" to "x"))
        assertEquals(AppResult.Success(setOf(600, 605)), repo.dayBookings("t", "b1", cita.date))
    }

    @Test fun `dia sin documento esta libre`() = runTest {
        assertEquals(AppResult.Success(emptySet<Int>()), repo.dayBookings("t", "b1", cita.date))
    }

    @Test fun `citas activas desde hoy (las viejas no cuentan)`() = runTest {
        gw.active += AppointmentMapper.toMap(cita)
        gw.active += AppointmentMapper.toMap(cita.copy(date = LocalDate.of(2026, 11, 1)))
        assertEquals(AppResult.Success(1), repo.countActive("t", "cli", LocalDate.of(2026, 11, 16)))
    }

    @Test fun `B3 si el gateway detecta choque es SlotTaken`() = runTest {
        gw.bookError = SlotTakenException()
        assertEquals(AppResult.Failure(BookingFailure.SlotTaken), repo.book(cita))
    }

    @Test fun `B3 si las reglas rechazan la escritura tambien es SlotTaken`() = runTest {
        gw.bookError = GatewayError.PermissionDenied()
        assertEquals(AppResult.Failure(BookingFailure.SlotTaken), repo.book(cita))
    }

    @Test fun `sin red`() = runTest {
        gw.bookError = GatewayError.Network()
        assertEquals(AppResult.Failure(BookingFailure.NoConnection), repo.book(cita))
    }

    @Test fun `cancelar libera exactamente los bloques de la cita`() = runTest {
        repo.cancel(cita)
        assertEquals(setOf("600", "605", "610"), gw.lastCancel)
    }

    @Test fun `R4 mis citas desde una fecha, ordenadas`() = runTest {
        val later = cita.copy(id = "a2", date = LocalDate.of(2026, 11, 25))
        gw.live.value = listOf("a2" to AppointmentMapper.toMap(later), "a1" to AppointmentMapper.toMap(cita))
        assertEquals(listOf("a1", "a2"), repo.observeUpcoming("t", "cli", LocalDate.of(2026, 11, 16)).first().map { it.id })
        assertEquals("2026-11-16", gw.lastFrom)
    }

    @Test fun `U4 las canceladas no salen en mis citas`() = runTest {
        gw.live.value = listOf("a1" to AppointmentMapper.toMap(cita.copy(status = AppointmentStatus.CANCELLED)))
        assertEquals(emptyList<String>(), repo.observeUpcoming("t", "cli", LocalDate.of(2026, 11, 16)).first().map { it.id })
    }

    @Test fun `R6 datos de la barberia, con zona por defecto si viene mal`() = runTest {
        gw.tenant = mapOf("name" to "Gold", "timezone" to "Marte/Olimpo", "shareCode" to "ABC234")
        assertEquals(
            AppResult.Success(com.barberia.feature.booking.domain.model.Barbershop("t", "Gold", java.time.ZoneId.of("America/Mexico_City"), "ABC234")),
            repo.barbershop("t"),
        )
        gw.tenant = null
        assertEquals(AppResult.Success(null), repo.barbershop("t"))
    }

    @Test fun `R7 un codigo ocupado no se puede tomar`() = runTest {
        assertEquals(AppResult.Success(false), repo.claimShareCode("t2", "ABC234"))
        assertEquals(AppResult.Success(true), repo.claimShareCode("t2", "XYZ789"))
    }

    @Test fun `B11 SlotTaken envuelto por Firebase se recupera`() = runTest {
        val wrapped = GatewayError.Other(RuntimeException("tx falló", SlotTakenException()))
        val error = runCatching {
            com.barberia.feature.booking.data.firebase.unwrapSlotTaken<Unit> { throw wrapped }
        }.exceptionOrNull()
        assertEquals(SlotTakenException::class, error!!::class)
        val other = runCatching {
            com.barberia.feature.booking.data.firebase.unwrapSlotTaken<Unit> { throw GatewayError.Network() }
        }.exceptionOrNull()
        assertEquals(GatewayError.Network::class, other!!::class)
    }

    @Test fun `codigo para compartir`() = runTest {
        assertEquals(AppResult.Success("t"), repo.resolveShareCode("ABC234"))
        assertEquals(AppResult.Success(null), repo.resolveShareCode("ZZZ999"))
    }
}
