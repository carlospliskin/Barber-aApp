package com.barberia.feature.auth.presentation.register

import com.barberia.core.common.AppResult
import com.barberia.core.mvi.MviViewModel
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.repository.ClientRegistration
import com.barberia.feature.auth.domain.repository.OwnerRegistration
import com.barberia.feature.auth.domain.usecase.RegisterClientUseCase
import com.barberia.feature.auth.domain.usecase.RegisterOwnerUseCase
import com.barberia.feature.auth.presentation.common.splitValidation

class RegisterViewModel(private val registerOwner: RegisterOwnerUseCase, private val registerClient: RegisterClientUseCase) :
    MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState()) {

    override suspend fun handleIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.TypeChanged -> setState {
                copy(type = intent.type, fieldErrors = fieldErrors - FieldError.BUSINESS_NAME_REQUIRED, failure = null)
            }

            is RegisterIntent.EmailChanged -> edit(FieldError.EMAIL_INVALID) { copy(email = intent.value) }

            is RegisterIntent.PasswordChanged -> edit(FieldError.PASSWORD_TOO_SHORT) { copy(password = intent.value) }

            is RegisterIntent.NameChanged -> edit(FieldError.NAME_REQUIRED) { copy(name = intent.value) }

            is RegisterIntent.BusinessNameChanged ->
                edit(FieldError.BUSINESS_NAME_REQUIRED) { copy(businessName = intent.value) }

            is RegisterIntent.PhoneChanged -> edit(FieldError.PHONE_INVALID) { copy(phone = intent.value) }

            RegisterIntent.Back -> sendEffect(RegisterEffect.Close)

            RegisterIntent.Submit -> submit()
        }
    }

    private fun edit(clears: FieldError, change: RegisterState.() -> RegisterState) =
        setState { change().copy(fieldErrors = fieldErrors - clears, failure = null) }

    private suspend fun submit() {
        if (currentState.isLoading) return
        setState { copy(isLoading = true, fieldErrors = emptySet(), failure = null) }
        val s = currentState
        val result = when (s.type) {
            AccountType.OWNER -> registerOwner(OwnerRegistration(s.email, s.password, s.name, s.businessName, s.phone))
            AccountType.CLIENT -> registerClient(ClientRegistration(s.email, s.password, s.name, s.phone))
        }
        // Éxito: la sesión cambia y SessionViewModel lleva al home. Aquí solo se limpia la carga.
        setState {
            when (result) {
                is AppResult.Success -> copy(isLoading = false, password = "")

                is AppResult.Failure -> {
                    val (fields, general) = result.error.splitValidation()
                    copy(isLoading = false, fieldErrors = fields, failure = general)
                }
            }
        }
    }
}
