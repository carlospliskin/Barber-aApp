package com.barberia.feature.catalog.data.repository

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.catalog.data.gateway.CatalogGateway
import com.barberia.feature.catalog.data.mapper.CatalogMapper
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FirestoreCatalogRepository(private val gateway: CatalogGateway) : CatalogRepository {

    override fun observe(tenantId: String): Flow<Catalog> =
        gateway.observe(tenantId).map { CatalogMapper.fromMap(it) ?: Catalog.empty() }

    override suspend fun edit(
        tenantId: String,
        change: (Catalog) -> CatalogResult<Catalog>,
    ): CatalogResult<Catalog> {
        // La transacción puede reintentarse: el último intento es el que cuenta.
        var outcome: CatalogResult<Catalog> = AppResult.Failure(CatalogFailure.Unknown())
        return try {
            gateway.transact(tenantId) { raw ->
                val current = CatalogMapper.fromMap(raw) ?: Catalog.empty()
                outcome = when (val r = change(current)) {
                    is AppResult.Success -> AppResult.Success(r.value.copy(version = current.version + 1))
                    is AppResult.Failure -> r
                }
                (outcome as? AppResult.Success)?.let { CatalogMapper.toMap(it.value) }
            }
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: GatewayError) {
            AppResult.Failure(e.toFailure())
        }
    }
}

internal fun GatewayError.toFailure(): CatalogFailure = when (this) {
    is GatewayError.Network -> CatalogFailure.NoConnection
    is GatewayError.PermissionDenied -> CatalogFailure.NotOwner
    else -> CatalogFailure.Unknown(cause?.message)
}
