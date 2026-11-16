package com.barberia.feature.catalog.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.usecase.AddTimeOffUseCase
import com.barberia.feature.catalog.domain.usecase.RemoveTimeOffUseCase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** N2: días libres, vacaciones y "hoy salgo temprano". */
class TimeOffTest {
    private var nextId = 0
    private val editor = CatalogEditor { "id-${++nextId}" }
    private val today = LocalDate.of(2026, 11, 16)
    private val twoBarbers = editor.addBarber(editor.addBarber(Catalog.empty(), "Luis").ok(), "Ana").ok()

    private fun <T> AppResult<CatalogFailure, T>.ok(): T = (this as AppResult.Success).value
    private fun <T> AppResult<CatalogFailure, T>.errors(): Set<CatalogError> =
        ((this as AppResult.Failure).error as CatalogFailure.Validation).errors

    @Test fun `N2 dia completo libre para un barbero`() {
        val off = TimeOff(today.plusDays(3))
        val c = editor.addTimeOff(twoBarbers, barberId = "id-1", off, today).ok()
        assertEquals(listOf(off), c.barbers[0].timeOff)
        assertEquals(emptyList<TimeOff>(), c.barbers[1].timeOff)
    }

    @Test fun `N2 sin barbero se cierra la barberia para todos (dia festivo)`() {
        val off = TimeOff(LocalDate.of(2026, 12, 25))
        val c = editor.addTimeOff(twoBarbers, barberId = null, off, today).ok()
        assertTrue(c.barbers.all { it.timeOff == listOf(off) })
    }

    @Test fun `N2 hoy salgo temprano bloquea solo un rango`() {
        val off = TimeOff(today, TimeRange.of("17:00", "20:00"))
        assertEquals(listOf(off), editor.addTimeOff(twoBarbers, "id-1", off, today).ok().barbers[0].timeOff)
    }

    @Test fun `N2 un rango invalido o una fecha pasada no se guardan`() {
        assertEquals(
            setOf(CatalogError.SCHEDULE_RANGE_INVALID),
            editor.addTimeOff(twoBarbers, "id-1", TimeOff(today, TimeRange(600, 607)), today).errors()
        )
        assertEquals(
            setOf(CatalogError.TIME_OFF_PAST),
            editor.addTimeOff(twoBarbers, "id-1", TimeOff(today.minusDays(1)), today).errors()
        )
    }

    @Test fun `N2 barbero que no existe`() = assertEquals(
        AppResult.Failure(CatalogFailure.NotFound),
        editor.addTimeOff(twoBarbers, "nadie", TimeOff(today), today)
    )

    @Test fun `N2 al guardar se borran los dias libres pasados (el documento no crece) y se ordenan`() {
        val old = twoBarbers.copy(
            barbers = twoBarbers.barbers.map { it.copy(timeOff = listOf(TimeOff(today.minusDays(10)), TimeOff(today.plusDays(9)))) }
        )
        val c = editor.addTimeOff(old, "id-1", TimeOff(today.plusDays(2)), today).ok()
        assertEquals(listOf(TimeOff(today.plusDays(2)), TimeOff(today.plusDays(9))), c.barbers[0].timeOff)
    }

    @Test fun `N2 el mismo dia libre dos veces no se duplica`() {
        val once = editor.addTimeOff(twoBarbers, "id-1", TimeOff(today), today).ok()
        assertEquals(1, editor.addTimeOff(once, "id-1", TimeOff(today), today).ok().barbers[0].timeOff.size)
    }

    @Test fun `N2 maximo 30 dias libres por barbero (tamano del documento)`() {
        var c = twoBarbers
        repeat(CatalogEditor.MAX_TIME_OFF) { c = editor.addTimeOff(c, "id-1", TimeOff(today.plusDays(it.toLong())), today).ok() }
        assertEquals(
            setOf(CatalogError.TOO_MANY_ITEMS),
            editor.addTimeOff(c, "id-1", TimeOff(today.plusDays(40)), today).errors()
        )
    }

    @Test fun `N2 quitar un dia libre`() {
        val off = TimeOff(today.plusDays(1))
        val c = editor.addTimeOff(twoBarbers, null, off, today).ok()
        val removed = editor.removeTimeOff(c, "id-2", off).ok()
        assertEquals(listOf(off), removed.barbers[0].timeOff)
        assertEquals(emptyList<TimeOff>(), removed.barbers[1].timeOff)
    }

    @Test fun `N2 que bloquea un dia libre`() {
        val early = TimeOff(today, TimeRange.of("17:00", "20:00"))
        assertTrue(early.blocks(today, TimeRange.parse("16:30"), TimeRange.parse("17:15")))
        assertFalse(early.blocks(today, TimeRange.parse("16:00"), TimeRange.parse("17:00")))
        assertFalse(early.blocks(today.plusDays(1), TimeRange.parse("18:00"), TimeRange.parse("18:30")))
        assertTrue(TimeOff(today).blocks(today, 0, 5))
    }

    @Test fun `N2 los casos de uso usan la fecha del reloj y la version mas reciente`() = runTest {
        val repo = FakeCatalogRepository(twoBarbers)
        val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val off = TimeOff(today)
        AddTimeOffUseCase(repo, editor, clock)("t1", "id-1", off).ok()
        assertEquals(listOf(off), repo.stored.value.barbers[0].timeOff)
        RemoveTimeOffUseCase(repo, editor)("t1", "id-1", off).ok()
        assertEquals(emptyList<TimeOff>(), repo.stored.value.barbers[0].timeOff)
        assertEquals(
            setOf(CatalogError.TIME_OFF_PAST),
            AddTimeOffUseCase(repo, editor, clock)("t1", "id-1", TimeOff(today.minusDays(1))).errors()
        )
    }

    @Test fun `N2 un barbero trabaja un dia si tiene horario y no lo tiene libre completo`() {
        val luis = com.barberia.feature.catalog.domain.model.Barber(
            "b1",
            "Luis",
            schedule = com.barberia.feature.catalog.domain.model.WeeklySchedule(
                mapOf(today.dayOfWeek to listOf(TimeRange.of("10:00", "14:00")))
            ),
            timeOff = listOf(TimeOff(today.plusDays(7)), TimeOff(today.plusDays(14), TimeRange.of("10:00", "11:00")))
        )
        assertTrue(luis.worksOn(today))
        assertFalse(luis.worksOn(today.plusDays(1))) // no tiene horario ese día
        assertFalse(luis.worksOn(today.plusDays(7))) // libre todo el día
        assertTrue(luis.worksOn(today.plusDays(14))) // solo libre un rato
    }
}
