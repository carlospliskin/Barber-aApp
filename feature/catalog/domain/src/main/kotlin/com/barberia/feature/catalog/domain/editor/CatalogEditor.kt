package com.barberia.feature.catalog.domain.editor

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.ServiceDraft
import com.barberia.feature.catalog.domain.model.WeeklySchedule

typealias CatalogResult<T> = AppResult<CatalogFailure, T>

/** Genera ids para servicios y barberos (UUID en la app, secuencial en tests). */
fun interface IdGenerator {
    fun next(): String
}

/**
 * Reglas puras para editar el catálogo: reciben un catálogo y devuelven el nuevo (o el error).
 * No guardan nada; el repositorio las aplica dentro de una transacción sobre la versión más reciente.
 */
class CatalogEditor(private val ids: IdGenerator) {

    fun addService(catalog: Catalog, draft: ServiceDraft): CatalogResult<Catalog> {
        if (catalog.services.size >= MAX_SERVICES) return invalid(CatalogError.TOO_MANY_ITEMS)
        val name = draft.name.cleanName()
        val errors = serviceErrors(draft, name) + duplicate(name, catalog.activeServiceNames(except = null))
        if (errors.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(errors))
        val service = Service(ids.next(), name, draft.durationMinutes, draft.price, active = true)
        return ok(catalog.copy(services = catalog.services + service))
    }

    fun updateService(catalog: Catalog, id: String, draft: ServiceDraft): CatalogResult<Catalog> {
        val current = catalog.services.find { it.id == id } ?: return AppResult.Failure(CatalogFailure.NotFound)
        val name = draft.name.cleanName()
        val dupes = if (current.active) duplicate(name, catalog.activeServiceNames(except = id)) else emptySet()
        val errors = serviceErrors(draft, name) + dupes
        if (errors.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(errors))
        val updated = current.copy(name = name, durationMinutes = draft.durationMinutes, price = draft.price)
        return ok(catalog.copy(services = catalog.services.replace(id) { updated }))
    }

    /** C3: nunca se borra; se desactiva para no romper citas que lo usan. */
    fun setServiceActive(catalog: Catalog, id: String, active: Boolean): CatalogResult<Catalog> {
        val current = catalog.services.find { it.id == id } ?: return AppResult.Failure(CatalogFailure.NotFound)
        if (active && !current.active) {
            val dupes = duplicate(current.name, catalog.activeServiceNames(except = id))
            if (dupes.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(dupes))
        }
        return ok(catalog.copy(services = catalog.services.replace(id) { it.copy(active = active) }))
    }

    fun addBarber(catalog: Catalog, rawName: String): CatalogResult<Catalog> {
        if (catalog.barbers.size >= MAX_BARBERS) return invalid(CatalogError.TOO_MANY_ITEMS)
        val name = rawName.cleanName()
        val errors = nameErrors(name) + duplicate(name, catalog.activeBarberNames(except = null))
        if (errors.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(errors))
        return ok(catalog.copy(barbers = catalog.barbers + Barber(ids.next(), name)))
    }

    fun renameBarber(catalog: Catalog, id: String, rawName: String): CatalogResult<Catalog> {
        val current = catalog.barbers.find { it.id == id } ?: return AppResult.Failure(CatalogFailure.NotFound)
        val name = rawName.cleanName()
        val dupes = if (current.active) duplicate(name, catalog.activeBarberNames(except = id)) else emptySet()
        val errors = nameErrors(name) + dupes
        if (errors.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(errors))
        return ok(catalog.copy(barbers = catalog.barbers.replace(id) { it.copy(name = name) }))
    }

    /** C4: se desactiva, no se borra. */
    fun setBarberActive(catalog: Catalog, id: String, active: Boolean): CatalogResult<Catalog> {
        val current = catalog.barbers.find { it.id == id } ?: return AppResult.Failure(CatalogFailure.NotFound)
        if (active && !current.active) {
            val dupes = duplicate(current.name, catalog.activeBarberNames(except = id))
            if (dupes.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(dupes))
        }
        return ok(catalog.copy(barbers = catalog.barbers.replace(id) { it.copy(active = active) }))
    }

    /** C5: rangos válidos, sin encimarse; se guardan ordenados. Días sin rangos se omiten. */
    fun setSchedule(catalog: Catalog, id: String, schedule: WeeklySchedule): CatalogResult<Catalog> {
        if (catalog.barbers.none { it.id == id }) return AppResult.Failure(CatalogFailure.NotFound)
        val errors = buildSet {
            schedule.days.values.forEach { ranges ->
                if (ranges.any { !it.isValid }) add(CatalogError.SCHEDULE_RANGE_INVALID)
                val sorted = ranges.sortedBy { it.startMinute }
                if (sorted.zipWithNext().any { (a, b) -> a.overlaps(b) }) add(CatalogError.SCHEDULE_OVERLAP)
            }
        }
        if (errors.isNotEmpty()) return AppResult.Failure(CatalogFailure.Validation(errors))
        val normalized = WeeklySchedule(
            schedule.days.filterValues { it.isNotEmpty() }.mapValues { (_, r) -> r.sortedBy { it.startMinute } },
        )
        return ok(catalog.copy(barbers = catalog.barbers.replace(id) { it.copy(schedule = normalized) }))
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    private fun serviceErrors(draft: ServiceDraft, name: String) = buildSet {
        addAll(nameErrors(name))
        val d = draft.durationMinutes
        if (d < MIN_DURATION || d > MAX_DURATION || d % STEP != 0) add(CatalogError.DURATION_INVALID)
        if (draft.price.cents !in 0..MAX_PRICE_CENTS) add(CatalogError.PRICE_INVALID)
    }

    private fun nameErrors(name: String) = buildSet {
        if (name.isEmpty()) add(CatalogError.NAME_REQUIRED)
        if (name.length > MAX_NAME) add(CatalogError.NAME_TOO_LONG)
    }

    private fun duplicate(name: String, existing: Set<String>) =
        if (name.isNotEmpty() && name.lowercase() in existing) setOf(CatalogError.NAME_DUPLICATED) else emptySet()

    private fun Catalog.activeServiceNames(except: String?) =
        services.filter { it.active && it.id != except }.map { it.name.lowercase() }.toSet()

    private fun Catalog.activeBarberNames(except: String?) =
        barbers.filter { it.active && it.id != except }.map { it.name.lowercase() }.toSet()

    private fun String.cleanName() = trim().replace(Regex("\\s+"), " ")

    private fun <T : Any> List<T>.replace(id: String, change: (T) -> T): List<T> = map {
        val itemId = when (it) {
            is Service -> it.id
            is Barber -> it.id
            else -> null
        }
        if (itemId == id) change(it) else it
    }

    private fun ok(catalog: Catalog): CatalogResult<Catalog> = AppResult.Success(catalog)
    private fun invalid(error: CatalogError): CatalogResult<Catalog> =
        AppResult.Failure(CatalogFailure.Validation(setOf(error)))

    companion object {
        const val MAX_SERVICES = 50
        const val MAX_BARBERS = 20
        const val MAX_NAME = 60
        const val MIN_DURATION = 5
        const val MAX_DURATION = 480
        const val STEP = 5
        const val MAX_PRICE_CENTS = 100_000L * 100
    }
}
