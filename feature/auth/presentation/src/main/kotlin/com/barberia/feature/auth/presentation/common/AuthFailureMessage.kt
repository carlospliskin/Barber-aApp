package com.barberia.feature.auth.presentation.common

import com.barberia.feature.auth.domain.model.AuthFailure

/** Texto para el usuario: vive en presentación, no en dominio. */
fun AuthFailure.userMessage(): String = when (this) {
    AuthFailure.InvalidCredentials -> "Email o contraseña incorrectos"
    AuthFailure.EmailAlreadyExists -> "Este email ya está registrado"
    AuthFailure.NoConnection -> "Sin conexión. Verifica tu internet"
    AuthFailure.SessionExpired -> "Tu sesión expiró. Vuelve a iniciar sesión"
    is AuthFailure.ServerError -> message ?: "Error del servidor. Intenta de nuevo"
    is AuthFailure.Unknown -> message ?: "Ocurrió un error inesperado"
}
