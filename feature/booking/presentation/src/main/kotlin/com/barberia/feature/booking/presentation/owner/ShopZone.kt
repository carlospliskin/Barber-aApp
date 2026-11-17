package com.barberia.feature.booking.presentation.owner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.ShopZones
import com.barberia.feature.booking.domain.usecase.ChangeShopZoneUseCase
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.messageRes
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel

data class ShopZoneState(
    val tenantId: String? = null,
    /** null mientras carga o si no es el dueño (no se muestra la tarjeta). */
    val zone: ZoneId? = null,
    val saving: Boolean = false,
    val failure: BookingFailure? = null
) : UiState

sealed interface ShopZoneIntent : UiIntent {
    data class Select(val zone: ZoneId) : ShopZoneIntent
}

sealed interface ShopZoneEffect : UiEffect

/** N5: el dueño elige la zona horaria de su barbería ("hoy", horarios y recordatorios salen de ahí). */
class ShopZoneViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val getShop: GetBarbershopUseCase,
    private val change: ChangeShopZoneUseCase
) : MviViewModel<ShopZoneState, ShopZoneIntent, ShopZoneEffect>(ShopZoneState()) {

    init {
        launchInScope {
            observeUser()
                .map { u -> u?.staffTenantId.takeIf { u?.staffRole == StaffRole.OWNER && u.emailVerified } }
                .distinctUntilChanged()
                .collect { tenantId ->
                    if (tenantId == null) return@collect setState { ShopZoneState() }
                    when (val r = getShop(tenantId)) {
                        is AppResult.Success -> setState { copy(tenantId = tenantId, zone = r.value?.zone, failure = null) }
                        is AppResult.Failure -> setState { copy(tenantId = tenantId, failure = r.error) }
                    }
                }
        }
    }

    override suspend fun handleIntent(intent: ShopZoneIntent) = when (intent) {
        is ShopZoneIntent.Select -> select(intent.zone)
    }

    private suspend fun select(zone: ZoneId) {
        val tenantId = state.value.tenantId ?: return
        setState { copy(saving = true, failure = null) }
        when (val r = change(tenantId, zone)) {
            is AppResult.Success -> setState { copy(zone = zone, saving = false) }
            is AppResult.Failure -> setState { copy(saving = false, failure = r.error) }
        }
    }
}

/** "Mexico City (UTC−06:00)": nombre de la ciudad de la zona y su diferencia actual. */
fun ZoneId.label(now: Instant = Instant.now()): String =
    id.substringAfterLast('/').replace('_', ' ') + " (UTC" + rules.getOffset(now).id.replace("Z", "±00:00") + ")"

@Composable
fun ShopZoneCard(modifier: Modifier = Modifier, viewModel: ShopZoneViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = state.zone ?: return
    var open by remember { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.shop_zone_title), style = MaterialTheme.typography.titleMedium)
            Text(zone.label())
            Box {
                TextButton(onClick = { open = true }, enabled = !state.saving) { Text(stringResource(R.string.shop_zone_change)) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ShopZones.ALLOWED.forEach { z ->
                        DropdownMenuItem(text = { Text(z.label()) }, onClick = {
                            open = false
                            viewModel.onIntent(ShopZoneIntent.Select(z))
                        })
                    }
                }
            }
            state.failure?.let {
                val msg = if (it == BookingFailure.HasActiveAppointments) R.string.shop_zone_has_upcoming else it.messageRes()
                Text(stringResource(msg), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
