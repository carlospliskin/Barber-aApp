package com.barberia.feature.auth.data.remote

import com.barberia.core.network.GraphQLClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** Única responsabilidad: hablar con el backend GraphQL de auth y devolver DTOs. */
internal class AuthRemoteDataSource(
    private val client: GraphQLClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun login(email: String, password: String): AuthPayloadDto =
        client.execute(
            "mutation(\$e:String!,\$p:String!){login(email:\$e,password:\$p){$AUTH_FIELDS}}",
            mapOf("e" to email, "p" to password),
        ).decode("login")

    suspend fun registerOwner(
        email: String,
        password: String,
        barberiaName: String,
        ownerName: String,
        phone: String?,
    ): AuthPayloadDto = client.execute(
        "mutation(\$e:String!,\$p:String!,\$b:String!,\$o:String!,\$ph:String)" +
            "{register(email:\$e,password:\$p,barberiaName:\$b,ownerName:\$o,phone:\$ph){$AUTH_FIELDS}}",
        mapOf("e" to email, "p" to password, "b" to barberiaName, "o" to ownerName, "ph" to phone),
    ).decode("register")

    suspend fun registerClient(email: String, password: String, name: String, phone: String): AuthPayloadDto =
        client.execute(
            "mutation(\$e:String!,\$p:String!,\$n:String!,\$ph:String!)" +
                "{registerClient(email:\$e,password:\$p,name:\$n,phone:\$ph){$AUTH_FIELDS}}",
            mapOf("e" to email, "p" to password, "n" to name, "ph" to phone),
        ).decode("registerClient")

    suspend fun switchMode(mode: String, token: String): AuthPayloadDto =
        client.execute(
            "mutation(\$m:String!){switchMode(mode:\$m){$AUTH_FIELDS}}",
            mapOf("m" to mode),
            token = token,
        ).decode("switchMode")

    suspend fun me(token: String): MeDto =
        client.execute("{me{$PROFILE_FIELDS}}", token = token).decode("me")

    private inline fun <reified T> JsonObject.decode(field: String): T =
        json.decodeFromJsonElement(getValue(field).jsonObject)

    private companion object {
        const val PROFILE_FIELDS = "userId capabilities primaryRole " +
            "tenant{id name timezone plan} " +
            "clientProfile{id name phone isPenalized penaltyUntil noShowCount}"
        const val AUTH_FIELDS = "token refreshToken $PROFILE_FIELDS"
    }
}
