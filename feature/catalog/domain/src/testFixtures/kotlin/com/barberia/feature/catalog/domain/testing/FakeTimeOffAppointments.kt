package com.barberia.feature.catalog.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.repository.TimeOffAppointments

/** N2-b: citas que choca un día libre, en memoria. */
class FakeTimeOffAppointments(var affected: Int = 0, var failure: CatalogFailure? = null) : TimeOffAppointments {
    val cancelled = mutableListOf<Pair<String?, TimeOff>>()

    override suspend fun affected(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Int> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(affected)

    override suspend fun cancel(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Int> {
        failure?.let { return AppResult.Failure(it) }
        cancelled += barberId to off
        return AppResult.Success(affected).also { affected = 0 }
    }
}
