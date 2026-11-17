package com.barberia.feature.catalog.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.designsystem.theme.BarberTheme
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.presentation.CatalogDialog.BarberForm
import com.barberia.feature.catalog.presentation.CatalogDialog.Schedule
import com.barberia.feature.catalog.presentation.CatalogDialog.ServiceForm
import org.koin.androidx.compose.koinViewModel

@Composable
fun CatalogRoute(onBack: () -> Unit, viewModel: CatalogViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogScreen(state, viewModel::onIntent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(state: CatalogState, onIntent: (CatalogIntent) -> Unit, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.failure) {
        state.failure?.let {
            snackbar.showSnackbar(context.getString(it.messageRes()))
            onIntent(CatalogIntent.DismissFailure)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.catalog_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.canEdit) {
                val services = state.tab == CatalogTab.SERVICES
                val label = stringResource(if (services) R.string.catalog_add_service else R.string.catalog_add_barber)
                ExtendedFloatingActionButton(
                    onClick = { onIntent(if (services) CatalogIntent.OpenNewService else CatalogIntent.OpenNewBarber) },
                    text = { Text(label) },
                    icon = { Text("+", Modifier.clearAndSetSemantics { }) },
                    // C13: TalkBack anunciaba solo "+".
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                CatalogTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onIntent(CatalogIntent.TabSelected(tab)) },
                        text = {
                            Text(
                                stringResource(
                                    if (tab ==
                                        CatalogTab.SERVICES
                                    ) {
                                        R.string.catalog_tab_services
                                    } else {
                                        R.string.catalog_tab_barbers
                                    }
                                )
                            )
                        }
                    )
                }
            }
            if (!state.canEdit && state.tenantId != null) {
                Text(
                    stringResource(R.string.catalog_read_only),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            when (state.tab) {
                CatalogTab.SERVICES -> ServicesList(state.catalog.services, state.canEdit, onIntent)
                CatalogTab.BARBERS -> BarbersList(state.catalog.barbers, state.canEdit, onIntent)
            }
        }
    }
    when (val d = state.dialog) {
        is ServiceForm -> ServiceDialog(d, state.isSaving, onIntent)

        is BarberForm -> BarberDialog(d, state.isSaving, onIntent)

        is Schedule -> ScheduleDialog(d, state.isSaving, onIntent)

        is CatalogDialog.TimeOffForm -> TimeOffDialog(d, state.isSaving, onIntent)

        is CatalogDialog.RevokeAccess -> AlertDialog(
            onDismissRequest = { onIntent(CatalogIntent.DismissDialog) },
            title = { Text(stringResource(R.string.catalog_revoke_title, d.barberName)) },
            text = {
                Text(
                    when (d.revoked) {
                        null -> stringResource(R.string.catalog_revoke_hint)
                        0 -> stringResource(R.string.catalog_revoke_none)
                        else -> pluralStringResource(R.plurals.catalog_revoke_done, d.revoked, d.revoked)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(CatalogIntent.SaveDialog) }, enabled = !state.isSaving) {
                    Text(stringResource(if (d.revoked == null) R.string.catalog_revoke_confirm else R.string.catalog_done))
                }
            },
            dismissButton = if (d.revoked == null) {
                { TextButton(onClick = { onIntent(CatalogIntent.DismissDialog) }) { Text(stringResource(R.string.catalog_cancel)) } }
            } else {
                null
            }
        )

        // N2-b: el día libre ya quedó; ¿qué pasa con las citas de ese día?
        is CatalogDialog.TimeOffConflicts -> AlertDialog(
            onDismissRequest = { onIntent(CatalogIntent.DismissDialog) },
            title = {
                Text(
                    if (d.cancelled == null) {
                        pluralStringResource(R.plurals.catalog_timeoff_conflicts_title, d.affected, d.affected)
                    } else {
                        stringResource(R.string.catalog_timeoff_saved)
                    }
                )
            },
            text = {
                Text(
                    when (d.cancelled) {
                        null -> stringResource(R.string.catalog_timeoff_conflicts_hint)
                        else -> pluralStringResource(R.plurals.catalog_timeoff_conflicts_done, d.cancelled, d.cancelled)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(CatalogIntent.SaveDialog) }, enabled = !state.isSaving) {
                    Text(
                        if (d.cancelled == null) {
                            pluralStringResource(R.plurals.catalog_timeoff_conflicts_cancel, d.affected, d.affected)
                        } else {
                            stringResource(R.string.catalog_done)
                        }
                    )
                }
            },
            dismissButton = if (d.cancelled == null) {
                {
                    TextButton(onClick = {
                        onIntent(CatalogIntent.DismissDialog)
                    }) { Text(stringResource(R.string.catalog_timeoff_conflicts_keep)) }
                }
            } else {
                null
            }
        )

        is CatalogDialog.Invite -> AlertDialog(
            onDismissRequest = { onIntent(CatalogIntent.DismissDialog) },
            title = { Text(stringResource(R.string.catalog_invite_title, d.barberName)) },
            text = {
                Column {
                    Text(d.code, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.catalog_invite_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(CatalogIntent.DismissDialog) }) { Text(stringResource(R.string.catalog_done)) }
            }
        )

        null -> Unit
    }
}

@Composable
private fun ServicesList(services: List<Service>, canEdit: Boolean, onIntent: (CatalogIntent) -> Unit) {
    if (services.isEmpty()) return EmptyText(R.string.catalog_empty_services)
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(services, key = { it.id }) { s ->
            ListItem(
                headlineContent = { Text(s.name) },
                supportingContent = { Text(stringResource(R.string.catalog_service_line, s.durationMinutes, s.price.label)) },
                trailingContent = {
                    Switch(s.active, { onIntent(CatalogIntent.SetServiceActive(s.id, it)) }, enabled = canEdit)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(enabled = canEdit) { onIntent(CatalogIntent.OpenEditService(s.id)) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun BarbersList(barbers: List<Barber>, canEdit: Boolean, onIntent: (CatalogIntent) -> Unit) {
    if (barbers.isEmpty()) return EmptyText(R.string.catalog_empty_barbers)
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        if (canEdit) {
            item(key = "close-shop") {
                TextButton(onClick = { onIntent(CatalogIntent.OpenTimeOff(null)) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.catalog_close_shop))
                }
            }
        }
        items(barbers, key = { it.id }) { b ->
            ListItem(
                headlineContent = { Text(b.name) },
                supportingContent = {
                    Column {
                        val days = b.schedule.days.size
                        Text(
                            if (days == 0) {
                                stringResource(R.string.catalog_no_schedule)
                            } else {
                                pluralStringResource(R.plurals.catalog_days_working, days, days)
                            },
                            color = if (days == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // N2: días libres próximos
                        b.timeOff.forEach { off ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(off.label(), Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                                if (canEdit) {
                                    TextButton(onClick = { onIntent(CatalogIntent.RemoveTimeOff(b.id, off)) }) {
                                        Text(stringResource(R.string.catalog_time_off_remove))
                                    }
                                }
                            }
                        }
                        if (canEdit && b.active) {
                            TextButton(onClick = { onIntent(CatalogIntent.OpenTimeOff(b.id)) }) {
                                Text("+ " + stringResource(R.string.catalog_time_off))
                            }
                        }
                        // A16: también para un barbero desactivado (renunció o lo despidieron)
                        if (canEdit) {
                            TextButton(onClick = { onIntent(CatalogIntent.OpenRevokeAccess(b.id)) }) {
                                Text(stringResource(R.string.catalog_revoke), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onIntent(CatalogIntent.OpenSchedule(b.id)) }, enabled = canEdit) {
                            Text(stringResource(R.string.catalog_schedule))
                        }
                        TextButton(onClick = { onIntent(CatalogIntent.InviteBarber(b.id)) }, enabled = canEdit && b.active) {
                            Text(stringResource(R.string.catalog_invite))
                        }
                        Switch(b.active, { onIntent(CatalogIntent.SetBarberActive(b.id, it)) }, enabled = canEdit)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(enabled = canEdit) { onIntent(CatalogIntent.OpenEditBarber(b.id)) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyText(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun Set<CatalogError>.textFor(vararg errors: CatalogError): String? =
    errors.firstOrNull { it in this }?.let { stringResource(it.messageRes()) }

@Composable
private fun FormDialog(title: String, isSaving: Boolean, onIntent: (CatalogIntent) -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(CatalogIntent.DismissDialog) },
        title = { Text(title) },
        text = { content() },
        confirmButton = {
            TextButton(onClick = { onIntent(CatalogIntent.SaveDialog) }, enabled = !isSaving) {
                Text(stringResource(R.string.catalog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(CatalogIntent.DismissDialog) }) { Text(stringResource(R.string.catalog_cancel)) }
        }
    )
}

@Composable
private fun ServiceDialog(form: ServiceForm, isSaving: Boolean, onIntent: (CatalogIntent) -> Unit) {
    FormDialog(stringResource(R.string.catalog_service), isSaving, onIntent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarberTextField(
                form.name,
                { onIntent(CatalogIntent.ServiceNameChanged(it)) },
                stringResource(R.string.catalog_service_name),
                error = form.errors.textFor(
                    CatalogError.NAME_REQUIRED,
                    CatalogError.NAME_TOO_LONG,
                    CatalogError.NAME_DUPLICATED,
                    CatalogError.TOO_MANY_ITEMS
                )
            )
            BarberTextField(
                form.duration,
                { onIntent(CatalogIntent.ServiceDurationChanged(it)) },
                stringResource(R.string.catalog_duration),
                keyboardType = KeyboardType.Number,
                error = form.errors.textFor(CatalogError.DURATION_INVALID)
            )
            BarberTextField(
                form.price,
                { onIntent(CatalogIntent.ServicePriceChanged(it)) },
                stringResource(R.string.catalog_price),
                keyboardType = KeyboardType.Decimal,
                error = form.errors.textFor(CatalogError.PRICE_INVALID)
            )
        }
    }
}

@Composable
private fun BarberDialog(form: BarberForm, isSaving: Boolean, onIntent: (CatalogIntent) -> Unit) {
    FormDialog(stringResource(R.string.catalog_barber), isSaving, onIntent) {
        BarberTextField(
            form.name,
            { onIntent(CatalogIntent.BarberNameChanged(it)) },
            stringResource(R.string.catalog_barber_name),
            error = form.errors.textFor(
                CatalogError.NAME_REQUIRED,
                CatalogError.NAME_TOO_LONG,
                CatalogError.NAME_DUPLICATED,
                CatalogError.TOO_MANY_ITEMS
            )
        )
    }
}

@Composable
private fun ScheduleDialog(form: Schedule, isSaving: Boolean, onIntent: (CatalogIntent) -> Unit) {
    FormDialog(stringResource(R.string.catalog_schedule_of, form.barberName), isSaving, onIntent) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            form.errors.textFor(CatalogError.SCHEDULE_RANGE_INVALID, CatalogError.SCHEDULE_OVERLAP)?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            form.days.toSortedMap().forEach { (day, draft) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.label(), Modifier.weight(1f))
                    Switch(draft.enabled, { onIntent(CatalogIntent.DayToggled(day, it)) })
                }
                if (draft.enabled) {
                    draft.extra.forEach { r ->
                        Text(
                            stringResource(R.string.catalog_extra_shift, r.startLabel, r.endLabel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        BarberTextField(
                            draft.start,
                            { onIntent(CatalogIntent.DayStartChanged(day, it)) },
                            stringResource(R.string.catalog_from),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        BarberTextField(
                            draft.end,
                            { onIntent(CatalogIntent.DayEndChanged(day, it)) },
                            stringResource(R.string.catalog_to),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeOff.label(): String {
    val date = date.toInput()
    val r = range
    return if (r == null) {
        stringResource(R.string.catalog_time_off_line_day, date)
    } else {
        stringResource(R.string.catalog_time_off_line_range, date, r.startLabel, r.endLabel)
    }
}

@Composable
private fun TimeOffDialog(form: CatalogDialog.TimeOffForm, isSaving: Boolean, onIntent: (CatalogIntent) -> Unit) {
    val title = form.barberName?.let { stringResource(R.string.catalog_time_off_of, it) } ?: stringResource(R.string.catalog_close_shop)
    FormDialog(title, isSaving, onIntent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (form.barberId ==
                null
            ) {
                Text(stringResource(R.string.catalog_close_shop_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BarberTextField(
                form.date,
                { onIntent(CatalogIntent.TimeOffDateChanged(it)) },
                stringResource(R.string.catalog_time_off_date),
                error = form.errors.textFor(CatalogError.DATE_INVALID, CatalogError.TIME_OFF_PAST, CatalogError.TOO_MANY_ITEMS)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.catalog_time_off_whole_day), Modifier.weight(1f))
                Switch(form.wholeDay, { onIntent(CatalogIntent.TimeOffWholeDayToggled(it)) })
            }
            if (!form.wholeDay) {
                form.errors.textFor(CatalogError.SCHEDULE_RANGE_INVALID)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth()) {
                    BarberTextField(
                        form.start,
                        { onIntent(CatalogIntent.TimeOffStartChanged(it)) },
                        stringResource(R.string.catalog_from),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    BarberTextField(
                        form.end,
                        { onIntent(CatalogIntent.TimeOffEndChanged(it)) },
                        stringResource(R.string.catalog_to),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(stringResource(R.string.catalog_time_off_existing), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview
@Composable
private fun CatalogPreview() = BarberTheme {
    CatalogScreen(
        CatalogState(
            tenantId = "t",
            canEdit = true,
            catalog = Catalog(
                listOf(Service("1", "Corte clásico", 30, Money(15_000)), Service("2", "Barba", 20, Money(9_950), active = false)),
                emptyList(),
                2
            )
        ),
        {},
        {}
    )
}
