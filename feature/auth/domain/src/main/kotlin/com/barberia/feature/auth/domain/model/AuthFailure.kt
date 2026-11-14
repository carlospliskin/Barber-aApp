package com.barberia.feature.auth.domain.model

sealed interface AuthFailure {
    data object InvalidCredentials : AuthFailure
    data object EmailAlreadyExists : AuthFailure
    data object NoConnection : AuthFailure
    data object SessionExpired : AuthFailure
    data class ServerError(val message: String? = null) : AuthFailure
    data class Unknown(val message: String? = null) : AuthFailure

    val isRetryable: Boolean
        get() = when (this) {
            InvalidCredentials, EmailAlreadyExists, SessionExpired -> false
            NoConnection, is ServerError, is Unknown -> true
        }
}
