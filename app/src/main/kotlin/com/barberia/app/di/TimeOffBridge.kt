package com.barberia.app.di

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.booking.domain.usecase.CancelByStaffUseCase
import com.barberia.feature.booking.presentation.common.asBookingFailure
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.repository.TimeOffAppointments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** N2-b: el catálogo pregunta a booking qué citas choca un día libre (una consulta del día) y las cancela. */
class BookingTimeOffAppointments(private val bookings: BookingRepository, private val cancelByStaff: CancelByStaffUseCase) :
    TimeOffAppointments {

    override suspend fun affected(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Int> =
        hit(tenantId, barberId, off).map { it.size }

    override suspend fun cancel(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<Int> =
        when (val r = hit(tenantId, barberId, off)) {
            is AppResult.Failure -> r

            // Las que ya empezaron (W4) o fallan se quedan: se cuentan solo las canceladas.
            is AppResult.Success -> AppResult.Success(r.value.count { cancelByStaff(it) is AppResult.Success })
        }

    private suspend fun hit(tenantId: String, barberId: String?, off: TimeOff): CatalogResult<List<Appointment>> = try {
        val day = bookings.observeDay(tenantId, off.date, barberId).first()
        AppResult.Success(
            day.filter { it.status == AppointmentStatus.BOOKED && off.blocks(it.date, it.startMinute, it.endMinute) }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(e.asBookingFailure().toCatalogFailure())
    }

    private fun <T, R> CatalogResult<T>.map(f: (T) -> R): CatalogResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(f(value))
        is AppResult.Failure -> this
    }
}

internal fun BookingFailure.toCatalogFailure(): CatalogFailure = when (this) {
    BookingFailure.NoConnection -> CatalogFailure.NoConnection
    BookingFailure.NoAccess -> CatalogFailure.NotOwner
    else -> CatalogFailure.Unknown(toString())
}
