package com.barberia.feature.booking.domain

import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.slotKeys
import com.barberia.feature.booking.domain.availability.AvailabilityCalculator
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class AvailabilityTest {
    private val monday = LocalDate.of(2026, 11, 16) // lunes
    private val sunday = monday.minusDays(1)
    private val policy = BookingPolicy(minNoticeMinutes = 30, maxDaysAhead = 30, slotIntervalMinutes = 15)
    private val calc = AvailabilityCalculator(policy)

    private val luis = Barber(
        "b1", "Luis",
        schedule = WeeklySchedule(
            mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "12:00"), TimeRange.of("13:00", "14:00"))),
        ),
    )
    private val corte = Service("s1", "Corte", 45, Money(15_000))
    private val yesterdayNight = LocalDateTime.of(2026, 11, 15, 20, 0)

    private fun slots(
        now: LocalDateTime = yesterdayNight,
        date: LocalDate = monday,
        booked: Set<Int> = emptySet(),
        barber: Barber = luis,
        service: Service = corte,
    ) = calc.slots(barber, service, date, booked, now).map { TimeRange.label(it) }

    @Test fun `ofrece cada 15 minutos y el servicio cabe completo en el turno (B2)`() =
        assertEquals(
            listOf("10:00", "10:15", "10:30", "10:45", "11:00", "11:15", "13:00", "13:15"),
            slots(),
        )

    @Test fun `B2 un servicio no se mete en el descanso`() =
        assertTrue("11:30" !in slots()) // 11:30 + 45 = 12:15, pasa el descanso

    @Test fun `B1 hoy no ofrece horas pasadas ni sin la anticipacion minima`() {
        val now = LocalDateTime.of(2026, 11, 16, 10, 20) // lunes 10:20 → desde 10:50
        assertEquals(listOf("11:00", "11:15", "13:00", "13:15"), slots(now = now))
    }

    @Test fun `B1 un dia pasado no tiene horarios`() =
        assertEquals(emptyList<String>(), slots(now = LocalDateTime.of(2026, 11, 17, 9, 0)))

    @Test fun `B8 no mas de 30 dias adelante`() {
        assertEquals(emptyList<String>(), slots(date = monday.plusDays(35)))
        assertTrue(slots(date = monday.plusDays(28)).isNotEmpty()) // lunes dentro de la ventana
    }

    @Test fun `dia que no trabaja`() = assertEquals(emptyList<String>(), slots(date = sunday.plusDays(7)))

    @Test fun `B3 los bloques ocupados quitan todos los inicios que se enciman`() {
        val taken = slotKeys(start = TimeRange.parse("10:30"), duration = 30) // 10:30–11:00
        assertEquals(listOf("11:00", "11:15", "13:00", "13:15"), slots(booked = taken))
    }

    @Test fun `B9 barbero inactivo o servicio desactivado no tienen horarios`() {
        assertEquals(emptyList<String>(), slots(barber = luis.copy(active = false)))
        assertEquals(emptyList<String>(), slots(service = corte.copy(active = false)))
    }

    @Test fun `bloques de 5 minutos que ocupa una cita`() =
        assertEquals(setOf(600, 605, 610), slotKeys(start = 600, duration = 15))

    @Test fun `N2 un dia libre no tiene horarios`() =
        assertEquals(emptyList<String>(), slots(barber = luis.copy(timeOff = listOf(TimeOff(monday)))))

    @Test fun `N2 un dia libre de otra fecha no afecta`() =
        assertEquals(8, slots(barber = luis.copy(timeOff = listOf(TimeOff(monday.plusDays(7))))).size)

    @Test fun `N2 salir temprano quita los inicios cuyo servicio se encima con el rango bloqueado`() {
        val early = luis.copy(timeOff = listOf(TimeOff(monday, TimeRange.of("11:00", "14:00"))))
        assertEquals(listOf("10:00", "10:15"), slots(barber = early)) // 10:15 + 45 = 11:00 justo
    }
}
