package com.barberia.feature.auth.presentation.common

import androidx.annotation.StringRes
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.presentation.R

// El ViewModel guarda tipos de dominio; solo la vista los convierte a texto (strings.xml).

@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.EMAIL_INVALID -> R.string.auth_error_email
    FieldError.PASSWORD_EMPTY -> R.string.auth_error_password_empty
    FieldError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_short
    FieldError.NAME_REQUIRED -> R.string.auth_error_name
    FieldError.BUSINESS_NAME_REQUIRED -> R.string.auth_error_business
    FieldError.PHONE_INVALID -> R.string.auth_error_phone
}

@StringRes
fun AuthFailure.messageRes(): Int = when (this) {
    is AuthFailure.Validation -> R.string.auth_error_unknown

    // se muestran por campo, no aquí
    AuthFailure.InvalidCredentials -> R.string.auth_error_credentials

    AuthFailure.EmailAlreadyInUse -> R.string.auth_error_email_in_use

    AuthFailure.WeakPassword -> R.string.auth_error_weak_password

    AuthFailure.NoConnection -> R.string.auth_error_no_connection

    AuthFailure.TooManyRequests -> R.string.auth_error_too_many

    AuthFailure.NotSignedIn -> R.string.auth_error_not_signed_in

    AuthFailure.ModeNotAllowed -> R.string.auth_error_mode

    is AuthFailure.Unknown -> R.string.auth_error_unknown
}

@StringRes
fun AppMode.labelRes(): Int = when (this) {
    AppMode.OWNER -> R.string.auth_mode_owner
    AppMode.BARBER -> R.string.auth_mode_barber
    AppMode.CLIENT -> R.string.auth_mode_client
}

/** Separa los errores de validación (van a los campos) del resto (mensaje general). */
fun AuthFailure.splitValidation(): Pair<Set<FieldError>, AuthFailure?> =
    if (this is AuthFailure.Validation) errors to null else emptySet<FieldError>() to this
