package com.barberia.feature.booking.domain.model

import java.time.Duration
import java.time.Instant

/** Reglas de lealtad y penalización (deben coincidir con firestore.rules: 10, 3, 14). */
data class LoyaltyPolicy(val visitsPerReward: Int = 10, val noShowsForPenalty: Int = 3, val penaltyDays: Long = 14)

/** Ficha del cliente en una barbería: `tenants/{t}/clients/{uid}`. */
data class ClientRecord(
    val uid: String,
    val name: String,
    val completed: Int,
    val noShows: Int,
    val rewardsUsed: Int,
    val penaltyUntil: Instant?
) {
    fun availableRewards(p: LoyaltyPolicy): Int = completed / p.visitsPerReward - rewardsUsed

    fun visitsTowardNext(p: LoyaltyPolicy): Int = completed % p.visitsPerReward

    fun isPenalized(now: Instant): Boolean = penaltyUntil?.isAfter(now) == true

    /** P3: una visita más. */
    fun afterCompleted(): ClientRecord = copy(completed = completed + 1)

    /** P1: un no-show más; al llegar al límite queda penalizado. */
    fun afterNoShow(now: Instant, p: LoyaltyPolicy): ClientRecord {
        val total = noShows + 1
        // F7-1: cada N faltas (3, 6, 9…). Tras cumplir el castigo se cuenta desde cero; el total se conserva.
        val penalty = if (total % p.noShowsForPenalty == 0) now.plus(Duration.ofDays(p.penaltyDays)) else penaltyUntil
        return copy(noShows = total, penaltyUntil = penalty)
    }

    /** F7-2: se deshace una visita. */
    fun undoCompleted(): ClientRecord = copy(completed = completed - 1)

    /** F7-2: se deshace una falta; si fue la que penalizó (3, 6, 9…), se quita la penalización. */
    fun undoNoShow(p: LoyaltyPolicy): ClientRecord =
        copy(noShows = noShows - 1, penaltyUntil = if (noShows % p.noShowsForPenalty == 0) null else penaltyUntil)

    companion object {
        fun new(uid: String, name: String) = ClientRecord(uid, name, 0, 0, 0, null)
    }
}
