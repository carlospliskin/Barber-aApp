package com.barberia.feature.catalog.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.core.common.ShortCodes
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.CatalogException
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.StaffInvite
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import com.barberia.feature.catalog.domain.repository.InviteRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.util.Random

class CreateBarberInviteUseCase(
    private val invites: InviteRepository,
    private val catalog: CatalogRepository,
    private val clock: Clock,
    private val random: Random,
) {
    /** A1: invitación para un barbero ACTIVO del catálogo; vence en 7 días. */
    suspend operator fun invoke(tenantId: String, barberId: String): CatalogResult<StaffInvite> {
        val barber = try {
            catalog.observe(tenantId).first().barbers.find { it.id == barberId && it.active }
        } catch (e: CatalogException) {
            return AppResult.Failure(e.failure)
        } ?: return AppResult.Failure(CatalogFailure.NotFound)
        repeat(MAX_ATTEMPTS) {
            val invite = StaffInvite(
                code = ShortCodes.generate(random),
                tenantId = tenantId,
                barberId = barber.id,
                barberName = barber.name,
                expiresAt = clock.instant().plus(VALIDITY),
                usedBy = null,
            )
            when (val r = invites.create(invite)) {
                is AppResult.Failure -> return r
                is AppResult.Success -> if (r.value) return AppResult.Success(invite)
            }
        }
        return AppResult.Failure(CatalogFailure.Unknown("No se encontró un código libre"))
    }

    companion object {
        val VALIDITY: Duration = Duration.ofDays(7)
        private const val MAX_ATTEMPTS = 5
    }
}

class RedeemBarberInviteUseCase(
    private val invites: InviteRepository,
    private val catalog: CatalogRepository,
    private val clock: Clock,
) {
    /** A1/A8: un cliente sin rol acepta una invitación vigente y sin usar. */
    suspend operator fun invoke(rawCode: String, uid: String, alreadyStaff: Boolean): CatalogResult<StaffInvite> {
        if (alreadyStaff) return AppResult.Failure(CatalogFailure.AlreadyStaff)
        val code = ShortCodes.normalize(rawCode) ?: return AppResult.Failure(CatalogFailure.InviteInvalid)
        val invite = when (val r = invites.find(code)) {
            is AppResult.Success -> r.value ?: return AppResult.Failure(CatalogFailure.InviteInvalid)
            is AppResult.Failure -> return r
        }
        if (invite.usedBy != null) return AppResult.Failure(CatalogFailure.InviteInvalid)
        if (!clock.instant().isBefore(invite.expiresAt)) return AppResult.Failure(CatalogFailure.InviteExpired)
        // W2: el barbero debe seguir activo en el catálogo
        val stillActive = try {
            catalog.observe(invite.tenantId).first().barbers.any { it.id == invite.barberId && it.active }
        } catch (e: CatalogException) {
            return AppResult.Failure(e.failure)
        }
        if (!stillActive) return AppResult.Failure(CatalogFailure.InviteInvalid)
        return when (val r = invites.redeem(invite, uid)) {
            is AppResult.Success -> AppResult.Success(invite)
            is AppResult.Failure -> r
        }
    }
}

/**
 * A16: el dueño quita el acceso de la app a un barbero (renunció, lo despidió o cambió de cuenta).
 * Revoca todas las cuentas ligadas a ese barbero del catálogo. Devuelve cuántas.
 */
class RevokeBarberAccessUseCase(private val invites: InviteRepository) {
    suspend operator fun invoke(tenantId: String, barberId: String): CatalogResult<Int> {
        val uids = when (val r = invites.linkedAccounts(tenantId, barberId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> return r
        }
        uids.forEach { uid -> invites.revoke(tenantId, uid, barberId).let { if (it is AppResult.Failure) return it } }
        return AppResult.Success(uids.size)
    }
}
