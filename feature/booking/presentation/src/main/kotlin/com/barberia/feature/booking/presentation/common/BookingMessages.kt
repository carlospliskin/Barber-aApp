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
    BookingFailure.NoConnection -> R.string.booking_error_connection
    else -> R.string.booking_error_unknown
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-MX"))

fun LocalDate.shortLabel(): String = format(DAY).replaceFirstChar { it.uppercase() }
