package com.barberia.feature.auth.presentation.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.designsystem.theme.BarberTheme
import com.barberia.core.mvi.CollectEffects
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.presentation.R
import com.barberia.feature.auth.presentation.common.messageRes
import com.barberia.feature.auth.presentation.common.textFor
import org.koin.androidx.compose.koinViewModel

@Composable
fun RegisterRoute(onClose: () -> Unit, viewModel: RegisterViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { if (it == RegisterEffect.Close) onClose() }
    RegisterScreen(state, viewModel::onIntent)
}

@Composable
fun RegisterScreen(state: RegisterState, onIntent: (RegisterIntent) -> Unit) {
    val enabled = !state.isLoading
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.auth_register_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AccountType.entries.forEachIndexed { i, type ->
                SegmentedButton(
                    selected = state.type == type,
                    onClick = { onIntent(RegisterIntent.TypeChanged(type)) },
                    shape = SegmentedButtonDefaults.itemShape(i, AccountType.entries.size),
                    enabled = enabled,
                ) {
                    Text(
                        stringResource(
                            if (type == AccountType.CLIENT) R.string.auth_type_client else R.string.auth_type_owner,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        BarberTextField(
            state.name, { onIntent(RegisterIntent.NameChanged(it)) }, stringResource(R.string.auth_name),
            enabled = enabled, error = state.fieldErrors.textFor(FieldError.NAME_REQUIRED),
        )
        if (state.type == AccountType.OWNER) {
            Spacer(Modifier.height(12.dp))
            BarberTextField(
                state.businessName, { onIntent(RegisterIntent.BusinessNameChanged(it)) },
                stringResource(R.string.auth_business_name),
                enabled = enabled, error = state.fieldErrors.textFor(FieldError.BUSINESS_NAME_REQUIRED),
            )
        }
        Spacer(Modifier.height(12.dp))
        BarberTextField(
            state.email, { onIntent(RegisterIntent.EmailChanged(it)) }, stringResource(R.string.auth_email),
            keyboardType = KeyboardType.Email, enabled = enabled,
            error = state.fieldErrors.textFor(FieldError.EMAIL_INVALID),
        )
        Spacer(Modifier.height(12.dp))
        BarberTextField(
            state.phone, { onIntent(RegisterIntent.PhoneChanged(it)) },
            stringResource(if (state.type == AccountType.OWNER) R.string.auth_phone_optional else R.string.auth_phone),
            keyboardType = KeyboardType.Phone, enabled = enabled,
            error = state.fieldErrors.textFor(FieldError.PHONE_INVALID),
        )
        Spacer(Modifier.height(12.dp))
        BarberTextField(
            state.password, { onIntent(RegisterIntent.PasswordChanged(it)) }, stringResource(R.string.auth_password),
            isPassword = true, enabled = enabled,
            error = state.fieldErrors.textFor(FieldError.PASSWORD_TOO_SHORT),
        )
        state.failure?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        BarberButton(stringResource(R.string.auth_register), { onIntent(RegisterIntent.Submit) }, isLoading = state.isLoading)
        TextButton(onClick = { onIntent(RegisterIntent.Back) }) { Text(stringResource(R.string.auth_back)) }
    }
}

@Preview
@Composable
private fun RegisterPreview() = BarberTheme {
    RegisterScreen(RegisterState(type = AccountType.OWNER, fieldErrors = setOf(FieldError.BUSINESS_NAME_REQUIRED))) {}
}
