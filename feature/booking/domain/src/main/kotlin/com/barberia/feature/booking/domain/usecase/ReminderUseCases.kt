package com.barberia.feature.booking.domain.usecase

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.reminderTime
import com.barberia.feature.booking.domain.repository.ReminderScheduler
import java.time.Clock

class SyncRemindersUseCase(private val scheduler: ReminderScheduler, private val clock: Clock) {
    /** R3: (re)programa los recordatorios de las citas próximas. Idempotente: abrir el home no duplica. */
    operator fun invoke(appointments: List<Appointment>) {
        val now = clock.instant()
        appointments.forEach { a ->
            val at = reminderTime(a, now)
            if (at != null) scheduler.schedule(a.tenantId, a.id, at) else scheduler.cancel(a.id)
        }
    }
}

class CancelReminderUseCase(private val scheduler: ReminderScheduler) {
    /** R5 */
    operator fun invoke(appointmentId: String) = scheduler.cancel(appointmentId)
}
