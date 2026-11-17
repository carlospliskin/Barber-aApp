package com.barberia.feature.booking.data.repository

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.booking.data.gateway.AccountDataGateway
import com.barberia.feature.booking.data.mapper.AppointmentMapper
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.repository.AccountDataRepository
import kotlinx.coroutines.CancellationException

/**
 * A7. Spark: 20 000 borrados y 50 000 lecturas al día; una barbería chica cabe de sobra.
 * Todo es idempotente: si se corta, se vuelve a llamar y sigue donde quedó.
 */
internal class FirestoreAccountDataRepository(private val gateway: AccountDataGateway) : AccountDataRepository {

    override suspend fun myAppointments(uid: String, limit: Int) = attempt {
        gateway.appointmentsOfClient(uid, limit).mapNotNull { (tenant, id, m) -> AppointmentMapper.fromMap(id, tenant, m) }
    }

    override suspend fun anonymize(appointments: List<Appointment>) = attempt {
        appointments.groupBy { it.tenantId }.forEach { (tenant, list) ->
            list.map { it.id }.chunked(BATCH).forEach { gateway.anonymize(tenant, it) }
        }
    }

    override suspend fun deleteClientRecords(uid: String, tenantIds: Set<String>) = attempt {
        tenantIds.map { "tenants/$it/clients/$uid" }.chunked(BATCH).forEach { gateway.deleteAll(it) }
    }

    override suspend fun deleteBarbershop(tenantId: String) = attempt {
        val root = "tenants/$tenantId"
        // Citas y sus días: 200 citas + hasta 200 días = máx. 400 borrados por batch.
        while (true) {
            val page = gateway.tenantAppointments(tenantId, PAGE)
            if (page.isEmpty()) break
            val days = page.mapNotNull { (_, m) -> m["dayId"] as? String }.distinct().map { "$root/days/$it" }
            gateway.deleteAll(page.map { (id, _) -> "$root/appointments/$id" } + days)
        }
        while (true) {
            val ids = gateway.clientIds(tenantId, BATCH)
            if (ids.isEmpty()) break
            gateway.deleteAll(ids.map { "$root/clients/$it" })
        }
        gateway.inviteCodes(tenantId).map { "staffInvites/$it" }.chunked(BATCH).forEach { gateway.deleteAll(it) }
        gateway.revokedIds(tenantId).map { "$root/revoked/$it" }.chunked(BATCH).forEach { gateway.deleteAll(it) }
        val code = gateway.shareCodeOf(tenantId)
        // La barbería va al final: mientras exista, el reintento sabe qué falta (y su código).
        gateway.deleteAll(listOfNotNull("$root/catalog/main", code?.let { "shareCodes/$it" }, root))
    }

    private suspend fun <T> attempt(block: suspend () -> T): BookingResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: GatewayError) {
        AppResult.Failure(
            when (e) {
                is GatewayError.Network -> BookingFailure.NoConnection

                is GatewayError.TooManyRequests -> BookingFailure.DailyLimit

                // A7-d: RESOURCE_EXHAUSTED
                else -> BookingFailure.Unknown(e.cause?.message)
            }
        )
    }

    private companion object {
        const val PAGE = 200
        const val BATCH = 400
    }
}
