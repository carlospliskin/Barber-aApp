package com.barberia.feature.booking.presentation.invite

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.RetrySessionUseCase
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.usecase.RedeemBarberInviteUseCase
import org.koin.androidx.compose.koinViewModel

data class RedeemInviteState(
    val uid: String? = null,
    val isStaff: Boolean = false,
    val code: String = "",
    val busy: Boolean = false,
    val joinedAs: String? = null,
    val failure: CatalogFailure? = null
) : UiState

sealed interface RedeemInviteIntent : UiIntent {
    data class CodeChanged(val value: String) : RedeemInviteIntent
    data object Redeem : RedeemInviteIntent
}

sealed interface RedeemInviteEffect : UiEffect

/** A1/A9: un cliente acepta la invitación de su barbería y la sesión se refresca (aparece el modo Barbero). */
class RedeemInviteViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val redeem: RedeemBarberInviteUseCase,
    private val refreshSession: RetrySessionUseCase
) : MviViewModel<RedeemInviteState, RedeemInviteIntent, RedeemInviteEffect>(RedeemInviteState()) {

    init {
        launchInScope {
            observeUser().collect { u -> setState { copy(uid = u?.uid, isStaff = u?.staffRole != null) } }
        }
    }

    override suspend fun handleIntent(intent: RedeemInviteIntent) {
        when (intent) {
            is RedeemInviteIntent.CodeChanged -> setState { copy(code = intent.value.uppercase(), failure = null) }

            RedeemInviteIntent.Redeem -> {
                val uid = currentState.uid ?: return
                setState { copy(busy = true, failure = null) }
                when (val r = redeem(currentState.code, uid, currentState.isStaff)) {
                    is AppResult.Success -> {
                        setState { copy(busy = false, joinedAs = r.value.barberName, code = "") }
                        refreshSession() // A9: el perfil ahora es BARBER → la sesión lleva al selector de modo
                    }

                    is AppResult.Failure -> setState { copy(busy = false, failure = r.error) }
                }
            }
        }
    }
}

@Composable
fun RedeemInviteCard(modifier: Modifier = Modifier, viewModel: RedeemInviteViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.isStaff && state.joinedAs == null) return
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleMedium)
            state.joinedAs?.let {
                Text(stringResource(R.string.invite_done, it), color = MaterialTheme.colorScheme.primary)
                return@Column
            }
            Text(stringResource(R.string.invite_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            BarberTextField(
                state.code,
                { viewModel.onIntent(RedeemInviteIntent.CodeChanged(it)) },
                stringResource(R.string.invite_code),
                error = state.failure?.let { stringResource(it.inviteMessageRes()) }
            )
            BarberButton(stringResource(R.string.invite_accept), { viewModel.onIntent(RedeemInviteIntent.Redeem) }, isLoading = state.busy)
        }
    }
}

private fun CatalogFailure.inviteMessageRes(): Int = when (this) {
    CatalogFailure.InviteExpired -> R.string.invite_error_expired
    CatalogFailure.AlreadyStaff -> R.string.invite_error_staff
    CatalogFailure.NoConnection -> R.string.booking_error_connection
    else -> R.string.invite_error_invalid
}
