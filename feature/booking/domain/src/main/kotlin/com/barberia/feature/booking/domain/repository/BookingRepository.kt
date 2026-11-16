package com.barberia.feature.booking.domain.repository

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BookingRepository {
    /** Bloques ocupados de un barbero en un día (1 lectura). */
    suspend fun dayBookings(tenantId: String, barberId: String, date: LocalDate): BookingResult<Set<Int>>

    /** Citas BOOKED del cliente desde [from] (para el límite de citas activas). */
    suspend fun countActive(tenantId: String, clientUid: String, from: LocalDate): BookingResult<Int>

    /**
     * Guarda la cita y ocupa sus bloques de forma atómica.
     * Si algún bloque ya está tomado devuelve SlotTaken y no guarda nada (B3).
     */
    suspend fun book(appointment: Appointment): BookingResult<Unit>

    /** Marca la cita como cancelada y libera sus bloques, de forma atómica. */
    suspend fun cancel(appointment: Appointment): BookingResult<Unit>

    /** R4: citas del cliente desde [from], ordenadas (no todo el historial). */
    fun observeUpcoming(tenantId: String, clientUid: String, from: LocalDate): Flow<List<Appointment>>

    /** R6: nombre, zona horaria y código de la barbería (1 lectura). */
    suspend fun barbershop(tenantId: String): BookingResult<Barbershop?>

    /** R7: reserva [code] para la barbería. `false` si ya lo tiene otra. */
    suspend fun claimShareCode(tenantId: String, code: String): BookingResult<Boolean>

    /** A3: citas de un día (de un barbero, o de todos si [barberId] es null). Nunca todo el historial. */
    fun observeDay(tenantId: String, date: LocalDate, barberId: String?): Flow<List<Appointment>>

    /** A4: cambia el estado de una cita BOOKED a COMPLETED o NO_SHOW. */
    suspend fun setStatus(appointment: Appointment, status: AppointmentStatus): BookingResult<Unit>

    /** Barbería a la que apunta un código para compartir (B0), o null. */
    suspend fun resolveShareCode(code: String): BookingResult<String?>
}
