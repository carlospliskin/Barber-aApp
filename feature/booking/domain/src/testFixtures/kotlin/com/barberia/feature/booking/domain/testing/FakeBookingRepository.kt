package com.barberia.feature.booking.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.ClientRecord
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

    override suspend fun appointment(tenantId: String, id: String): BookingResult<Appointment?> =
        AppResult.Success(all.value.find { it.id == id })

    override suspend fun activeAppointments(tenantId: String, clientUid: String): BookingResult<List<Appointment>> =
        AppResult.Success(all.value.filter { it.clientUid == clientUid && it.status == AppointmentStatus.BOOKED })

    override suspend fun countActive(tenantId: String, clientUid: String, from: LocalDate): BookingResult<Int> =
        activeOverride?.let { AppResult.Success(it) } ?: AppResult.Success(all.value.count { it.clientUid == clientUid && it.status == AppointmentStatus.BOOKED && !it.date.isBefore(from) })

    override suspend fun book(appointment: Appointment): BookingResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        val day = days.getOrPut(dayId(appointment.barberId, appointment.date)) { mutableMapOf() }
        if (appointment.slots.any { it in day }) return AppResult.Failure(BookingFailure.SlotTaken)
        appointment.slots.forEach { day[it] = appointment.id }
        all.value = all.value + appointment
        // F7-6: primera reserva crea la ficha en ceros
        appointment.clientUid?.let { uid -> clients.getOrPut(uid) { ClientRecord.new(uid, appointment.clientName) } } // N1: sin cuenta no hay ficha
        publishClients()
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

    var lastAgendaDate: LocalDate? = null
    var lastStatus: AppointmentStatus? = null

    var agendaError: BookingFailure? = null

    override fun observeDay(tenantId: String, date: LocalDate, barberId: String?): Flow<List<Appointment>> {
        lastAgendaDate = date
        agendaError?.let { f -> return kotlinx.coroutines.flow.flow { throw com.barberia.feature.booking.domain.model.BookingException(f) } }
        return all.map { list -> list.filter { it.date == date && (barberId == null || it.barberId == barberId) } }
    }

    val clients = mutableMapOf<String, ClientRecord>()
    private val clientsFlow = MutableStateFlow<Map<String, ClientRecord>>(emptyMap())
    private fun publishClients() { clientsFlow.value = clients.toMap() }

    override suspend fun setStatus(
        appointment: Appointment,
        status: AppointmentStatus,
        updateClient: ((ClientRecord?) -> ClientRecord)?,
    ): BookingResult<Unit> {
        lastStatus = status
        all.value = all.value.map { if (it.id == appointment.id) it.copy(status = status) else it }
        val uid = appointment.clientUid
        if (uid != null && updateClient != null) clients[uid] = updateClient(clients[uid])
        publishClients()
        return AppResult.Success(Unit)
    }

    override suspend fun client(tenantId: String, uid: String): BookingResult<ClientRecord?> = AppResult.Success(clients[uid])

    override fun observeClient(tenantId: String, uid: String): Flow<ClientRecord?> {
        publishClients()
        return clientsFlow.map { it[uid] }
    }

    override fun observeClients(tenantId: String): Flow<List<ClientRecord>> {
        publishClients()
        return clientsFlow.map { it.values.toList() }
    }

    override suspend fun updateClient(
        tenantId: String,
        uid: String,
        change: (ClientRecord) -> BookingResult<ClientRecord>,
    ): BookingResult<ClientRecord> {
        val current = clients[uid] ?: return AppResult.Failure(BookingFailure.NotYours)
        val r = change(current)
        if (r is AppResult.Success) {
            clients[uid] = r.value
            publishClients()
        }
        return r
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
