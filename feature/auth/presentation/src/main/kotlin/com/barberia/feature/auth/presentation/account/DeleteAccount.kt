package com.barberia.feature.auth.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.common.AppResult
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.mvi.MviViewModel
import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.usecase.DeleteAccountUseCase
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.presentation.R
import com.barberia.feature.auth.presentation.common.messageRes
import com.barberia.feature.auth.presentation.common.splitValidation
import org.koin.androidx.compose.koinViewModel

data class DeleteAccountState(
    val email: String = "",
    val isOwner: Boolean = false,
    val password: String = "",
    val understood: Boolean = false,
    val deleting: Boolean = false,
    val fieldErrors: Set<FieldError> = emptySet(),
    val failure: AuthFailure? = null
) : UiState {
    val canConfirm: Boolean get() = understood && !deleting
}

sealed interface DeleteAccountIntent : UiIntent {
    data class PasswordChanged(val value: String) : DeleteAccountIntent
    data object UnderstoodToggled : DeleteAccountIntent
    data object Confirm : DeleteAccountIntent
}

sealed interface DeleteAccountEffect : UiEffect

/** A7: al terminar, la sesión se cierra sola y la navegación vuelve al login. */
class DeleteAccountViewModel(observeUser: ObserveCurrentUserUseCase, private val deleteAccount: DeleteAccountUseCase) :
    MviViewModel<DeleteAccountState, DeleteAccountIntent, DeleteAccountEffect>(DeleteAccountState()) {

    init {
        launchInScope {
            observeUser().collect { u ->
                if (u != null) setState { copy(email = u.email, isOwner = u.staffRole == StaffRole.OWNER) }
            }
        }
    }

    override suspend fun handleIntent(intent: DeleteAccountIntent) {
        when (intent) {
            is DeleteAccountIntent.PasswordChanged ->
                setState { copy(password = intent.value, fieldErrors = emptySet(), failure = null) }

            DeleteAccountIntent.UnderstoodToggled -> setState { copy(understood = !understood) }

            DeleteAccountIntent.Confirm -> confirm()
        }
    }

    private suspend fun confirm() {
        if (!currentState.canConfirm) return
        setState { copy(deleting = true, fieldErrors = emptySet(), failure = null) }
        val r = deleteAccount(currentState.password)
        setState {
            when (r) {
                is AppResult.Success -> copy(deleting = false, password = "")

                is AppResult.Failure -> {
                    val (fields, general) = r.error.splitValidation()
                    copy(deleting = false, fieldErrors = fields, failure = general)
                }
            }
        }
    }
}

@Composable
fun DeleteAccountRoute(onBack: () -> Unit, viewModel: DeleteAccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeleteAccountScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun DeleteAccountScreen(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.delete_account_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.delete_account_what, state.email))
        Text(stringResource(R.string.delete_account_appointments), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.isOwner) {
            Text(stringResource(R.string.delete_account_owner), color = MaterialTheme.colorScheme.error)
        }
        // Toda la fila se toca (no solo la casilla): más fácil y accesible.
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = state.understood,
                enabled = !state.deleting,
                role = Role.Checkbox,
                onValueChange = { onIntent(DeleteAccountIntent.UnderstoodToggled) }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = state.understood, onCheckedChange = null)
            Text(stringResource(R.string.delete_account_understood))
        }
        BarberTextField(
            state.password,
            { onIntent(DeleteAccountIntent.PasswordChanged(it)) },
            stringResource(R.string.delete_account_password),
            isPassword = true,
            enabled = !state.deleting,
            error = state.fieldErrors.firstOrNull()?.let { stringResource(it.messageRes()) }
        )
        state.failure?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        BarberButton(
            stringResource(R.string.delete_account_confirm),
            { onIntent(DeleteAccountIntent.Confirm) },
            enabled = state.canConfirm,
            isLoading = state.deleting
        )
        if (state.deleting) Text(stringResource(R.string.delete_account_wait), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onBack, enabled = !state.deleting) { Text(stringResource(R.string.delete_account_cancel)) }
    }
}
