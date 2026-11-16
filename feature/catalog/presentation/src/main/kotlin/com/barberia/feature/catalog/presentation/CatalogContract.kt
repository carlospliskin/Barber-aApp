package com.barberia.feature.catalog.presentation

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import java.time.DayOfWeek

enum class CatalogTab { SERVICES, BARBERS }

data class DayDraft(val enabled: Boolean = false, val start: String = "10:00", val end: String = "19:00")

/** Formularios abiertos. Guardan texto tal cual se escribe; se convierte al guardar (C12). */
sealed interface CatalogDialog {
    val errors: Set<CatalogError>

    data class ServiceForm(
        val editingId: String? = null,
        val name: String = "",
        val duration: String = "30",
        val price: String = "",
        override val errors: Set<CatalogError> = emptySet(),
    ) : CatalogDialog

    data class BarberForm(
        val editingId: String? = null,
        val name: String = "",
        override val errors: Set<CatalogError> = emptySet(),
    ) : CatalogDialog

    data class Schedule(
        val barberId: String,
        val barberName: String,
        val days: Map<DayOfWeek, DayDraft>,
        override val errors: Set<CatalogError> = emptySet(),
    ) : CatalogDialog
}

data class CatalogState(
    val tenantId: String? = null,
    /** C11: solo el dueño edita; el resto lo ve en solo lectura. */
    val canEdit: Boolean = false,
    val catalog: Catalog = Catalog.empty(),
    val tab: CatalogTab = CatalogTab.SERVICES,
    val dialog: CatalogDialog? = null,
    val isSaving: Boolean = false,
    val failure: CatalogFailure? = null,
) : UiState

sealed interface CatalogIntent : UiIntent {
    data class TabSelected(val tab: CatalogTab) : CatalogIntent

    data object OpenNewService : CatalogIntent
    data class OpenEditService(val id: String) : CatalogIntent
    data class ServiceNameChanged(val value: String) : CatalogIntent
    data class ServiceDurationChanged(val value: String) : CatalogIntent
    data class ServicePriceChanged(val value: String) : CatalogIntent
    data class SetServiceActive(val id: String, val active: Boolean) : CatalogIntent

    data object OpenNewBarber : CatalogIntent
    data class OpenEditBarber(val id: String) : CatalogIntent
    data class BarberNameChanged(val value: String) : CatalogIntent
    data class SetBarberActive(val id: String, val active: Boolean) : CatalogIntent

    data class OpenSchedule(val barberId: String) : CatalogIntent
    data class DayToggled(val day: DayOfWeek, val enabled: Boolean) : CatalogIntent
    data class DayStartChanged(val day: DayOfWeek, val value: String) : CatalogIntent
    data class DayEndChanged(val day: DayOfWeek, val value: String) : CatalogIntent

    data object SaveDialog : CatalogIntent
    data object DismissDialog : CatalogIntent
    data object DismissFailure : CatalogIntent
}

sealed interface CatalogEffect : UiEffect
