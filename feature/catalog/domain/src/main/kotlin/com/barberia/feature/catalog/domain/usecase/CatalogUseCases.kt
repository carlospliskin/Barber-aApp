package com.barberia.feature.catalog.domain.usecase

import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.ServiceDraft
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.repository.CatalogRepository
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
    suspend operator fun invoke(tenantId: String, name: String): CatalogResult<Catalog> =
        repo.edit(tenantId) { editor.addBarber(it, name) }
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
