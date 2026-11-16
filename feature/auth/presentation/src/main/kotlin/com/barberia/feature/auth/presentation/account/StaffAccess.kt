package com.barberia.feature.auth.presentation.account

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
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.StaffAccess
import com.barberia.feature.auth.domain.usecase.CheckStaffAccessUseCase
import com.barberia.feature.auth.domain.usecase.LeaveStaffUseCase
import com.barberia.feature.auth.presentation.R
import com.barberia.feature.auth.presentation.common.messageRes
import org.koin.androidx.compose.koinViewModel

data class StaffAccessState(val access: StaffAccess? = null, val leaving: Boolean = false, val failure: AuthFailure? = null) : UiState

sealed interface StaffAccessIntent : UiIntent {
    data object Leave : StaffAccessIntent
}

sealed interface StaffAccessEffect : UiEffect

/** A7-b/A16: al abrir el home de barbero revisa (2 lecturas) si la barbería existe y si sigue con acceso. */
class StaffAccessViewModel(private val check: CheckStaffAccessUseCase, private val leave: LeaveStaffUseCase) :
    MviViewModel<StaffAccessState, StaffAccessIntent, StaffAccessEffect>(StaffAccessState()) {

    init {
        launchInScope {
            val r = check()
            setState { if (r is AppResult.Success) copy(access = r.value) else this } // sin red: no se molesta
        }
    }

    override suspend fun handleIntent(intent: StaffAccessIntent) {
        when (intent) {
            StaffAccessIntent.Leave -> {
                if (currentState.leaving) return
                setState { copy(leaving = true, failure = null) }
                val r = leave() // si sale bien, la sesión cambia y la app va al home de cliente
                setState { copy(leaving = false, failure = (r as? AppResult.Failure)?.error) }
            }
        }
    }
}

/** No dibuja nada mientras el acceso esté vigente. */
@Composable
fun StaffAccessCard(modifier: Modifier = Modifier, viewModel: StaffAccessViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val message = when (s.access) {
        StaffAccess.REVOKED -> R.string.staff_access_revoked
        StaffAccess.SHOP_GONE -> R.string.staff_access_shop_gone
        StaffAccess.ACTIVE, null -> return
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.onErrorContainer)
            s.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
            BarberButton(stringResource(R.string.staff_access_leave), {
                viewModel.onIntent(StaffAccessIntent.Leave)
            }, isLoading = s.leaving)
        }
    }
}
