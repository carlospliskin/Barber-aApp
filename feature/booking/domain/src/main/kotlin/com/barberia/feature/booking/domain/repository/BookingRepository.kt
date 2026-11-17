package com.barberia.feature.booking.domain.repository

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.ClientRecord
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface BookingRepository {
    /** Bloques ocupados de un barbero en un día (1 lectura). */
    suspend fun dayBookings(tenantId: String, barberId: String, date: LocalDate): BookingResult<Set<Int>>

    /** R2: una cita por id (o null si no existe). */
    suspend fun appointment(tenantId: String, id: String): BookingResult<Appointment?>

    /** F7-3: citas BOOKED del cliente en la barbería (pasadas y futuras). */
    suspend fun activeAppointments(tenantId: String, clientUid: String): BookingResult<List<Appointment>>

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

    /** N5: ¿hay alguna cita BOOKED desde [from]? (1 lectura: límite 1). */
    suspend fun hasUpcoming(tenantId: String, from: LocalDate): BookingResult<Boolean>

    /** N5: guarda la zona horaria de la barbería. */
    suspend fun updateZone(tenantId: String, zone: java.time.ZoneId): BookingResult<Unit>

    /** R7: reserva [code] para la barbería. `false` si ya lo tiene otra. */
    suspend fun claimShareCode(tenantId: String, code: String): BookingResult<Boolean>

    /** A3: citas de un día (de un barbero, o de todos si [barberId] es null). Nunca todo el historial. */
    fun observeDay(tenantId: String, date: LocalDate, barberId: String?): Flow<List<Appointment>>

    /**
     * A4/P7: cambia el estado de una cita BOOKED a COMPLETED o NO_SHOW y actualiza la ficha del cliente
     * en la MISMA transacción ([updateClient] recibe la ficha actual o null si es su primera vez).
     * N1: [updateClient] es null en citas sin cuenta (no tienen ficha).
     */
    suspend fun setStatus(
        appointment: Appointment,
        status: AppointmentStatus,
        updateClient: ((ClientRecord?) -> ClientRecord)?
    ): BookingResult<Unit>

    suspend fun client(tenantId: String, uid: String): BookingResult<ClientRecord?>

    fun observeClient(tenantId: String, uid: String): Flow<ClientRecord?>

    /** P5: clientes de la barbería (máx. 50, los más recientes). */
    fun observeClients(tenantId: String): Flow<List<ClientRecord>>

    /** Cambia la ficha en una transacción (premios, perdonar). Si [change] falla no se guarda nada. */
    suspend fun updateClient(
        tenantId: String,
        uid: String,
        change: (ClientRecord) -> BookingResult<ClientRecord>
    ): BookingResult<ClientRecord>

    /** Barbería a la que apunta un código para compartir (B0), o null. */
    suspend fun resolveShareCode(code: String): BookingResult<String?>
}
