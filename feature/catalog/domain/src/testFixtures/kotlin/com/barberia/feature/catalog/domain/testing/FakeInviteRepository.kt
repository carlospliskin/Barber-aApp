package com.barberia.feature.catalog.domain.testing

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.StaffInvite
import com.barberia.feature.catalog.domain.repository.InviteRepository

class FakeInviteRepository : InviteRepository {
    val stored = mutableMapOf<String, StaffInvite>()
    val taken = mutableSetOf<String>()
    var attempts = 0
    var redeemed: Pair<String, String>? = null

    override suspend fun create(invite: StaffInvite): CatalogResult<Boolean> {
        attempts++
        if (invite.code in taken || invite.code in stored) return AppResult.Success(false)
        stored[invite.code] = invite
        return AppResult.Success(true)
    }

    override suspend fun find(code: String): CatalogResult<StaffInvite?> = AppResult.Success(stored[code])

    override suspend fun redeem(invite: StaffInvite, uid: String): CatalogResult<Unit> {
        redeemed = uid to invite.code
        stored[invite.code] = invite.copy(usedBy = uid)
        return AppResult.Success(Unit)
    }
}
