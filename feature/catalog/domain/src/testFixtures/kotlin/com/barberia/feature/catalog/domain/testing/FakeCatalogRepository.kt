package com.barberia.feature.catalog.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Repositorio en memoria: simula el documento y su versión. */
class FakeCatalogRepository(initial: Catalog = Catalog.empty()) : CatalogRepository {
    val stored = MutableStateFlow(initial)
    var failure: CatalogFailure? = null
    var edits = 0

    override fun observe(tenantId: String): Flow<Catalog> = stored

    override suspend fun edit(tenantId: String, change: (Catalog) -> CatalogResult<Catalog>): CatalogResult<Catalog> {
        failure?.let { return AppResult.Failure(it) }
        edits++
        return when (val r = change(stored.value)) {
            is AppResult.Success -> {
                val saved = r.value.copy(version = stored.value.version + 1)
                stored.value = saved
                AppResult.Success(saved)
            }
            is AppResult.Failure -> r
        }
    }
}
