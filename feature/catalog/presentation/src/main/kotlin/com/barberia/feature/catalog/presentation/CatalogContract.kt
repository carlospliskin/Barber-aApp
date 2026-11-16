package com.barberia.feature.catalog.presentation

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import java.time.DayOfWeek

enum class CatalogTab { SERVICES, BARBERS }

/**
 * Un día del formulario: se edita el primer turno. [extra] guarda los demás turnos del día (turno
 * partido) para no perderlos al guardar (G1).
 */
data class DayDraft(
    val enabled: Boolean = false,
    val start: String = "10:00",
    val end: String = "19:00",
    val extra: List<TimeRange> = emptyList()
)

/** Formularios abiertos. Guardan texto tal cual se escribe; se convierte al guardar (C12). */
sealed interface CatalogDialog {
    val errors: Set<CatalogError>

    data class ServiceForm(
        val editingId: String? = null,
        val name: String = "",
        val duration: String = "30",
        val price: String = "",
        override val errors: Set<CatalogError> = emptySet()
    ) : CatalogDialog

    data class BarberForm(val editingId: String? = null, val name: String = "", override val errors: Set<CatalogError> = emptySet()) :
        CatalogDialog

    /** A1: código para mandarle al barbero. */
    data class Invite(
        val barberName: String,
        val code: String,
        val expiresAt: java.time.Instant,
        override val errors: Set<CatalogError> = emptySet()
    ) : CatalogDialog

    /**
     * A16: quitar el acceso de la app a las cuentas de un barbero. [revoked] null = pidiendo confirmación;
     * con número = ya se hizo (cuántas cuentas).
     */
    data class RevokeAccess(
        val barberId: String,
        val barberName: String,
        val revoked: Int? = null,
        override val errors: Set<CatalogError> = emptySet()
    ) : CatalogDialog

    /** N2: día libre de un barbero, o de todos si [barberId] es null (día festivo). Fecha "dd/MM/aaaa". */
    data class TimeOffForm(
        val barberId: String?,
        val barberName: String?,
        val date: String,
        val wholeDay: Boolean = true,
        val start: String = "14:00",
        val end: String = "20:00",
        override val errors: Set<CatalogError> = emptySet()
    ) : CatalogDialog

    data class Schedule(
        val barberId: String,
        val barberName: String,
        val days: Map<DayOfWeek, DayDraft>,
        override val errors: Set<CatalogError> = emptySet()
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
    val failure: CatalogFailure? = null
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
    data class InviteBarber(val barberId: String) : CatalogIntent
    data class OpenRevokeAccess(val barberId: String) : CatalogIntent
    data class DayToggled(val day: DayOfWeek, val enabled: Boolean) : CatalogIntent
    data class DayStartChanged(val day: DayOfWeek, val value: String) : CatalogIntent
    data class DayEndChanged(val day: DayOfWeek, val value: String) : CatalogIntent

    data class OpenTimeOff(val barberId: String?) : CatalogIntent
    data class TimeOffDateChanged(val value: String) : CatalogIntent
    data class TimeOffWholeDayToggled(val wholeDay: Boolean) : CatalogIntent
    data class TimeOffStartChanged(val value: String) : CatalogIntent
    data class TimeOffEndChanged(val value: String) : CatalogIntent
    data class RemoveTimeOff(val barberId: String, val off: TimeOff) : CatalogIntent

    data object SaveDialog : CatalogIntent
    data object DismissDialog : CatalogIntent
    data object DismissFailure : CatalogIntent
}

sealed interface CatalogEffect : UiEffect
