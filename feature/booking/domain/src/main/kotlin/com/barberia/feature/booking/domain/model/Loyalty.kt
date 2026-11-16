package com.barberia.feature.booking.domain.model

import java.time.Duration
import java.time.Instant

/** Reglas de lealtad y penalización (deben coincidir con firestore.rules: 10, 3, 14). */
data class LoyaltyPolicy(
    val visitsPerReward: Int = 10,
    val noShowsForPenalty: Int = 3,
    val penaltyDays: Long = 14,
)

/** Ficha del cliente en una barbería: `tenants/{t}/clients/{uid}`. */
data class ClientRecord(
    val uid: String,
    val name: String,
    val completed: Int,
    val noShows: Int,
    val rewardsUsed: Int,
    val penaltyUntil: Instant?,
) {
    fun availableRewards(p: LoyaltyPolicy): Int = completed / p.visitsPerReward - rewardsUsed

    fun visitsTowardNext(p: LoyaltyPolicy): Int = completed % p.visitsPerReward

    fun isPenalized(now: Instant): Boolean = penaltyUntil?.isAfter(now) == true

    /** P3: una visita más. */
    fun afterCompleted(): ClientRecord = copy(completed = completed + 1)

    /** P1: un no-show más; al llegar al límite queda penalizado. */
    fun afterNoShow(now: Instant, p: LoyaltyPolicy): ClientRecord {
        val total = noShows + 1
        val penalty = if (total >= p.noShowsForPenalty) now.plus(Duration.ofDays(p.penaltyDays)) else penaltyUntil
        return copy(noShows = total, penaltyUntil = penalty)
    }

    companion object {
        fun new(uid: String, name: String) = ClientRecord(uid, name, 0, 0, 0, null)
    }
}
