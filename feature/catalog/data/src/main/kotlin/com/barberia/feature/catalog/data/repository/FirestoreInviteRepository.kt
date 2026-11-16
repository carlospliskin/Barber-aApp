package com.barberia.feature.catalog.data.repository

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.catalog.data.gateway.InviteGateway
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.StaffInvite
import com.barberia.feature.catalog.domain.repository.InviteRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException

internal class FirestoreInviteRepository(private val gateway: InviteGateway) : InviteRepository {

    override suspend fun create(invite: StaffInvite): CatalogResult<Boolean> = attempt {
        gateway.createIfAbsent(
            invite.code,
            mapOf(
                "tenantId" to invite.tenantId,
                "barberId" to invite.barberId,
                "barberName" to invite.barberName,
                "expiresAt" to invite.expiresAt,
                "usedBy" to null
            )
        )
    }

    override suspend fun find(code: String): CatalogResult<StaffInvite?> = attempt {
        gateway.get(code)?.let { m ->
            runCatching {
                StaffInvite(
                    code = code,
                    tenantId = m["tenantId"] as String,
                    barberId = m["barberId"] as String,
                    barberName = m["barberName"] as? String ?: "",
                    expiresAt = m["expiresAt"] as Instant,
                    usedBy = m["usedBy"] as? String
                )
            }.getOrNull()
        }
    }

    /** Debe coincidir con `redeemsInvite()` de firestore.rules. */
    override suspend fun redeem(invite: StaffInvite, uid: String): CatalogResult<Unit> = attempt(onDenied = CatalogFailure.InviteInvalid) {
        gateway.redeem(
            invite.code,
            uid,
            mapOf(
                "staffTenantId" to invite.tenantId,
                "staffRole" to "BARBER",
                "staffBarberId" to invite.barberId,
                "staffInviteCode" to invite.code
            )
        )
    }

    override suspend fun linkedAccounts(tenantId: String, barberId: String): CatalogResult<Set<String>> = attempt {
        gateway.invitesOfTenant(tenantId).filter { it["barberId"] == barberId }.mapNotNull { it["usedBy"] as? String }.toSet()
    }

    override suspend fun revoke(tenantId: String, uid: String, barberId: String): CatalogResult<Unit> = attempt {
        gateway.revoke(tenantId, uid, barberId)
    }

    private suspend fun <T> attempt(onDenied: CatalogFailure? = null, block: suspend () -> T): CatalogResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: GatewayError) {
        AppResult.Failure(
            when (e) {
                is GatewayError.Network -> CatalogFailure.NoConnection
                is GatewayError.PermissionDenied -> onDenied ?: CatalogFailure.NotOwner
                else -> CatalogFailure.Unknown(e.cause?.message)
            }
        )
    }
}
