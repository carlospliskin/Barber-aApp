package com.barberia.feature.catalog.domain.usecase

import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.ServiceDraft
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// Cada caso de uso aplica una regla del editor dentro de la transacción del repositorio.

class ObserveCatalogUseCase(private val repo: CatalogRepository) {
    operator fun invoke(tenantId: String): Flow<Catalog> = repo.observe(tenantId)
}

class AddServiceUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, draft: ServiceDraft): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.addService(it, draft) }
}

class UpdateServiceUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, serviceId: String, draft: ServiceDraft): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.updateService(it, serviceId, draft) }
}

class SetServiceActiveUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, serviceId: String, active: Boolean): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.setServiceActive(it, serviceId, active) }
}

class AddBarberUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, name: String): CatalogResult<Catalog> = repo.edit(tenantId) { editor.addBarber(it, name) }
}

class RenameBarberUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, barberId: String, name: String): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.renameBarber(it, barberId, name) }
}

class SetBarberActiveUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, barberId: String, active: Boolean): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.setBarberActive(it, barberId, active) }
}

class SetBarberScheduleUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, barberId: String, schedule: WeeklySchedule): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.setSchedule(it, barberId, schedule) }
}

/**
 * N2-c: "hoy" en la zona horaria de la barbería. El reloj de la app va en UTC: desde las 18 h de la
 * Ciudad de México ya sería mañana y no se podría bloquear el día en curso.
 */
fun interface ShopCalendar {
    suspend fun today(tenantId: String): LocalDate

    companion object {
        fun of(clock: Clock, zoneOf: suspend (tenantId: String) -> java.time.ZoneId) =
            ShopCalendar { tenantId -> LocalDate.now(clock.withZone(zoneOf(tenantId))) }
    }
}

/** N2: la fecha de "hoy" sale del calendario de la barbería (no de la pantalla) para no aceptar días pasados. */
class AddTimeOffUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor, private val calendar: ShopCalendar) {
    suspend operator fun invoke(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Catalog> {
        val today = calendar.today(tenantId)
        return repo.edit(tenantId) { editor.addTimeOff(it, barberId, off, today) }
    }
}

class RemoveTimeOffUseCase(private val repo: CatalogRepository, private val editor: CatalogEditor) {
    suspend operator fun invoke(tenantId: String, barberId: String, off: TimeOff): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.removeTimeOff(it, barberId, off) }
}
