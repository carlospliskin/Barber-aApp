package com.barberia.feature.booking.domain.repository

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.BookingResult

/** A7: datos personales que un usuario deja en las barberías (para eliminar su cuenta sin Cloud Functions). */
interface AccountDataRepository {
    /** Citas con `clientUid == uid` en todas las barberías (consulta de grupo), máx. [limit]. */
    suspend fun myAppointments(uid: String, limit: Int): BookingResult<List<Appointment>>

    /** Quita uid y nombre de las citas (quedan para la contabilidad de la barbería, sin datos personales). */
    suspend fun anonymize(appointments: List<Appointment>): BookingResult<Unit>

    /** Borra la ficha `clients/{uid}` de cada barbería (si no existe, no pasa nada). */
    suspend fun deleteClientRecords(uid: String, tenantIds: Set<String>): BookingResult<Unit>

    /** El dueño borra su barbería entera, por partes (se puede reintentar). */
    suspend fun deleteBarbershop(tenantId: String): BookingResult<Unit>
}
