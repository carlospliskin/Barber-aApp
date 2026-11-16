package com.barberia.feature.catalog.data.gateway

import kotlinx.coroutines.flow.Flow

/** Documento `tenants/{tenantId}/catalog/main` como mapa crudo. */
internal interface CatalogGateway {
    fun observe(tenantId: String): Flow<Map<String, Any?>?>

    /** Transacción: lee el documento, aplica [update] y guarda lo que devuelva (null = no guardar). */
    suspend fun transact(tenantId: String, update: (Map<String, Any?>?) -> Map<String, Any?>?)
}
