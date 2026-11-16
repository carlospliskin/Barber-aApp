package com.barberia.feature.auth.domain.model

/** Cómo está usando la app el usuario en este momento. */
enum class AppMode { OWNER, BARBER, CLIENT }

/** A7-b/A16: si la cuenta de barbero todavía sirve. */
enum class StaffAccess { ACTIVE, REVOKED, SHOP_GONE }

/** Rol de personal dentro de una barbería. Un cliente no tiene. */
enum class StaffRole { OWNER, BARBER }

/** Usuario con sesión. Entidad de dominio: no sabe de Firebase ni de Android. */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean = false,
    val phone: String? = null,
    val staffTenantId: String? = null,
    val staffRole: StaffRole? = null,
    /** A10: barbero del catálogo al que está ligada esta cuenta (solo rol BARBER). */
    val staffBarberId: String? = null
) {
    /** Todo el personal también puede reservar como cliente (A6). */
    val availableModes: List<AppMode>
        get() = when (staffRole) {
            // W1: el dueño solo trabaja como barbero si está ligado a un barbero del catálogo
            StaffRole.OWNER -> listOfNotNull(AppMode.OWNER, AppMode.BARBER.takeIf { staffBarberId != null }, AppMode.CLIENT)

            StaffRole.BARBER -> listOf(AppMode.BARBER, AppMode.CLIENT)

            null -> listOf(AppMode.CLIENT)
        }

    val initials: String
        get() {
            val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts[0][0]}${parts[1][0]}".uppercase()
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> "?"
            }
        }
}
