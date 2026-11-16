package com.barberia.feature.booking.domain.availability

import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.slotKeys
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Service
import java.time.LocalDate
import java.time.LocalDateTime

/** Calcula los inicios disponibles de un servicio con un barbero en un día. Función pura. */
class AvailabilityCalculator(private val policy: BookingPolicy) {

    /**
     * @param booked bloques de 5 min ya ocupados ese día.
     * @param now fecha y hora actuales **en la zona de la barbería**.
     * @return minutos del día en que puede empezar el servicio, ordenados.
     */
    fun slots(barber: Barber, service: Service, date: LocalDate, booked: Set<Int>, now: LocalDateTime): List<Int> {
        if (!barber.active || !service.active) return emptyList()
        val today = now.toLocalDate()
        if (date.isBefore(today) || date.isAfter(today.plusDays(policy.maxDaysAhead.toLong()))) return emptyList()
        val earliest = if (date == today) now.hour * 60 + now.minute + policy.minNoticeMinutes else 0
        val duration = service.durationMinutes
        return barber.schedule.days[date.dayOfWeek].orEmpty().flatMap { range ->
            generateSequence(range.startMinute) { it + policy.slotIntervalMinutes }
                .takeWhile { it + duration <= range.endMinute } // B2: cabe completo en el turno
                .filter { it >= earliest } // B1
                .filter { start -> slotKeys(start, duration).none { it in booked } } // B3
                .toList()
        }.sorted()
    }
}
