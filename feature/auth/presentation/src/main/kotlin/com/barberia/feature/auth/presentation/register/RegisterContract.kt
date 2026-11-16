package com.barberia.feature.auth.presentation.register

import com.barberia.core.mvi.UiEffect
import com.barberia.core.mvi.UiIntent
import com.barberia.core.mvi.UiState
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError

enum class AccountType { CLIENT, OWNER }

data class RegisterState(
    val type: AccountType = AccountType.CLIENT,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val businessName: String = "",
    val phone: String = "",
    val isLoading: Boolean = false,
    val fieldErrors: Set<FieldError> = emptySet(),
    val failure: AuthFailure? = null,
) : UiState

sealed interface RegisterIntent : UiIntent {
    data class TypeChanged(val type: AccountType) : RegisterIntent
    data class EmailChanged(val value: String) : RegisterIntent
    data class PasswordChanged(val value: String) : RegisterIntent
    data class NameChanged(val value: String) : RegisterIntent
    data class BusinessNameChanged(val value: String) : RegisterIntent
    data class PhoneChanged(val value: String) : RegisterIntent
    data object Submit : RegisterIntent
    data object Back : RegisterIntent
}

sealed interface RegisterEffect : UiEffect {
    data object Close : RegisterEffect
}
