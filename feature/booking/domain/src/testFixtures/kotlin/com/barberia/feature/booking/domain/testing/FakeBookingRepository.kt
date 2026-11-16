package com.barberia.feature.booking.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.dayId
import com.barberia.feature.booking.domain.repository.BookingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Agenda en memoria con la misma garantía que Firestore: un bloque ocupado no se sobrescribe. */
class FakeBookingRepository : BookingRepository {
    private val days = mutableMapOf<String, MutableMap<Int, String>>()
    private val all = MutableStateFlow<List<Appointment>>(emptyList())
    val created: List<Appointment> get() = all.value
    val shareCodes = mutableMapOf<String, String>()
    var failure: BookingFailure? = null

    fun day(barberId: String, date: LocalDate): Map<Int, String> = days[dayId(barberId, date)].orEmpty()

    override suspend fun dayBookings(tenantId: String, barberId: String, date: LocalDate): BookingResult<Set<Int>> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(day(barberId, date).keys)

    var activeOverride: Int? = null

    override suspend fun countActive(tenantId: String, clientUid: String, from: LocalDate): BookingResult<Int> =
        activeOverride?.let { AppResult.Success(it) } ?: AppResult.Success(all.value.count { it.clientUid == clientUid && it.status == AppointmentStatus.BOOKED && !it.date.isBefore(from) })

    override suspend fun book(appointment: Appointment): BookingResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        val day = days.getOrPut(dayId(appointment.barberId, appointment.date)) { mutableMapOf() }
        if (appointment.slots.any { it in day }) return AppResult.Failure(BookingFailure.SlotTaken)
        appointment.slots.forEach { day[it] = appointment.id }
        all.value = all.value + appointment
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(appointment: Appointment): BookingResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        days[dayId(appointment.barberId, appointment.date)]?.keys?.removeAll(appointment.slots)
        all.value = all.value.map { if (it.id == appointment.id) it.copy(status = AppointmentStatus.CANCELLED) else it }
        return AppResult.Success(Unit)
    }

    val barbershops = mutableMapOf<String, Barbershop>()
    var claims = 0
    var lastUpcomingFrom: LocalDate? = null

    override fun observeUpcoming(tenantId: String, clientUid: String, from: LocalDate): Flow<List<Appointment>> {
        lastUpcomingFrom = from
        return all.map { list -> list.filter { it.clientUid == clientUid && it.status == AppointmentStatus.BOOKED && !it.date.isBefore(from) } }
    }

    override suspend fun resolveShareCode(code: String): BookingResult<String?> = AppResult.Success(shareCodes[code])

    override suspend fun barbershop(tenantId: String): BookingResult<Barbershop?> = AppResult.Success(barbershops[tenantId])

    override suspend fun claimShareCode(tenantId: String, code: String): BookingResult<Boolean> {
        claims++
        if (code in shareCodes) return AppResult.Success(false)
        shareCodes[code] = tenantId
        barbershops[tenantId]?.let { barbershops[tenantId] = it.copy(shareCode = code) }
        return AppResult.Success(true)
    }
}
