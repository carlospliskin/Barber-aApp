package com.barberia.feature.catalog.domain.model

import java.time.DayOfWeek

/** Dinero en centavos (evita errores de redondeo con Double). */
@JvmInline
value class Money(val cents: Long) {
    val label: String
        get() {
            val pesos = cents / 100
            val rest = cents % 100
            return if (rest == 0L) "$$pesos" else "$$pesos.${rest.toString().padStart(2, '0')}"
        }

    companion object {
        fun pesos(value: Long) = Money(value * 100)
    }
}

data class Service(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val price: Money,
    val active: Boolean = true,
)

/** Lo que el dueño escribe en el formulario de servicio. */
data class ServiceDraft(val name: String, val durationMinutes: Int, val price: Money)

/** Rango en minutos desde las 00:00. `end` es exclusivo. */
data class TimeRange(val startMinute: Int, val endMinute: Int) {
    val isValid: Boolean
        get() = startMinute in 0 until DAY && endMinute in 1..DAY && startMinute < endMinute &&
            startMinute % STEP == 0 && endMinute % STEP == 0

    fun overlaps(other: TimeRange) = startMinute < other.endMinute && other.startMinute < endMinute

    val startLabel: String get() = label(startMinute)
    val endLabel: String get() = label(endMinute)

    companion object {
        const val DAY = 24 * 60
        const val STEP = 5

        /** "09:30" → minutos. */
        fun of(start: String, end: String) = TimeRange(parse(start), parse(end))

        fun parse(hhmm: String): Int {
            val (h, m) = hhmm.split(':').map { it.trim().toInt() }
            return h * 60 + m
        }

        fun label(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    }
}

data class WeeklySchedule(val days: Map<DayOfWeek, List<TimeRange>>) {
    val isEmpty: Boolean get() = days.values.all { it.isEmpty() }

    companion object {
        val EMPTY = WeeklySchedule(emptyMap())
    }
}

data class Barber(
    val id: String,
    val name: String,
    val active: Boolean = true,
    val schedule: WeeklySchedule = WeeklySchedule.EMPTY,
)

/**
 * Todo el catálogo de una barbería vive en UN documento: el cliente lo carga con una sola lectura.
 * [version] sube en cada guardado para detectar ediciones simultáneas (C9).
 */
data class Catalog(
    val services: List<Service>,
    val barbers: List<Barber>,
    val version: Long,
) {
    val bookableServices: List<Service> get() = services.filter { it.active }
    /** Un barbero se puede reservar si está activo y tiene al menos un horario. */
    val bookableBarbers: List<Barber> get() = barbers.filter { it.active && !it.schedule.isEmpty }

    companion object {
        fun empty() = Catalog(emptyList(), emptyList(), version = 0)
    }
}

enum class CatalogError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    NAME_DUPLICATED,
    DURATION_INVALID,
    PRICE_INVALID,
    TOO_MANY_ITEMS,
    SCHEDULE_RANGE_INVALID,
    SCHEDULE_OVERLAP,
}

sealed interface CatalogFailure {
    data class Validation(val errors: Set<CatalogError>) : CatalogFailure
    data object NotFound : CatalogFailure
    data object NotOwner : CatalogFailure
    data object NoConnection : CatalogFailure
    data class Unknown(val detail: String? = null) : CatalogFailure
}
