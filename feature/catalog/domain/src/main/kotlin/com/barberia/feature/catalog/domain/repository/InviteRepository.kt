package com.barberia.feature.catalog.domain.repository

import com.barberia.feature.catalog.domain.editor.CatalogResult
import com.barberia.feature.catalog.domain.model.StaffInvite

interface InviteRepository {
    /** Guarda la invitación. `false` si el código ya existe. */
    suspend fun create(invite: StaffInvite): CatalogResult<Boolean>

    suspend fun find(code: String): CatalogResult<StaffInvite?>

    /** Marca la invitación como usada y liga el perfil de [uid] al barbero, de forma atómica. */
    suspend fun redeem(invite: StaffInvite, uid: String): CatalogResult<Unit>
}
