package com.barberia.feature.booking.domain.model

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reglas de negocio configurables de las reservas. */
data class BookingPolicy(
    val minNoticeMinutes: Int = 30,
    val maxDaysAhead: Int = 30,
    val slotIntervalMinutes: Int = 15,
    val maxActivePerClient: Int = 2,
    val cancelHoursBefore: Long = 2,
)

enum class AppointmentStatus { BOOKED, CANCELLED, COMPLETED, NO_SHOW }

/**
 * Cita. Guarda copia del nombre y precio del servicio: si el catálogo cambia después,
 * la cita conserva lo que se acordó.
 */
data class Appointment(
    val id: String,
    val tenantId: String,
    val clientUid: String,
    val clientName: String,
    val barberId: String,
    val barberName: String,
    val serviceId: String,
    val serviceName: String,
    val price: Money,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val status: AppointmentStatus,
    /** B10: inicio como instante (UTC). Las reglas de Firestore lo usan para el límite de cancelación. */
    val startsAt: Instant,
) {
    val slots: Set<Int> get() = slotKeys(startMinute, endMinute - startMinute)
}

data class BookingRequest(
    val tenantId: String,
    /** Zona horaria de la barbería (B6): "hoy" y "ahora" se calculan aquí, no con la del teléfono. */
    val zone: ZoneId,
    val clientUid: String,
    val clientName: String,
    val barberId: String,
    val serviceId: String,
    val date: LocalDate,
    val startMinute: Int,
)

sealed interface BookingFailure {
    data object SlotTaken : BookingFailure
    data object SlotUnavailable : BookingFailure
    data object ServiceUnavailable : BookingFailure
    data object BarberUnavailable : BookingFailure
    data object TooManyActive : BookingFailure
    data object CancelTooLate : BookingFailure
    data object NotYours : BookingFailure
    data object AlreadyClosed : BookingFailure
    data object InvalidCode : BookingFailure
    data object NoConnection : BookingFailure
    data class Unknown(val detail: String? = null) : BookingFailure
}

typealias BookingResult<T> = AppResult<BookingFailure, T>

/** Unidad de agenda: bloques de 5 minutos (la misma que usa el catálogo para horarios y duraciones). */
const val SLOT_UNIT = 5

/** Bloques de 5 min que ocupa una cita que empieza en [start] y dura [duration] minutos. */
fun slotKeys(start: Int, duration: Int): Set<Int> = (start until start + duration step SLOT_UNIT).toSet()

/** Id del documento de agenda de un barbero en un día: `b1_2026-11-16`. */
fun dayId(barberId: String, date: LocalDate) = "${barberId}_$date"
