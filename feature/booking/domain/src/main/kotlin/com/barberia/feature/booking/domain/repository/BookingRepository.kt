package com.barberia.feature.booking.domain.repository

import com.barberia.feature.booking.domain.model.Appointment
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

    fun observeClientAppointments(tenantId: String, clientUid: String): Flow<List<Appointment>>

    /** Barbería a la que apunta un código para compartir (B0), o null. */
    suspend fun resolveShareCode(code: String): BookingResult<String?>
}
