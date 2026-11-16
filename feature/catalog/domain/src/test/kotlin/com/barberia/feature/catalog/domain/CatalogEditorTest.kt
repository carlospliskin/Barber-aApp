package com.barberia.feature.catalog.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.ServiceDraft
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEditorTest {

    private var nextId = 0
    private val editor = CatalogEditor { "id-${++nextId}" }
    private val empty = Catalog.empty()

    private fun draft(name: String = "Corte", minutes: Int = 30, pesos: Long = 150) = ServiceDraft(name, minutes, Money.pesos(pesos))

    private fun <T> AppResult<CatalogFailure, T>.ok(): T = (this as AppResult.Success).value
    private fun <T> AppResult<CatalogFailure, T>.errors(): Set<CatalogError> =
        ((this as AppResult.Failure).error as CatalogFailure.Validation).errors

    // ── Servicios ─────────────────────────────────────────────────────────────
    @Test fun `agregar servicio limpia el nombre y le da id`() {
        val c = editor.addService(empty, draft(name = "  Corte clásico ")).ok()
        assertEquals(listOf(Service("id-1", "Corte clásico", 30, Money(15_000), active = true)), c.services)
    }

    @Test fun `C7 duracion en pasos de 5 entre 5 y 480 minutos`() {
        assertEquals(setOf(CatalogError.DURATION_INVALID), editor.addService(empty, draft(minutes = 0)).errors())
        assertEquals(setOf(CatalogError.DURATION_INVALID), editor.addService(empty, draft(minutes = 32)).errors())
        assertEquals(setOf(CatalogError.DURATION_INVALID), editor.addService(empty, draft(minutes = 485)).errors())
        editor.addService(empty, draft(minutes = 480)).ok()
    }

    @Test fun `C7 precio sin negativos ni mayor a 100 mil pesos`() {
        assertEquals(setOf(CatalogError.PRICE_INVALID), editor.addService(empty, draft(pesos = -1)).errors())
        assertEquals(setOf(CatalogError.PRICE_INVALID), editor.addService(empty, draft(pesos = 100_001)).errors())
        editor.addService(empty, draft(pesos = 0)).ok() // servicio de cortesía
    }

    @Test fun `nombre vacio o muy largo`() {
        assertEquals(setOf(CatalogError.NAME_REQUIRED), editor.addService(empty, draft(name = "  ")).errors())
        assertEquals(setOf(CatalogError.NAME_TOO_LONG), editor.addService(empty, draft(name = "x".repeat(61))).errors())
    }

    @Test fun `C6 nombre repetido sin importar mayusculas ni espacios`() {
        val c = editor.addService(empty, draft(name = "Corte")).ok()
        assertEquals(setOf(CatalogError.NAME_DUPLICATED), editor.addService(c, draft(name = " corte ")).errors())
    }

    @Test fun `editar un servicio puede conservar su propio nombre`() {
        val c = editor.addService(empty, draft()).ok()
        val edited = editor.updateService(c, "id-1", draft(name = "Corte", minutes = 45)).ok()
        assertEquals(45, edited.services.single().durationMinutes)
    }

    @Test fun `editar un servicio que no existe`() {
        assertEquals(
            AppResult.Failure(CatalogFailure.NotFound),
            editor.updateService(empty, "nada", draft())
        )
    }

    @Test fun `C3 desactivar en vez de borrar y no se ofrece para reservar`() {
        val c = editor.addService(empty, draft()).ok()
        val off = editor.setServiceActive(c, "id-1", active = false).ok()
        assertEquals(1, off.services.size) // sigue existiendo para las citas viejas
        assertTrue(off.bookableServices.isEmpty())
    }

    @Test fun `C6 no se reactiva si otro servicio activo ya usa el nombre`() {
        var c = editor.addService(empty, draft(name = "Corte")).ok()
        c = editor.setServiceActive(c, "id-1", active = false).ok()
        c = editor.addService(c, draft(name = "Corte")).ok() // id-2 activo
        assertEquals(setOf(CatalogError.NAME_DUPLICATED), editor.setServiceActive(c, "id-1", active = true).errors())
    }

    @Test fun `un servicio inactivo no bloquea el nombre`() {
        var c = editor.addService(empty, draft(name = "Corte")).ok()
        c = editor.setServiceActive(c, "id-1", active = false).ok()
        editor.addService(c, draft(name = "Corte")).ok()
    }

    @Test fun `C8 maximo 50 servicios`() {
        var c = empty
        repeat(50) { c = editor.addService(c, draft(name = "S$it")).ok() }
        assertEquals(setOf(CatalogError.TOO_MANY_ITEMS), editor.addService(c, draft(name = "otro")).errors())
    }

    // ── Barberos ──────────────────────────────────────────────────────────────
    @Test fun `agregar barbero sin horario todavia no es reservable`() {
        val c = editor.addBarber(empty, " Luis ").ok()
        assertEquals(Barber("id-1", "Luis", active = true, schedule = WeeklySchedule.EMPTY), c.barbers.single())
        assertTrue(c.bookableBarbers.isEmpty())
    }

    @Test fun `C6 barbero con nombre repetido`() {
        val c = editor.addBarber(empty, "Luis").ok()
        assertEquals(setOf(CatalogError.NAME_DUPLICATED), editor.addBarber(c, "LUIS").errors())
    }

    @Test fun `C4 desactivar barbero lo saca de las reservas pero lo conserva`() {
        var c = editor.addBarber(empty, "Luis").ok()
        c = editor.setSchedule(c, "id-1", WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "18:00"))))).ok()
        assertEquals(1, c.bookableBarbers.size)
        c = editor.setBarberActive(c, "id-1", active = false).ok()
        assertEquals(1, c.barbers.size)
        assertTrue(c.bookableBarbers.isEmpty())
    }

    @Test fun `C8 maximo 20 barberos`() {
        var c = empty
        repeat(20) { c = editor.addBarber(c, "B$it").ok() }
        assertEquals(setOf(CatalogError.TOO_MANY_ITEMS), editor.addBarber(c, "otro").errors())
    }

    @Test fun `renombrar barbero`() {
        val c = editor.addBarber(empty, "Luis").ok()
        assertEquals("Luis F", editor.renameBarber(c, "id-1", " Luis F ").ok().barbers.single().name)
    }

    // ── Horarios (C5) ─────────────────────────────────────────────────────────
    @Test fun `C5 el rango debe terminar despues de empezar`() {
        val c = editor.addBarber(empty, "Luis").ok()
        val bad = WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("18:00", "10:00"))))
        assertEquals(setOf(CatalogError.SCHEDULE_RANGE_INVALID), editor.setSchedule(c, "id-1", bad).errors())
    }

    @Test fun `C5 rangos que se enciman en el mismo dia`() {
        val c = editor.addBarber(empty, "Luis").ok()
        val overlap = WeeklySchedule(
            mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "14:00"), TimeRange.of("13:30", "18:00")))
        )
        assertEquals(setOf(CatalogError.SCHEDULE_OVERLAP), editor.setSchedule(c, "id-1", overlap).errors())
    }

    @Test fun `C5 turno partido con descanso es valido y se ordena`() {
        val c = editor.addBarber(empty, "Luis").ok()
        val split = WeeklySchedule(
            mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("16:00", "20:00"), TimeRange.of("10:00", "14:00")))
        )
        val saved = editor.setSchedule(c, "id-1", split).ok().barbers.single().schedule
        assertEquals(listOf(TimeRange.of("10:00", "14:00"), TimeRange.of("16:00", "20:00")), saved.days[DayOfWeek.MONDAY])
    }

    @Test fun `C5 los horarios van en pasos de 5 minutos y dentro del dia`() {
        assertFalse(TimeRange(600, 607).isValid)
        assertFalse(TimeRange(0, 24 * 60 + 5).isValid)
        assertTrue(TimeRange(0, 24 * 60).isValid)
    }

    @Test fun `formato de hora y dinero`() {
        assertEquals("09:05", TimeRange.of("09:05", "10:00").startLabel)
        assertEquals("$150.50", Money(15_050).label)
        assertEquals("$0", Money(0).label)
    }
}
