package com.barberia.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpGraphQLClient(
    private val endpoint: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : GraphQLClient {

    override suspend fun execute(
        document: String,
        variables: Map<String, Any?>,
        token: String?,
    ): JsonObject = withContext(io) {
        val body = buildJsonObject {
            put("query", JsonPrimitive(document))
            put("variables", JsonObject(variables.filterValues { it != null }.mapValues { it.value.toJson() }))
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        val raw = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw NetworkException.Http(response.code)
                response.body.string()
            }
        } catch (e: IOException) {
            throw NetworkException.NoConnection(e)
        }

        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: SerializationException) {
            throw NetworkException.Malformed(e)
        } catch (e: IllegalArgumentException) {
            throw NetworkException.Malformed(e)
        }

        (root["errors"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { errors ->
            throw NetworkException.GraphQL(
                errors.map { (it as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: it.toString() },
            )
        }
        root["data"] as? JsonObject ?: throw NetworkException.Malformed(IllegalStateException("Sin nodo data"))
    }

    private fun Any?.toJson(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

    private companion object {
        val JSON = "application/json".toMediaType()

        fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
