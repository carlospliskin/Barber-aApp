package com.barberia.app.di

import com.barberia.core.common.AppResult
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.repository.AuthResult
import com.barberia.feature.auth.domain.repository.UserDataEraser
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.usecase.EraseUserDataUseCase

/** A7: auth pide borrar los datos del usuario; booking sabe dónde están. Solo `app` conoce a los dos. */
class BookingUserDataEraser(private val erase: EraseUserDataUseCase) : UserDataEraser {
    override suspend fun erase(uid: String, ownedTenantId: String?): AuthResult<Unit> = when (val r = erase.invoke(uid, ownedTenantId)) {
        is AppResult.Success -> r
        is AppResult.Failure -> AppResult.Failure(r.error.toAuthFailure())
    }
}

internal fun BookingFailure.toAuthFailure(): AuthFailure = when (this) {
    BookingFailure.NoConnection -> AuthFailure.NoConnection
    BookingFailure.DailyLimit -> AuthFailure.DailyLimit
    is BookingFailure.Unknown -> AuthFailure.Unknown(detail)
    else -> AuthFailure.Unknown(toString())
}
