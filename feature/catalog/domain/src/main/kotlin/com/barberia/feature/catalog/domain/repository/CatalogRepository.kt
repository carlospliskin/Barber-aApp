package com.barberia.feature.catalog.domain.repository

import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.Catalog
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    /** Catálogo en vivo (vacío si la barbería todavía no tiene). */
    fun observe(tenantId: String): Flow<Catalog>

    /**
     * Aplica [change] sobre la versión MÁS RECIENTE del catálogo, de forma atómica (C9),
     * y guarda el resultado con la versión + 1. Si [change] falla, no se guarda nada.
     */
    suspend fun edit(tenantId: String, change: (Catalog) -> CatalogResult<Catalog>): CatalogResult<Catalog>
}
