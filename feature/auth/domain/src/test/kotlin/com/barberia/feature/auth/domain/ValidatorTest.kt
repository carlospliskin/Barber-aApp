package com.barberia.feature.auth.domain

import com.barberia.feature.auth.domain.testing.*

import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.domain.validation.AuthValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidatorTest {

    @Test fun `email`() {
        assertEquals(emptySet<FieldError>(), AuthValidator.email("ana@mail.com"))
        assertEquals(setOf(FieldError.EMAIL_INVALID), AuthValidator.email("ana@"))
        assertEquals(setOf(FieldError.EMAIL_INVALID), AuthValidator.email(""))
    }

    @Test fun `login solo exige contrasena no vacia`() {
        assertEquals(setOf(FieldError.PASSWORD_EMPTY), AuthValidator.signIn("ana@mail.com", ""))
        assertEquals(emptySet<FieldError>(), AuthValidator.signIn("ana@mail.com", "123"))
    }

    @Test fun `registro exige contrasena de 8 o mas (A3)`() {
        assertEquals(setOf(FieldError.PASSWORD_TOO_SHORT), AuthValidator.newPassword("1234567"))
        assertEquals(emptySet<FieldError>(), AuthValidator.newPassword("12345678"))
    }

    @Test fun `telefono de 10 a 15 digitos, acepta espacios guiones y +`() {
        assertEquals(emptySet<FieldError>(), AuthValidator.phone("+52 55-1234-5678"))
        assertEquals(emptySet<FieldError>(), AuthValidator.phone("5512345678"))
        assertEquals(setOf(FieldError.PHONE_INVALID), AuthValidator.phone("12345"))
        assertEquals(setOf(FieldError.PHONE_INVALID), AuthValidator.phone("55abc12345"))
    }

    @Test fun `junta todos los errores a la vez para marcarlos en el formulario`() {
        val errors = AuthValidator.ownerRegistration(
            email = "x", password = "1", ownerName = " ", businessName = "", phone = null,
        )
        assertEquals(
            setOf(
                FieldError.EMAIL_INVALID,
                FieldError.PASSWORD_TOO_SHORT,
                FieldError.NAME_REQUIRED,
                FieldError.BUSINESS_NAME_REQUIRED,
            ),
            errors,
        )
    }
}
