package com.barberia.feature.booking.data.repository

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.booking.data.gateway.BookingGateway
import com.barberia.feature.booking.data.gateway.SlotTakenException
import com.barberia.feature.booking.data.mapper.AppointmentMapper
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.dayId
import com.barberia.feature.booking.domain.repository.BookingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

internal class FirestoreBookingRepository(private val gateway: BookingGateway) : BookingRepository {

    override suspend fun dayBookings(tenantId: String, barberId: String, date: LocalDate) = attempt {
        AppointmentMapper.bookedMinutes(gateway.getDay(tenantId, dayId(barberId, date)))
    }

    override suspend fun countActive(tenantId: String, clientUid: String, from: LocalDate) = attempt {
        gateway.activeAppointments(tenantId, clientUid)
            .mapNotNull { AppointmentMapper.fromMap("", tenantId, it) }
            .count { !it.date.isBefore(from) }
    }

    override suspend fun book(appointment: Appointment): BookingResult<Unit> = attempt(onDenied = BookingFailure.SlotTaken) {
        gateway.book(
            appointment.tenantId,
            appointment.id,
            AppointmentMapper.toMap(appointment),
            dayId(appointment.barberId, appointment.date),
            appointment.slots.map { it.toString() }.toSet(),
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

    override fun observeClientAppointments(tenantId: String, clientUid: String): Flow<List<Appointment>> =
        gateway.observeClient(tenantId, clientUid).map { docs ->
            docs.mapNotNull { (id, data) -> AppointmentMapper.fromMap(id, tenantId, data) }
                .sortedWith(compareBy({ it.date }, { it.startMinute }))
        }

    override suspend fun resolveShareCode(code: String) = attempt { gateway.shareCode(code) }

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
