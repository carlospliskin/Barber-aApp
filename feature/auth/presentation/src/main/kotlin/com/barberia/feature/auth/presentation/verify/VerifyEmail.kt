package com.barberia.feature.auth.presentation.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.usecase.CheckEmailVerifiedUseCase
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.ResendVerificationEmailUseCase
import com.barberia.feature.auth.presentation.R
import com.barberia.feature.auth.presentation.common.messageRes
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class VerifyEmailState(
    val email: String = "",
    val sent: Boolean = false,
    val lastSentAt: Instant? = null,
    val checking: Boolean = false,
    val notYet: Boolean = false,
    val failure: AuthFailure? = null,
) : UiState

sealed interface VerifyEmailIntent : UiIntent {
    data object Resend : VerifyEmailIntent
    data object Check : VerifyEmailIntent
}

sealed interface VerifyEmailEffect : UiEffect

/** A14: el dueño confirma su correo. Al verificarse, la sesión se refresca y el home se desbloquea. */
class VerifyEmailViewModel(
    observeUser: ObserveCurrentUserUseCase,
    private val resend: ResendVerificationEmailUseCase,
    private val check: CheckEmailVerifiedUseCase,
    private val clock: Clock,
) : MviViewModel<VerifyEmailState, VerifyEmailIntent, VerifyEmailEffect>(VerifyEmailState()) {

    init {
        launchInScope { observeUser().collect { u -> setState { copy(email = u?.email.orEmpty()) } } }
    }

    override suspend fun handleIntent(intent: VerifyEmailIntent) {
        when (intent) {
            VerifyEmailIntent.Resend -> {
                val last = currentState.lastSentAt
                // Evita gastar el límite diario de correos (1000/día en Spark) con toques repetidos.
                if (last != null && Duration.between(last, clock.instant()) < COOLDOWN) return
                val r = resend()
                setState {
                    when (r) {
                        is AppResult.Success -> copy(sent = true, lastSentAt = clock.instant(), failure = null)
                        is AppResult.Failure -> copy(failure = r.error)
                    }
                }
            }
            VerifyEmailIntent.Check -> {
                setState { copy(checking = true, notYet = false, failure = null) }
                val r = check()
                setState {
                    when (r) {
                        is AppResult.Success -> copy(checking = false, notYet = !r.value)
                        is AppResult.Failure -> copy(checking = false, failure = r.error)
                    }
                }
            }
        }
    }

    private companion object {
        val COOLDOWN: Duration = Duration.ofSeconds(60)
    }
}

@Composable
fun VerifyEmailCard(modifier: Modifier = Modifier, viewModel: VerifyEmailViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.verify_hint, s.email), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.sent) Text(stringResource(R.string.verify_sent), color = MaterialTheme.colorScheme.primary)
            if (s.notYet) Text(stringResource(R.string.verify_not_yet), color = MaterialTheme.colorScheme.error)
            s.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
            BarberButton(stringResource(R.string.verify_check), { viewModel.onIntent(VerifyEmailIntent.Check) }, isLoading = s.checking)
            Row { TextButton(onClick = { viewModel.onIntent(VerifyEmailIntent.Resend) }) { Text(stringResource(R.string.verify_resend)) } }
        }
    }
}
