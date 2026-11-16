package com.barberia.feature.booking.data.repository

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.booking.data.gateway.BookingGateway
import com.barberia.feature.booking.data.gateway.SlotTakenException
import com.barberia.feature.booking.data.mapper.AppointmentMapper
import com.barberia.feature.booking.data.mapper.ClientMapper
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.dayId
import com.barberia.feature.booking.domain.repository.BookingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.barberia.feature.booking.domain.model.BookingException
import java.time.LocalDate
import java.time.ZoneId

private val DEFAULT_ZONE: ZoneId = ZoneId.of("America/Mexico_City")

internal class FirestoreBookingRepository(private val gateway: BookingGateway) : BookingRepository {

    override suspend fun dayBookings(tenantId: String, barberId: String, date: LocalDate) = attempt {
        AppointmentMapper.bookedMinutes(gateway.getDay(tenantId, dayId(barberId, date)))
    }

    override suspend fun countActive(tenantId: String, clientUid: String, from: LocalDate) = attempt {
        gateway.activeAppointments(tenantId, clientUid)
            .mapNotNull { (id, m) -> AppointmentMapper.fromMap(id, tenantId, m) }
            .count { !it.date.isBefore(from) }
    }

    override suspend fun appointment(tenantId: String, id: String) = attempt {
        gateway.getAppointment(tenantId, id)?.let { AppointmentMapper.fromMap(id, tenantId, it) }
    }

    override suspend fun activeAppointments(tenantId: String, clientUid: String) = attempt {
        gateway.activeAppointments(tenantId, clientUid).mapNotNull { (id, m) -> AppointmentMapper.fromMap(id, tenantId, m) }
    }

    override suspend fun book(appointment: Appointment): BookingResult<Unit> = attempt(onDenied = BookingFailure.SlotTaken) {
        gateway.book(
            appointment.tenantId,
            appointment.id,
            AppointmentMapper.toMap(appointment),
            dayId(appointment.barberId, appointment.date),
            appointment.slots.map { it.toString() }.toSet(),
            appointment.clientUid,
            // N1: sin cuenta no hay ficha
            appointment.clientUid?.let { uid ->
                ClientMapper.toMap(ClientRecord.new(uid, appointment.clientName), lastAppointmentId = appointment.id)
            },
        )
    }

    override suspend fun cancel(appointment: Appointment) = attempt(onDenied = BookingFailure.CancelTooLate) {
        gateway.cancel(
            appointment.tenantId,
            appointment.id,
            dayId(appointment.barberId, appointment.date),
            appointment.slots.map { it.toString() }.toSet(),
        )
    }

    override fun observeUpcoming(tenantId: String, clientUid: String, from: LocalDate): Flow<List<Appointment>> =
        gateway.observeUpcoming(tenantId, clientUid, from.toString()).map { docs ->
            docs.mapNotNull { (id, data) -> AppointmentMapper.fromMap(id, tenantId, data) }
                .filter { it.status == AppointmentStatus.BOOKED } // U4 (la consulta ya filtra; doble seguro)
                .sortedWith(compareBy({ it.date }, { it.startMinute }))
        }.asBookingFlow()

    override fun observeDay(tenantId: String, date: LocalDate, barberId: String?): Flow<List<Appointment>> =
        gateway.observeDay(tenantId, date.toString(), barberId).map { docs ->
            docs.mapNotNull { (id, data) -> AppointmentMapper.fromMap(id, tenantId, data) }
        }.asBookingFlow()

    override suspend fun setStatus(
        appointment: Appointment,
        status: AppointmentStatus,
        updateClient: ((ClientRecord?) -> ClientRecord)?,
    ) = attempt(onDenied = BookingFailure.TooEarly) {
        val uid = appointment.clientUid
        val change: ((Map<String, Any?>?) -> Map<String, Any?>)? = if (uid == null || updateClient == null) {
            null
        } else {
            { raw -> ClientMapper.toMap(updateClient(ClientMapper.fromMap(uid, raw)), lastAppointmentId = appointment.id) }
        }
        gateway.setStatus(appointment.tenantId, appointment.id, status.name, uid, change)
    }

    override suspend fun client(tenantId: String, uid: String) = attempt {
        ClientMapper.fromMap(uid, gateway.getClient(tenantId, uid))
    }

    override fun observeClient(tenantId: String, uid: String): Flow<ClientRecord?> =
        gateway.observeClient(tenantId, uid).map { ClientMapper.fromMap(uid, it) }.asBookingFlow()

    override fun observeClients(tenantId: String): Flow<List<ClientRecord>> =
        gateway.observeClients(tenantId).map { docs -> docs.mapNotNull { (uid, m) -> ClientMapper.fromMap(uid, m) } }.asBookingFlow()

    override suspend fun updateClient(
        tenantId: String,
        uid: String,
        change: (ClientRecord) -> BookingResult<ClientRecord>,
    ): BookingResult<ClientRecord> {
        var outcome: BookingResult<ClientRecord> = AppResult.Failure(BookingFailure.NotYours)
        val written = attempt {
            gateway.updateClient(tenantId, uid) { raw ->
                val current = ClientMapper.fromMap(uid, raw) ?: return@updateClient null
                outcome = change(current)
                (outcome as? AppResult.Success)?.let {
                    // Se conserva lastAppointmentId: las reglas lo exigen como texto.
                    ClientMapper.toMap(it.value, lastAppointmentId = raw["lastAppointmentId"] as? String ?: "")
                }
            }
        }
        return if (written is AppResult.Failure) written else outcome
    }

    override suspend fun resolveShareCode(code: String) = attempt { gateway.shareCode(code) }

    override suspend fun barbershop(tenantId: String) = attempt {
        gateway.tenant(tenantId)?.let { t ->
            Barbershop(
                id = tenantId,
                name = t["name"] as? String ?: "",
                zone = (t["timezone"] as? String)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: DEFAULT_ZONE,
                shareCode = t["shareCode"] as? String,
            )
        }
    }

    override suspend fun claimShareCode(tenantId: String, code: String) = attempt { gateway.claimShareCode(tenantId, code) }

    /**
     * Si las reglas rechazan una reserva, en la práctica es porque otro cliente ganó el horario (B3);
     * si rechazan una cancelación, es porque ya pasó el límite de 2 h (B7).
     */
    private suspend fun <T> attempt(onDenied: BookingFailure? = null, block: suspend () -> T): BookingResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SlotTakenException) {
        AppResult.Failure(BookingFailure.SlotTaken)
    } catch (e: GatewayError) {
        AppResult.Failure(
            when (e) {
                is GatewayError.Network -> BookingFailure.NoConnection
                is GatewayError.PermissionDenied -> onDenied ?: BookingFailure.Unknown(e.cause?.message)
                else -> BookingFailure.Unknown(e.cause?.message)
            },
        )
    }
}

/** A16: errores de los Flow traducidos a fallas del dominio. */
internal fun Throwable.toBookingFailure(): BookingFailure = when (this) {
    is GatewayError.PermissionDenied -> BookingFailure.NoAccess
    is GatewayError.Network -> BookingFailure.NoConnection
    is GatewayError -> BookingFailure.Unknown(cause?.message)
    else -> BookingFailure.Unknown(message)
}

/** A16-e: los Flow del repositorio fallan con la falla del dominio, no con errores de Firebase. */
private fun <T> Flow<T>.asBookingFlow(): Flow<T> = catch { e ->
    throw if (e is BookingException) e else BookingException(e.toBookingFailure())
}
