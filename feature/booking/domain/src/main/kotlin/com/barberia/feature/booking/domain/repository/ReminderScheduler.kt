package com.barberia.feature.booking.domain.repository

import java.time.Instant

/** Recordatorios locales en el teléfono (sin servidor). Programar el mismo id lo reemplaza, no lo duplica. */
interface ReminderScheduler {
    fun schedule(tenantId: String, appointmentId: String, at: Instant)
    fun cancel(appointmentId: String)
}
