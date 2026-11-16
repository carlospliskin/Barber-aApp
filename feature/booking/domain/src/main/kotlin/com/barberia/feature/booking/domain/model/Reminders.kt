package com.barberia.feature.booking.domain.model

import java.time.Duration
import java.time.Instant

/** R1: 3 h antes, para que el cliente todavía pueda cancelar (el límite es 2 h). */
val REMINDER_BEFORE: Duration = Duration.ofHours(3)

/**
 * Al disparar el aviso: R2 la cita debe seguir vigente (pudo cancelarla el personal o una penalización);
 * R6 Android pudo retrasar el trabajo, y no se avisa si la cita ya empezó.
 */
fun shouldNotify(appointment: Appointment, now: Instant): Boolean =
    appointment.status == AppointmentStatus.BOOKED && now.isBefore(appointment.startsAt)

/** Momento del recordatorio, o null si ya no tiene sentido (cita no vigente o demasiado cerca). */
fun reminderTime(appointment: Appointment, now: Instant): Instant? {
    if (appointment.status != AppointmentStatus.BOOKED) return null
    val at = appointment.startsAt.minus(REMINDER_BEFORE)
    return at.takeIf { it.isAfter(now) }
}
