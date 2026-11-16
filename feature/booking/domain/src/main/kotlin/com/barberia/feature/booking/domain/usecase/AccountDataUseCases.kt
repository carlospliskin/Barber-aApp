package com.barberia.feature.booking.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.repository.AccountDataRepository
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.booking.domain.repository.JoinedBarbershopStore
import com.barberia.feature.booking.domain.repository.ReminderScheduler
import java.time.Clock
import java.time.Duration

/**
 * A7: al eliminar la cuenta.
 * - Dueño: primero se borra su barbería entera.
 * - Sus citas en cualquier barbería: se cancelan las que aún puede cancelar (libera el horario) y todas
 *   se anonimizan. Las que ya no puede cancelar quedan sin nombre para que el personal las cierre.
 * - Su ficha en cada barbería se borra ANTES de anonimizar: si se corta, el reintento vuelve a encontrar
 *   las citas y con ellas las barberías.
 */
class EraseUserDataUseCase(
    private val bookings: BookingRepository,
    private val data: AccountDataRepository,
    private val reminders: ReminderScheduler,
    private val joined: JoinedBarbershopStore,
    private val policy: BookingPolicy,
    private val clock: Clock,
) {
    suspend operator fun invoke(uid: String, ownedTenantId: String?): BookingResult<Unit> {
        ownedTenantId?.let { data.deleteBarbershop(it).onFailure { f -> return f } }
        repeat(MAX_PAGES) {
            val page = when (val r = data.myAppointments(uid, PAGE_SIZE)) {
                is AppResult.Success -> r.value
                is AppResult.Failure -> return r
            }
            if (page.isEmpty()) {
                joined.save(uid, null)
                return AppResult.Success(Unit)
            }
            val cancelUntil = clock.instant().plus(Duration.ofHours(policy.cancelHoursBefore))
            for (a in page) {
                reminders.cancel(a.id)
                if (a.status == AppointmentStatus.BOOKED && a.startsAt.isAfter(cancelUntil)) {
                    bookings.cancel(a).onFailure { f -> return f }
                }
            }
            data.deleteClientRecords(uid, page.map { it.tenantId }.toSet()).onFailure { f -> return f }
            data.anonymize(page).onFailure { f -> return f }
        }
        return AppResult.Failure(BookingFailure.Unknown("A7: demasiadas citas"))
    }

    companion object {
        const val PAGE_SIZE = 100
        private const val MAX_PAGES = 200
    }
}

private inline fun BookingResult<Unit>.onFailure(block: (AppResult.Failure<BookingFailure>) -> Nothing) {
    if (this is AppResult.Failure) block(this)
}
