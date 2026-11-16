package com.barberia.feature.auth.domain.model

/** Cómo está usando la app el usuario en este momento. */
enum class AppMode { OWNER, BARBER, CLIENT }

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
) {
    /** Todo el personal también puede reservar como cliente (A6). El dueño también corta (barbero). */
    val availableModes: List<AppMode>
        get() = when (staffRole) {
            StaffRole.OWNER -> listOf(AppMode.OWNER, AppMode.BARBER, AppMode.CLIENT)
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
