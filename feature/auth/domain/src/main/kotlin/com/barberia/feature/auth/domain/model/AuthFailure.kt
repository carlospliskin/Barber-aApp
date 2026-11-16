package com.barberia.feature.auth.domain.model

/** Campos de formulario que pueden fallar; la UI marca cada uno. */
enum class FieldError { EMAIL_INVALID, PASSWORD_EMPTY, PASSWORD_TOO_SHORT, NAME_REQUIRED, BUSINESS_NAME_REQUIRED, PHONE_INVALID }

sealed interface AuthFailure {
    data class Validation(val errors: Set<FieldError>) : AuthFailure
    data object InvalidCredentials : AuthFailure
    data object EmailAlreadyInUse : AuthFailure
    data object WeakPassword : AuthFailure
    data object NoConnection : AuthFailure
    data object TooManyRequests : AuthFailure
    data object NotSignedIn : AuthFailure
    data object ModeNotAllowed : AuthFailure
    data class Unknown(val detail: String? = null) : AuthFailure
}
