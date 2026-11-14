package com.barberia.feature.auth.domain.model

enum class Capability { OWNER, BARBER, CLIENT }

data class TenantInfo(
    val id: String,
    val name: String,
    val timezone: String = "America/Mexico_City",
    val plan: String = "FREE",
) {
    val isPaidPlan: Boolean get() = plan != "FREE"
}

data class ClientProfileInfo(
    val id: String,
    val name: String,
    val phone: String? = null,
    val isPenalized: Boolean = false,
    val penaltyUntil: String? = null,
    val noShowCount: Int = 0,
)

/** Usuario autenticado. Entidad de dominio: sin anotaciones de serialización ni Android. */
data class AuthUser(
    val userId: String,
    val token: String,
    val refreshToken: String,
    val capabilities: Set<Capability>,
    val primaryRole: Capability,
    val tenant: TenantInfo? = null,
    val clientProfile: ClientProfileInfo? = null,
) {
    val isOwner: Boolean get() = Capability.OWNER in capabilities
    val isBarber: Boolean get() = Capability.BARBER in capabilities
    val isClient: Boolean get() = Capability.CLIENT in capabilities
    val hasMultipleRoles: Boolean get() = capabilities.size > 1

    val displayName: String get() = clientProfile?.name ?: tenant?.name ?: userId.take(8)

    val initials: String
        get() {
            val parts = displayName.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts[0][0]}${parts[1][0]}".uppercase()
                parts.isNotEmpty() -> parts[0][0].uppercase()
                else -> "U"
            }
        }
}
