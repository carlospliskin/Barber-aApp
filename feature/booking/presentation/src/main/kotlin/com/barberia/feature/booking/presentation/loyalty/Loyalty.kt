package com.barberia.feature.booking.presentation.loyalty

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyLoyaltyUseCase
import com.barberia.feature.booking.presentation.R
import com.barberia.feature.booking.presentation.clients.dayLabel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel

data class LoyaltyState(
    val visible: Boolean = false,
    val visitsTowardNext: Int = 0,
    val visitsPerReward: Int = 10,
    val rewardsAvailable: Int = 0,
    val penalizedUntil: Instant? = null
) : UiState {
    /** U10: con penalización vigente no se entra al flujo de reserva. */
    val canBook: Boolean get() = penalizedUntil == null
}

sealed interface LoyaltyIntent : UiIntent
sealed interface LoyaltyEffect : UiEffect

/** P6: progreso de lealtad y penalización del cliente en su barbería. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoyaltyViewModel(
    observeUser: ObserveCurrentUserUseCase,
    observeJoined: ObserveJoinedBarbershopUseCase,
    observeMine: ObserveMyLoyaltyUseCase,
    private val loyalty: LoyaltyPolicy,
    private val clock: Clock
) : MviViewModel<LoyaltyState, LoyaltyIntent, LoyaltyEffect>(LoyaltyState(visitsPerReward = loyalty.visitsPerReward)) {

    init {
        launchInScope {
            observeUser().map { it?.uid }.distinctUntilChanged()
                .flatMapLatest { uid -> if (uid == null) emptyFlow() else observeJoined(uid).map { uid to it } }
                .flatMapLatest { (uid, joined) ->
                    val shop = (joined as? AppResult.Success)?.value
                    if (shop == null) flowOf(null) else observeMine(shop.id, uid).catch { } // sin tarjeta, sin cortar
                }
                .collect { c ->
                    setState {
                        copy(
                            visible = true,
                            visitsTowardNext = c?.visitsTowardNext(loyalty) ?: 0,
                            rewardsAvailable = c?.availableRewards(loyalty) ?: 0,
                            penalizedUntil = c?.penaltyUntil?.takeIf { c.isPenalized(clock.instant()) }
                        )
                    }
                }
        }
    }

    override suspend fun handleIntent(intent: LoyaltyIntent) = Unit
}

@Composable
fun LoyaltyCard(modifier: Modifier = Modifier, viewModel: LoyaltyViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    if (!s.visible) return
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            s.penalizedUntil?.let {
                Text(stringResource(R.string.loyalty_penalized, it.dayLabel()), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(R.string.loyalty_progress, s.visitsTowardNext, s.visitsPerReward),
                style = MaterialTheme.typography.titleSmall
            )
            LinearProgressIndicator(
                progress = { s.visitsTowardNext / s.visitsPerReward.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            if (s.rewardsAvailable > 0) {
                Text(stringResource(R.string.loyalty_reward, s.rewardsAvailable), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
