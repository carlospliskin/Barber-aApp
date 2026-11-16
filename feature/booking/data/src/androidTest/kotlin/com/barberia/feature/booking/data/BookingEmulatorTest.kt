package com.barberia.feature.booking.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.barberia.core.common.AppResult
import com.barberia.core.firebase.testing.FirebaseEmulator
import com.barberia.core.firebase.testing.FirebaseEmulator.read
import com.barberia.core.firebase.testing.FirebaseEmulator.seed
import com.barberia.feature.booking.data.firebase.FirestoreAccountDataGateway
import com.barberia.feature.booking.data.firebase.FirestoreBookingGateway
import com.barberia.feature.booking.data.repository.FirestoreAccountDataRepository
import com.barberia.feature.booking.data.repository.FirestoreBookingRepository
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingException
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.UndoMarkUseCase
import com.barberia.feature.catalog.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/**
 * Q1: repositorio + gateway reales de Firestore contra el emulador, con las reglas reales.
 * Prueba lo que los tests JVM no ven: transacciones, batches, conversión de fechas, consultas de grupo.
 */
@RunWith(AndroidJUnit4::class)
class BookingEmulatorTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val repo = FirestoreBookingRepository(FirestoreBookingGateway(FirebaseEmulator.db))
    private val accountData = FirestoreAccountDataRepository(FirestoreAccountDataGateway(FirebaseEmulator.db))
    private val clock = Clock.systemUTC()

    @Before fun reset() = FirebaseEmulator.reset()

    /** Cita que respeta las reglas: fecha/minuto locales coherentes con `startsAt`, bloques de 5 min. */
    private fun appt(id: String, tenant: String, uid: String?, date: LocalDate, startMinute: Int, name: String = "Ana") = Appointment(
        id, tenant, uid, name, "b1", "Luis", "s1", "Corte", Money(15_000), date, startMinute, startMinute + 30,
        AppointmentStatus.BOOKED, date.atStartOfDay(zone).plusMinutes(startMinute.toLong()).toInstant(),
    )

    private fun tomorrow() = LocalDate.now(zone).plusDays(1)

    @Test fun reservar_ocupa_el_horario_y_otro_cliente_no_puede_tomarlo() = runTest(timeout = 60.seconds) {
        val ana = FirebaseEmulator.signUp("ana@test.com")
        assertEquals(AppResult.Success(Unit), repo.book(appt("a1", "t1", ana, tomorrow(), 600)))
        assertEquals(AppResult.Success((600 until 630 step 5).toSet()), repo.dayBookings("t1", "b1", tomorrow()))
        assertEquals(0L, read("tenants/t1/clients/$ana")!!["completed"]) // F7-6: ficha en ceros en la misma transacción

        val beto = FirebaseEmulator.signUp("beto@test.com")
        val r = repo.book(appt("a2", "t1", beto, tomorrow(), 615))
        assertEquals(AppResult.Failure(BookingFailure.SlotTaken), r)
        assertNull(read("tenants/t1/appointments/a2"))
    }

    @Test fun cancelar_libera_el_horario() = runTest(timeout = 60.seconds) {
        val ana = FirebaseEmulator.signUp("ana@test.com")
        val a = appt("a1", "t1", ana, tomorrow(), 600)
        repo.book(a)
        assertEquals(AppResult.Success(Unit), repo.cancel(a))
        assertEquals(AppResult.Success(emptySet<Int>()), repo.dayBookings("t1", "b1", tomorrow()))
        assertEquals("CANCELLED", read("tenants/t1/appointments/a1")!!["status"])
    }

    @Test fun marcar_atendida_y_deshacer_corrigen_la_ficha_en_la_misma_transaccion() = runTest(timeout = 60.seconds) {
        val owner = FirebaseEmulator.signUp("dueno@test.com") // tenants/{uid del dueño}
        val now = Instant.now()
        val local = now.minusSeconds(600).atZone(zone)
        val start = local.hour * 60 + local.minute - (local.minute % 5)
        val date = local.toLocalDate()
        val past = appt("a1", owner, "cli", date, start)
        seed("tenants/$owner/appointments/a1", mapOf(
            "clientUid" to "cli", "clientName" to "Ana", "barberId" to "b1", "barberName" to "Luis", "serviceId" to "s1",
            "serviceName" to "Corte", "priceCents" to 15_000, "date" to date.toString(), "startMinute" to start,
            "endMinute" to start + 30, "startsAt" to past.startsAt, "status" to "BOOKED",
            "slotKeys" to past.slots.sorted().map { it.toString() }, "dayId" to "b1_$date", "createdAt" to now,
        ))
        seed("tenants/$owner/clients/cli", mapOf(
            "name" to "Ana", "completed" to 4, "noShows" to 0, "rewardsUsed" to 0, "penaltyUntil" to null,
            "lastAppointmentId" to "a1", "updatedAt" to now,
        ))

        assertEquals(AppResult.Success(Unit), MarkCompletedUseCase(repo, clock)(past))
        assertEquals(5L, read("tenants/$owner/clients/cli")!!["completed"])

        // F7-2: la hora de marcar la pone el servidor y vuelve como markedAt
        val marked = (repo.appointment(owner, "a1") as AppResult.Success).value!!
        assertTrue(marked.markedAt != null && marked.canUndo(Instant.now()))
        assertEquals(AppResult.Success(Unit), UndoMarkUseCase(repo, LoyaltyPolicy(), clock)(marked))
        assertEquals("BOOKED", read("tenants/$owner/appointments/a1")!!["status"])
        assertEquals(4L, read("tenants/$owner/clients/cli")!!["completed"])
    }

    @Test fun sin_acceso_la_agenda_falla_con_NoAccess_no_con_sin_conexion() = runTest(timeout = 60.seconds) {
        FirebaseEmulator.signUp("cliente@test.com")
        val e = runCatching { repo.observeDay("t1", tomorrow(), "b1").first() }.exceptionOrNull()
        assertEquals(BookingFailure.NoAccess, (e as BookingException).failure)
    }

    @Test fun A7_encuentra_mis_citas_en_dos_barberias_anonimiza_y_borra_fichas() = runTest(timeout = 60.seconds) {
        val ana = FirebaseEmulator.signUp("ana@test.com")
        repo.book(appt("a1", "t1", ana, tomorrow(), 600))
        repo.book(appt("a2", "t2", ana, tomorrow(), 600))
        repo.cancel(appt("a1", "t1", ana, tomorrow(), 600))
        repo.cancel(appt("a2", "t2", ana, tomorrow(), 600))

        val mine = (accountData.myAppointments(ana, 100) as AppResult.Success).value
        assertEquals(setOf("t1" to "a1", "t2" to "a2"), mine.map { it.tenantId to it.id }.toSet())
        assertEquals(AppResult.Success(Unit), accountData.deleteClientRecords(ana, setOf("t1", "t2")))
        assertEquals(AppResult.Success(Unit), accountData.anonymize(mine))

        assertEquals(AppResult.Success(emptyList<Appointment>()), accountData.myAppointments(ana, 100))
        assertEquals("", read("tenants/t1/appointments/a1")!!["clientName"])
        assertNull(read("tenants/t1/clients/$ana"))
        assertNull(read("tenants/t2/clients/$ana"))
    }

    @Test fun A7_el_duenio_borra_su_barberia_entera_y_nada_ajeno() = runTest(timeout = 60.seconds) {
        val owner = FirebaseEmulator.signUp("dueno@test.com")
        val now = Instant.now()
        seed("tenants/$owner", mapOf("name" to "Gold", "ownerUid" to owner, "timezone" to "America/Mexico_City", "createdAt" to now, "shareCode" to "ABC234"))
        seed("tenants/$owner/catalog/main", mapOf("version" to 1))
        seed("shareCodes/ABC234", mapOf("tenantId" to owner, "createdAt" to now))
        seed("staffInvites/INV234", mapOf("tenantId" to owner, "barberId" to "b1", "usedBy" to "luis"))
        seed("staffInvites/OTR234", mapOf("tenantId" to "otra", "barberId" to "b1", "usedBy" to null))
        seed("tenants/$owner/revoked/luis", mapOf("barberId" to "b1", "createdAt" to now))
        seed("tenants/$owner/clients/cli", mapOf("name" to "Ana"))
        repeat(3) { i ->
            seed("tenants/$owner/appointments/a$i", mapOf("clientUid" to "cli", "dayId" to "b1_2026-12-0${i + 1}", "status" to "COMPLETED"))
            seed("tenants/$owner/days/b1_2026-12-0${i + 1}", mapOf("lastOp" to "a$i"))
        }
        seed("tenants/otra/appointments/x", mapOf("clientUid" to "cli"))

        assertEquals(AppResult.Success(Unit), accountData.deleteBarbershop(owner))

        listOf(
            "tenants/$owner", "tenants/$owner/catalog/main", "shareCodes/ABC234", "staffInvites/INV234",
            "tenants/$owner/revoked/luis", "tenants/$owner/clients/cli", "tenants/$owner/appointments/a0",
            "tenants/$owner/days/b1_2026-12-01",
        ).forEach { assertNull(it, read(it)) }
        assertTrue(read("staffInvites/OTR234") != null && read("tenants/otra/appointments/x") != null)
    }
}
