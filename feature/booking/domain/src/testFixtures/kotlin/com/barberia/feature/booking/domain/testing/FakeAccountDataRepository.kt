package com.barberia.feature.booking.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.repository.AccountDataRepository

/** Lee las citas de [bookings] (así ve las cancelaciones) y recuerda qué se anonimizó y borró. */
class FakeAccountDataRepository(private val bookings: FakeBookingRepository) : AccountDataRepository {
    val anonymized = mutableListOf<String>()
    val deletedClientRecords = mutableListOf<Pair<String, String>>()
    val deletedShops = mutableListOf<String>()
    val calls = mutableListOf<String>()
    var failure: BookingFailure? = null
    var pagesRequested = 0

    override suspend fun myAppointments(uid: String, limit: Int): BookingResult<List<Appointment>> {
        pagesRequested++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(bookings.created.filter { it.clientUid == uid && it.id !in anonymized }.take(limit))
    }

    override suspend fun anonymize(appointments: List<Appointment>): BookingResult<Unit> {
        calls += "anonymize"
        anonymized += appointments.map { it.id }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteClientRecords(uid: String, tenantIds: Set<String>): BookingResult<Unit> {
        calls += "deleteClientRecords"
        deletedClientRecords += tenantIds.map { it to uid }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteBarbershop(tenantId: String): BookingResult<Unit> {
        calls += "deleteBarbershop"
        failure?.let { return AppResult.Failure(it) }
        deletedShops += tenantId
        return AppResult.Success(Unit)
    }
}
