package com.barberia.feature.catalog.presentation

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogException
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.ServiceDraft
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.usecase.AddBarberUseCase
import com.barberia.feature.catalog.domain.usecase.AddServiceUseCase
import com.barberia.feature.catalog.domain.usecase.AddTimeOffUseCase
import com.barberia.feature.catalog.domain.usecase.CreateBarberInviteUseCase
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.barberia.feature.catalog.domain.usecase.RemoveTimeOffUseCase
import com.barberia.feature.catalog.domain.usecase.RenameBarberUseCase
import com.barberia.feature.catalog.domain.usecase.RevokeBarberAccessUseCase
import com.barberia.feature.catalog.domain.usecase.SetBarberActiveUseCase
import com.barberia.feature.catalog.domain.usecase.SetBarberScheduleUseCase
import com.barberia.feature.catalog.domain.usecase.SetServiceActiveUseCase
import com.barberia.feature.catalog.domain.usecase.ShopCalendar
import com.barberia.feature.catalog.domain.usecase.UpdateServiceUseCase
import com.barberia.feature.catalog.presentation.CatalogDialog.BarberForm
import com.barberia.feature.catalog.presentation.CatalogDialog.Schedule
import com.barberia.feature.catalog.presentation.CatalogDialog.ServiceForm
import com.barberia.feature.catalog.presentation.CatalogDialog.TimeOffForm
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    observeUser: ObserveCurrentUserUseCase,
    observeCatalog: ObserveCatalogUseCase,
    private val addService: AddServiceUseCase,
    private val updateService: UpdateServiceUseCase,
    private val setServiceActive: SetServiceActiveUseCase,
    private val addBarber: AddBarberUseCase,
    private val renameBarber: RenameBarberUseCase,
    private val setBarberActive: SetBarberActiveUseCase,
    private val setSchedule: SetBarberScheduleUseCase,
    private val createInvite: CreateBarberInviteUseCase,
    private val addTimeOff: AddTimeOffUseCase,
    private val removeTimeOff: RemoveTimeOffUseCase,
    private val revokeAccess: RevokeBarberAccessUseCase,
    private val calendar: ShopCalendar
) : MviViewModel<CatalogState, CatalogIntent, CatalogEffect>(CatalogState()) {

    init {
        launchInScope {
            observeUser()
                .map { u -> u?.staffTenantId to (u?.staffRole == StaffRole.OWNER) }
                .distinctUntilChanged()
                .flatMapLatest { (tenantId, isOwner) ->
                    setState { copy(tenantId = tenantId, canEdit = isOwner && tenantId != null) }
                    // El catch va adentro: un error no corta las actualizaciones siguientes.
                    if (tenantId == null) {
                        emptyFlow()
                    } else {
                        observeCatalog(tenantId).catch { e ->
                            setState { copy(failure = (e as? CatalogException)?.failure ?: CatalogFailure.Unknown(e.message)) }
                        }
                    }
                }
                .collect { c -> setState { copy(catalog = c) } }
        }
    }

    override suspend fun handleIntent(intent: CatalogIntent) {
        when (intent) {
            is CatalogIntent.TabSelected -> setState { copy(tab = intent.tab) }

            CatalogIntent.DismissDialog -> setState { copy(dialog = null) }

            CatalogIntent.DismissFailure -> setState { copy(failure = null) }

            CatalogIntent.OpenNewService -> openIfEditable(ServiceForm())

            is CatalogIntent.OpenEditService -> currentState.catalog.services.find { it.id == intent.id }?.let {
                openIfEditable(ServiceForm(it.id, it.name, it.durationMinutes.toString(), it.price.toInput()))
            }

            is CatalogIntent.ServiceNameChanged -> editService { copy(name = intent.value, errors = errors - NAME_ERRORS) }

            is CatalogIntent.ServiceDurationChanged ->
                editService { copy(duration = intent.value, errors = errors - CatalogError.DURATION_INVALID) }

            is CatalogIntent.ServicePriceChanged ->
                editService { copy(price = intent.value, errors = errors - CatalogError.PRICE_INVALID) }

            is CatalogIntent.SetServiceActive ->
                save { t -> setServiceActive(t, intent.id, intent.active) }

            CatalogIntent.OpenNewBarber -> openIfEditable(BarberForm())

            is CatalogIntent.OpenEditBarber -> currentState.catalog.barbers.find { it.id == intent.id }?.let {
                openIfEditable(BarberForm(it.id, it.name))
            }

            is CatalogIntent.BarberNameChanged -> editBarber { copy(name = intent.value, errors = errors - NAME_ERRORS) }

            is CatalogIntent.SetBarberActive -> save { t -> setBarberActive(t, intent.id, intent.active) }

            is CatalogIntent.OpenSchedule -> currentState.catalog.barbers.find { it.id == intent.barberId }?.let { b ->
                val days = DayOfWeek.entries.associateWith { day ->
                    val ranges = b.schedule.days[day].orEmpty()
                    ranges.firstOrNull()?.let { DayDraft(true, it.startLabel, it.endLabel, extra = ranges.drop(1)) } ?: DayDraft()
                }
                openIfEditable(Schedule(b.id, b.name, days))
            }

            is CatalogIntent.DayToggled -> editDay(intent.day) { copy(enabled = intent.enabled) }

            is CatalogIntent.DayStartChanged -> editDay(intent.day) { copy(start = intent.value) }

            is CatalogIntent.DayEndChanged -> editDay(intent.day) { copy(end = intent.value) }

            is CatalogIntent.OpenTimeOff -> {
                val barber = intent.barberId?.let { id -> currentState.catalog.barbers.find { it.id == id } ?: return }
                val tenantId = currentState.tenantId ?: return
                openIfEditable(TimeOffForm(barber?.id, barber?.name, date = calendar.today(tenantId).toInput()))
            }

            is CatalogIntent.TimeOffDateChanged -> editTimeOff { copy(date = intent.value) }

            is CatalogIntent.TimeOffWholeDayToggled -> editTimeOff { copy(wholeDay = intent.wholeDay) }

            is CatalogIntent.TimeOffStartChanged -> editTimeOff { copy(start = intent.value) }

            is CatalogIntent.TimeOffEndChanged -> editTimeOff { copy(end = intent.value) }

            is CatalogIntent.RemoveTimeOff -> save { t -> removeTimeOff(t, intent.barberId, intent.off) }

            CatalogIntent.SaveDialog -> saveDialog()

            is CatalogIntent.InviteBarber -> invite(intent.barberId)

            is CatalogIntent.OpenRevokeAccess -> currentState.catalog.barbers.find { it.id == intent.barberId }?.let {
                openIfEditable(CatalogDialog.RevokeAccess(it.id, it.name))
            }
        }
    }

    private suspend fun invite(barberId: String) {
        val tenantId = currentState.tenantId
        if (!currentState.canEdit || tenantId == null) return
        setState { copy(isSaving = true, failure = null) }
        when (val r = createInvite(tenantId, barberId)) {
            is AppResult.Success -> setState {
                copy(isSaving = false, dialog = CatalogDialog.Invite(r.value.barberName, r.value.code, r.value.expiresAt))
            }

            is AppResult.Failure -> setState { copy(isSaving = false, failure = r.error) }
        }
    }

    private suspend fun revoke(d: CatalogDialog.RevokeAccess) {
        val tenantId = currentState.tenantId
        if (!currentState.canEdit || tenantId == null || currentState.isSaving) return
        setState { copy(isSaving = true, failure = null) }
        when (val r = revokeAccess(tenantId, d.barberId)) {
            is AppResult.Success -> setState { copy(isSaving = false, dialog = d.copy(revoked = r.value)) }
            is AppResult.Failure -> setState { copy(isSaving = false, dialog = null, failure = r.error) }
        }
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    private suspend fun saveDialog() {
        when (val d = currentState.dialog) {
            is ServiceForm -> {
                val minutes = parseMinutes(d.duration)
                val price = parsePrice(d.price)
                val formErrors = buildSet {
                    if (minutes == null) add(CatalogError.DURATION_INVALID)
                    if (price == null) add(CatalogError.PRICE_INVALID)
                }
                if (formErrors.isNotEmpty()) return setState { copy(dialog = d.copy(errors = formErrors)) }
                val draft = ServiceDraft(d.name, minutes!!, price!!)
                save { t -> d.editingId?.let { updateService(t, it, draft) } ?: addService(t, draft) }
            }

            is BarberForm -> save { t -> d.editingId?.let { renameBarber(t, it, d.name) } ?: addBarber(t, d.name) }

            is Schedule -> {
                val schedule = d.toSchedule()
                    ?: return setState { copy(dialog = d.copy(errors = setOf(CatalogError.SCHEDULE_RANGE_INVALID))) }
                save { t -> setSchedule(t, d.barberId, schedule) }
            }

            is TimeOffForm -> {
                val date = parseDate(d.date)
                    ?: return setState { copy(dialog = d.copy(errors = setOf(CatalogError.DATE_INVALID))) }
                val range = if (d.wholeDay) {
                    null
                } else {
                    val start = parseHour(d.start)
                    val end = parseHour(d.end)
                    if (start == null || end == null) {
                        return setState { copy(dialog = d.copy(errors = setOf(CatalogError.SCHEDULE_RANGE_INVALID))) }
                    }
                    TimeRange(start, end)
                }
                save { t -> addTimeOff(t, d.barberId, TimeOff(date, range)) }
            }

            is CatalogDialog.RevokeAccess -> if (d.revoked != null) setState { copy(dialog = null) } else revoke(d)

            is CatalogDialog.Invite, null -> setState { copy(dialog = null) }
        }
    }

    /** Ejecuta un guardado. Éxito: cierra el diálogo. Validación: errores en el diálogo. Otro: aviso general. */
    private suspend fun save(action: suspend (tenantId: String) -> CatalogResult<Catalog>) {
        val tenantId = currentState.tenantId
        if (!currentState.canEdit || tenantId == null || currentState.isSaving) return
        setState { copy(isSaving = true, failure = null) }
        val result = action(tenantId)
        setState {
            when (result) {
                is AppResult.Success -> copy(isSaving = false, dialog = null, catalog = result.value)

                is AppResult.Failure -> when (val f = result.error) {
                    is CatalogFailure.Validation -> copy(isSaving = false, dialog = dialog?.withErrors(f.errors))
                    else -> copy(isSaving = false, failure = f)
                }
            }
        }
    }

    // ── Edición de formularios ────────────────────────────────────────────────

    private fun openIfEditable(dialog: CatalogDialog) {
        if (currentState.canEdit) setState { copy(dialog = dialog, failure = null) }
    }

    private fun editService(change: ServiceForm.() -> ServiceForm) =
        setState { (dialog as? ServiceForm)?.let { copy(dialog = it.change()) } ?: this }

    private fun editBarber(change: BarberForm.() -> BarberForm) =
        setState { (dialog as? BarberForm)?.let { copy(dialog = it.change()) } ?: this }

    private fun editTimeOff(change: TimeOffForm.() -> TimeOffForm) =
        setState { (dialog as? TimeOffForm)?.let { copy(dialog = it.change().copy(errors = emptySet())) } ?: this }

    private fun editDay(day: DayOfWeek, change: DayDraft.() -> DayDraft) = setState {
        (dialog as? Schedule)?.let {
            copy(dialog = it.copy(days = it.days + (day to it.days.getValue(day).change()), errors = emptySet()))
        } ?: this
    }

    private fun Schedule.toSchedule(): WeeklySchedule? {
        val ranges = days.filterValues { it.enabled }.mapValues { (_, d) ->
            val start = parseHour(d.start) ?: return null
            val end = parseHour(d.end) ?: return null
            listOf(TimeRange(start, end)) + d.extra
        }
        return WeeklySchedule(ranges)
    }

    private fun CatalogDialog.withErrors(errors: Set<CatalogError>): CatalogDialog = when (this) {
        is ServiceForm -> copy(errors = errors)
        is BarberForm -> copy(errors = errors)
        is Schedule -> copy(errors = errors)
        is CatalogDialog.Invite -> copy(errors = errors)
        is CatalogDialog.RevokeAccess -> copy(errors = errors)
        is TimeOffForm -> copy(errors = errors)
    }

    private companion object {
        val NAME_ERRORS = setOf(CatalogError.NAME_REQUIRED, CatalogError.NAME_TOO_LONG, CatalogError.NAME_DUPLICATED)
    }
}
