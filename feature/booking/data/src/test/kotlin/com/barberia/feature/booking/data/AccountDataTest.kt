package com.barberia.feature.booking.data

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.booking.data.gateway.AccountDataGateway
import com.barberia.feature.booking.data.mapper.AppointmentMapper
import com.barberia.feature.booking.data.repository.FirestoreAccountDataRepository
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.catalog.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A7: borrar y anonimizar en lotes que respeten el límite de 500 escrituras por batch de Firestore. */
class AccountDataTest {

    private class FakeGateway : AccountDataGateway {
        /** ruta → datos */
        val docs = sortedMapOf<String, Map<String, Any?>>()
        val batches = mutableListOf<List<String>>()
        var error: GatewayError? = null

        private fun under(prefix: String) = docs.filterKeys {
            it.startsWith(prefix) && it.removePrefix(prefix).count { c -> c == '/' } == 0
        }

        override suspend fun appointmentsOfClient(uid: String, limit: Int) = docs
            .filter { (path, m) -> path.contains("/appointments/") && m["clientUid"] == uid }
            .entries.take(limit)
            .map { (path, m) -> Triple(path.split('/')[1], path.substringAfterLast('/'), m) }
            .also { error?.let { throw it } }

        override suspend fun anonymize(tenantId: String, ids: List<String>) {
            batches += ids
            ids.forEach { id ->
                docs["tenants/$tenantId/appointments/$id"] =
                    docs.getValue("tenants/$tenantId/appointments/$id") + mapOf("clientUid" to null, "clientName" to "")
            }
        }

        override suspend fun tenantAppointments(tenantId: String, limit: Int) =
            under("tenants/$tenantId/appointments/").entries.take(limit).map { (p, m) -> p.substringAfterLast('/') to m }

        override suspend fun clientIds(tenantId: String, limit: Int) =
            under("tenants/$tenantId/clients/").keys.take(limit).map { it.substringAfterLast('/') }

        override suspend fun inviteCodes(tenantId: String) =
            under("staffInvites/").filterValues { it["tenantId"] == tenantId }.keys.map { it.substringAfterLast('/') }

        override suspend fun revokedIds(tenantId: String) = under("tenants/$tenantId/revoked/").keys.map { it.substringAfterLast('/') }

        override suspend fun shareCodeOf(tenantId: String) = docs["tenants/$tenantId"]?.get("shareCode") as String?

        override suspend fun deleteAll(paths: List<String>) {
            assertTrue("máx. 500 por batch", paths.size <= 500)
            batches += paths
            paths.forEach { docs.remove(it) }
        }
    }

    private val gw = FakeGateway()
    private val repo = FirestoreAccountDataRepository(gw)

    private fun cita(id: String, tenant: String = "t1", uid: String? = "cli", day: Int = 20) = Appointment(
        id, tenant, uid, "Ana", "b1", "Luis", "s1", "Corte", Money(100), LocalDate.of(2026, 11, day),
        600, 615, AppointmentStatus.COMPLETED, Instant.parse("2026-11-${day}T16:00:00Z")
    )

    private fun put(a: Appointment) {
        gw.docs["tenants/${a.tenantId}/appointments/${a.id}"] = AppointmentMapper.toMap(a)
    }

    @Test fun `encuentra las citas del cliente en todas las barberias`() = runTest {
        put(cita("a1", "t1"))
        put(cita("a2", "t2"))
        put(cita("x", "t1", uid = "otro"))
        val r = repo.myAppointments("cli", 10) as AppResult.Success
        assertEquals(setOf("t1" to "a1", "t2" to "a2"), r.value.map { it.tenantId to it.id }.toSet())
    }

    @Test fun `anonimiza por barberia`() = runTest {
        put(cita("a1", "t1"))
        put(cita("a2", "t2"))
        val citas = (repo.myAppointments("cli", 10) as AppResult.Success).value
        assertEquals(AppResult.Success(Unit), repo.anonymize(citas))
        assertEquals(AppResult.Success(emptyList<Appointment>()), repo.myAppointments("cli", 10))
        assertEquals("", gw.docs["tenants/t1/appointments/a1"]!!["clientName"])
    }

    @Test fun `borra las fichas del cliente`() = runTest {
        gw.docs["tenants/t1/clients/cli"] = mapOf("name" to "Ana")
        gw.docs["tenants/t2/clients/cli"] = mapOf("name" to "Ana")
        repo.deleteClientRecords("cli", setOf("t1", "t2"))
        assertTrue(gw.docs.isEmpty())
    }

    @Test fun `el duenio borra toda su barberia y nada de otras`() = runTest {
        repeat(450) { put(cita("a$it", "t1", day = 10 + it % 15)) }
        put(cita("otra", "t2"))
        gw.docs["tenants/t1/days/b1_2026-11-20"] = mapOf("booked" to emptyMap<String, String>())
        repeat(3) { gw.docs["tenants/t1/clients/c$it"] = mapOf("name" to "C") }
        gw.docs["tenants/t1/catalog/main"] = mapOf("version" to 1)
        gw.docs["tenants/t1"] = mapOf("name" to "Gold", "shareCode" to "ABC234")
        gw.docs["shareCodes/ABC234"] = mapOf("tenantId" to "t1")
        gw.docs["staffInvites/INV234"] = mapOf("tenantId" to "t1")
        gw.docs["tenants/t1/revoked/luis"] = mapOf("barberId" to "b1") // A16
        gw.docs["staffInvites/OTR234"] = mapOf("tenantId" to "t2")

        assertEquals(AppResult.Success(Unit), repo.deleteBarbershop("t1"))

        assertEquals(setOf("tenants/t2/appointments/otra", "staffInvites/OTR234"), gw.docs.keys)
        assertEquals("tenants/t1", gw.batches.last().last()) // la barbería al final: si se corta, se reintenta
    }

    @Test fun `A7-d el limite diario de Spark se distingue (se reintenta mañana y sigue donde quedó)`() = runTest {
        gw.error = GatewayError.TooManyRequests()
        assertEquals(AppResult.Failure(BookingFailure.DailyLimit), repo.myAppointments("cli", 10))
    }

    @Test fun `sin red lo dice`() = runTest {
        gw.error = GatewayError.Network()
        assertEquals(AppResult.Failure(BookingFailure.NoConnection), repo.myAppointments("cli", 10))
    }
}
