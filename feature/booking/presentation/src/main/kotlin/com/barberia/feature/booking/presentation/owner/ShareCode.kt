package com.barberia.feature.booking.presentation.owner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.usecase.EnsureShareCodeUseCase
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.common.messageRes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel

data class ShareCodeState(val code: String? = null, val failure: BookingFailure? = null) : UiState

sealed interface ShareCodeIntent : UiIntent
sealed interface ShareCodeEffect : UiEffect

/** R7: el dueño ve (y si no existe, se crea) el código de su barbería. */
class ShareCodeViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val ensure: EnsureShareCodeUseCase,
) : MviViewModel<ShareCodeState, ShareCodeIntent, ShareCodeEffect>(ShareCodeState()) {

    init {
        launchInScope {
            observeUser()
                // X1: solo con el correo verificado (las reglas lo exigen); reacciona cuando se verifica.
                .map { u -> u?.staffTenantId.takeIf { u?.staffRole == StaffRole.OWNER && u.emailVerified } }
                .distinctUntilChanged()
                .collect { tenantId ->
                    if (tenantId == null) return@collect
                    when (val r = ensure(tenantId)) {
                        is AppResult.Success -> setState { copy(code = r.value, failure = null) }
                        is AppResult.Failure -> setState { copy(failure = r.error) }
                    }
                }
        }
    }

    override suspend fun handleIntent(intent: ShareCodeIntent) = Unit
}

@Composable
fun ShareCodeCard(modifier: Modifier = Modifier, viewModel: ShareCodeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.booking_share_title), style = MaterialTheme.typography.titleMedium)
            Text(
                state.code ?: "······",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.booking_share_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        }
    }
}
