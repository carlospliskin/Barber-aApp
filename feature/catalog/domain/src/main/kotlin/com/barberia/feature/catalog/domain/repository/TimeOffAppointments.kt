package com.barberia.feature.catalog.domain.repository

import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.TimeOff

/**
 * N2-b: citas reservadas que choca un día libre. Las citas son de booking; `:app` conecta las dos features.
 * [barberId] null = toda la barbería (día festivo).
 */
interface TimeOffAppointments {
    /** Cuántas citas activas caen en [off] (1 lectura por cita del día). */
    suspend fun affected(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Int>

    /** Las cancela y libera sus horarios; devuelve cuántas se cancelaron (las que ya empezaron se quedan). */
    suspend fun cancel(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Int>
}
