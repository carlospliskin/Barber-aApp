package com.barberia.feature.booking.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.ClientRecord
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.repository.BookingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RedeemRewardUseCase(private val bookings: BookingRepository, private val loyalty: LoyaltyPolicy) {
    /** P4: canjea un premio si el cliente tiene alguno disponible. */
    suspend operator fun invoke(tenantId: String, clientUid: String): BookingResult<Unit> {
        val r = bookings.updateClient(tenantId, clientUid) { c ->
            if (c.availableRewards(loyalty) <= 0) AppResult.Failure(BookingFailure.NoRewardAvailable)
            else AppResult.Success(c.copy(rewardsUsed = c.rewardsUsed + 1))
        }
        return if (r is AppResult.Failure) r else AppResult.Success(Unit)
    }
}

class ForgivePenaltyUseCase(private val bookings: BookingRepository) {
    /** P5: el dueño quita la penalización y reinicia los no-shows. */
    suspend operator fun invoke(tenantId: String, clientUid: String): BookingResult<Unit> {
        val r = bookings.updateClient(tenantId, clientUid) { c -> AppResult.Success(c.copy(noShows = 0, penaltyUntil = null)) }
        return if (r is AppResult.Failure) r else AppResult.Success(Unit)
    }
}

class ObserveClientsUseCase(private val bookings: BookingRepository) {
    /** P5: lista del dueño, penalizados primero y luego por visitas. */
    operator fun invoke(tenantId: String): Flow<List<ClientRecord>> =
        bookings.observeClients(tenantId).map { list ->
            list.sortedWith(compareByDescending<ClientRecord> { it.penaltyUntil != null }.thenByDescending { it.completed })
        }
}

class ObserveMyLoyaltyUseCase(private val bookings: BookingRepository) {
    /** P6: la ficha del cliente en su barbería (1 lectura + cambios). */
    operator fun invoke(tenantId: String, uid: String): Flow<ClientRecord?> = bookings.observeClient(tenantId, uid)
}
