package com.barberia.feature.auth.data.remote

import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.model.ClientProfileInfo
import com.barberia.feature.auth.domain.model.TenantInfo
import kotlinx.serialization.Serializable

// DTOs: solo existen en la capa data. El dominio nunca los ve.

@Serializable
internal data class TenantDto(
    val id: String,
    val name: String,
    val timezone: String = "America/Mexico_City",
    val plan: String = "FREE",
)

@Serializable
internal data class ClientProfileDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val isPenalized: Boolean = false,
    val penaltyUntil: String? = null,
    val noShowCount: Int = 0,
)

@Serializable
internal data class AuthPayloadDto(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val capabilities: List<String> = listOf("CLIENT"),
    val primaryRole: String = "CLIENT",
    val tenant: TenantDto? = null,
    val clientProfile: ClientProfileDto? = null,
)

/** Respuesta de `me`: no trae tokens, se combinan con los guardados. */
@Serializable
internal data class MeDto(
    val userId: String,
    val capabilities: List<String> = listOf("CLIENT"),
    val primaryRole: String = "CLIENT",
    val tenant: TenantDto? = null,
    val clientProfile: ClientProfileDto? = null,
)

internal fun AuthPayloadDto.toDomain() = AuthUser(
    userId = userId,
    token = token,
    refreshToken = refreshToken,
    capabilities = capabilities.toCapabilities(),
    primaryRole = primaryRole.toCapability(),
    tenant = tenant?.toDomain(),
    clientProfile = clientProfile?.toDomain(),
)

internal fun MeDto.toDomain(token: String, refreshToken: String) = AuthUser(
    userId = userId,
    token = token,
    refreshToken = refreshToken,
    capabilities = capabilities.toCapabilities(),
    primaryRole = primaryRole.toCapability(),
    tenant = tenant?.toDomain(),
    clientProfile = clientProfile?.toDomain(),
)

private fun TenantDto.toDomain() = TenantInfo(id, name, timezone, plan)
private fun ClientProfileDto.toDomain() =
    ClientProfileInfo(id, name, phone, isPenalized, penaltyUntil, noShowCount)

private fun List<String>.toCapabilities(): Set<Capability> =
    mapNotNull { raw -> Capability.entries.find { it.name == raw.uppercase() } }.toSet()
        .ifEmpty { setOf(Capability.CLIENT) }

private fun String.toCapability(): Capability =
    Capability.entries.find { it.name == uppercase() } ?: Capability.CLIENT
