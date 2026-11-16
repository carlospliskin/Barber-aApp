package com.barberia.feature.auth.domain.validation

import com.barberia.feature.auth.domain.model.FieldError

/** Reglas de formulario puras. Devuelven todos los errores juntos para marcarlos a la vez. */
object AuthValidator {
    const val MIN_PASSWORD = 8
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    private val PHONE_ALLOWED = Regex("^\\+?[0-9 \\-]+$")

    fun email(value: String): Set<FieldError> =
        if (EMAIL.matches(value.trim())) emptySet() else setOf(FieldError.EMAIL_INVALID)

    fun signIn(email: String, password: String): Set<FieldError> =
        email(email) + if (password.isEmpty()) setOf(FieldError.PASSWORD_EMPTY) else emptySet()

    fun newPassword(value: String): Set<FieldError> =
        if (value.length >= MIN_PASSWORD) emptySet() else setOf(FieldError.PASSWORD_TOO_SHORT)

    fun phone(value: String): Set<FieldError> {
        val digits = value.filter { it.isDigit() }
        val ok = PHONE_ALLOWED.matches(value.trim()) && digits.length in 10..15
        return if (ok) emptySet() else setOf(FieldError.PHONE_INVALID)
    }

    fun ownerRegistration(
        email: String,
        password: String,
        ownerName: String,
        businessName: String,
        phone: String?,
    ): Set<FieldError> = buildSet {
        addAll(email(email))
        addAll(newPassword(password))
        if (ownerName.isBlank()) add(FieldError.NAME_REQUIRED)
        if (businessName.isBlank()) add(FieldError.BUSINESS_NAME_REQUIRED)
        if (!phone.isNullOrBlank()) addAll(phone(phone))
    }

    fun clientRegistration(email: String, password: String, name: String, phone: String): Set<FieldError> =
        buildSet {
            addAll(email(email))
            addAll(newPassword(password))
            if (name.isBlank()) add(FieldError.NAME_REQUIRED)
            addAll(phone(phone))
        }

    fun normalizePhone(value: String): String {
        val digits = value.filter { it.isDigit() }
        return if (value.trim().startsWith("+")) "+$digits" else digits
    }
}
