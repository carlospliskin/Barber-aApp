package com.barberia.feature.booking.presentation.common

import androidx.annotation.StringRes
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.presentation.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
fun BookingFailure.messageRes(): Int = when (this) {
    BookingFailure.SlotTaken -> R.string.booking_error_taken
    BookingFailure.SlotUnavailable -> R.string.booking_error_unavailable
    BookingFailure.ServiceUnavailable -> R.string.booking_error_service
    BookingFailure.BarberUnavailable -> R.string.booking_error_barber
    BookingFailure.TooManyActive -> R.string.booking_error_too_many
    BookingFailure.CancelTooLate -> R.string.booking_error_cancel_late
    BookingFailure.InvalidCode -> R.string.booking_error_code
    BookingFailure.HasActiveAppointments -> R.string.booking_error_has_active
    BookingFailure.TooEarly -> R.string.booking_error_too_early
    is BookingFailure.Penalized -> R.string.booking_error_penalized
    BookingFailure.NoRewardAvailable -> R.string.booking_error_no_reward
    BookingFailure.NoConnection -> R.string.booking_error_connection
    BookingFailure.NoAccess -> R.string.booking_error_no_access
    BookingFailure.ShopGone -> R.string.booking_error_shop_gone
    BookingFailure.UndoExpired -> R.string.booking_error_undo_expired
    BookingFailure.RewardAlreadyUsed -> R.string.booking_error_reward_used
    BookingFailure.InvalidClient -> R.string.booking_error_invalid_client
    else -> R.string.booking_error_unknown
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-MX"))

fun LocalDate.shortLabel(): String = format(DAY).replaceFirstChar { it.uppercase() }

/** A16-e: falla real de un Flow (booking o catálogo); "sin conexión" solo si no se sabe más. */
fun Throwable.asBookingFailure(): BookingFailure = when (this) {
    is com.barberia.feature.booking.domain.model.BookingException -> failure

    is com.barberia.feature.catalog.domain.model.CatalogException -> when (failure) {
        com.barberia.feature.catalog.domain.model.CatalogFailure.NotOwner -> BookingFailure.NoAccess
        com.barberia.feature.catalog.domain.model.CatalogFailure.NoConnection -> BookingFailure.NoConnection
        else -> BookingFailure.Unknown(failure.toString())
    }

    else -> BookingFailure.NoConnection
}
