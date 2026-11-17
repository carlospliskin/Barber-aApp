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
    /** N1: el personal puede agendar en el turno que ya empezó hace hasta estos minutos (cliente que llega). */
    val staffLateStartMinutes: Int = 14
) {
    /** N1: para el personal no hay anticipación mínima; se acepta el turno actual. */
    fun forStaff(): BookingPolicy = copy(minNoticeMinutes = -staffLateStartMinutes)
}

enum class AppointmentStatus { BOOKED, CANCELLED, COMPLETED, NO_SHOW }

/**
 * Cita. Guarda copia del nombre y precio del servicio: si el catálogo cambia después,
 * la cita conserva lo que se acordó.
 */
data class Appointment(
    val id: String,
    val tenantId: String,
    /** N1: null = cliente sin cuenta (lo agendó el personal por teléfono o en el local). */
    val clientUid: String?,
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
    /** N1: teléfono (solo dígitos) del cliente sin cuenta, para avisarle. */
    val clientPhone: String? = null,
    /** F7-2: cuándo se marcó atendida/no-show (hora del servidor, `updatedAt`). */
    val markedAt: Instant? = null
) {
    val slots: Set<Int> get() = slotKeys(startMinute, endMinute - startMinute)

    /** F7-2: "atendida" o "no llegó" se pueden deshacer durante [UNDO_WINDOW]. */
    fun canUndo(now: Instant): Boolean {
        val at = markedAt ?: return false
        return status in UNDOABLE && now.isBefore(at.plus(UNDO_WINDOW))
    }

    companion object {
        val UNDO_WINDOW: java.time.Duration = java.time.Duration.ofMinutes(15)
        private val UNDOABLE = setOf(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
    }
    val isWalkIn: Boolean get() = clientUid == null && !isFromDeletedAccount

    /** A7: el cliente borró su cuenta (una cita sin cuenta siempre lleva nombre). */
    val isFromDeletedAccount: Boolean get() = clientUid == null && clientName.isBlank()
}

/** N5: zonas que el dueño puede elegir. La primera es la de alta. Las reglas de Firestore repiten esta lista. */
object ShopZones {
    val ALLOWED: List<ZoneId> = listOf(
        "America/Mexico_City", "America/Monterrey", "America/Merida", "America/Cancun", "America/Chihuahua",
        "America/Mazatlan", "America/Hermosillo", "America/Tijuana",
        "America/Bogota", "America/Lima", "America/Guatemala", "America/Santiago", "America/Argentina/Buenos_Aires",
        "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "Europe/Madrid"
    ).map(ZoneId::of)
}

/** Datos públicos de la barbería que necesita el cliente (R6). */
data class Barbershop(val id: String, val name: String, val zone: ZoneId, val shareCode: String?)

data class BookingRequest(
    val tenantId: String,
    /** Zona horaria de la barbería (B6): "hoy" y "ahora" se calculan aquí, no con la del teléfono. */
    val zone: ZoneId,
    val clientUid: String,
    val clientName: String,
    val barberId: String,
    val serviceId: String,
    val date: LocalDate,
    val startMinute: Int
)

/** N1: cita que agenda el personal para un cliente sin cuenta. */
data class WalkInRequest(
    val tenantId: String,
    val zone: ZoneId,
    val clientName: String,
    val clientPhone: String?,
    val barberId: String,
    val serviceId: String,
    val date: LocalDate,
    val startMinute: Int
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
    data object HasActiveAppointments : BookingFailure

    /** A7-d: Spark llegó a su límite del día (p. ej. 20 000 borrados). Mañana se reintenta y sigue donde quedó. */
    data object DailyLimit : BookingFailure

    /** N5: zona horaria fuera de [ShopZones.ALLOWED]. */
    data object InvalidZone : BookingFailure
    data object TooEarly : BookingFailure

    /** P2: penalizado por no-shows hasta [until]. */
    data class Penalized(val until: Instant) : BookingFailure
    data object NoRewardAvailable : BookingFailure

    /** N1: nombre vacío o teléfono inválido. */
    data object InvalidClient : BookingFailure
    data object NoConnection : BookingFailure

    /** A16: sin permiso (le quitaron el acceso o la barbería ya no existe). */
    data object NoAccess : BookingFailure

    /** A7-c: la barbería guardada ya no existe (se borró). */
    data object ShopGone : BookingFailure

    /** F7-2: pasaron los 15 minutos para deshacer. */
    data object UndoExpired : BookingFailure

    /** F7-2: ya canjeó un premio contando esa visita. */
    data object RewardAlreadyUsed : BookingFailure
    data class Unknown(val detail: String? = null) : BookingFailure
}

typealias BookingResult<T> = AppResult<BookingFailure, T>

/** Error de un Flow del repositorio (los Flow no devuelven AppResult). */
class BookingException(val failure: BookingFailure) : Exception(failure.toString())

/** Unidad de agenda: bloques de 5 minutos (la misma que usa el catálogo para horarios y duraciones). */
const val SLOT_UNIT = 5

/** Bloques de 5 min que ocupa una cita que empieza en [start] y dura [duration] minutos. */
fun slotKeys(start: Int, duration: Int): Set<Int> = (start until start + duration step SLOT_UNIT).toSet()

/** Id del documento de agenda de un barbero en un día: `b1_2026-11-16`. */
fun dayId(barberId: String, date: LocalDate) = "${barberId}_$date"
