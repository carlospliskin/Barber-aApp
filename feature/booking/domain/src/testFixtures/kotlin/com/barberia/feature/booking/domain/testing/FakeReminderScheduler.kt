package com.barberia.feature.booking.domain.testing

import com.barberia.feature.booking.domain.repository.ReminderScheduler
import java.time.Instant

class FakeReminderScheduler : ReminderScheduler {
    val scheduled = linkedMapOf<String, Instant>()
    override fun schedule(tenantId: String, appointmentId: String, at: Instant) {
        scheduled[appointmentId] = at
    }
    override fun cancel(appointmentId: String) {
        scheduled.remove(appointmentId)
    }
}
