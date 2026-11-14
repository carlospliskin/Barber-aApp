package com.barberia.feature.auth.data

import com.barberia.core.common.AppResult
import com.barberia.core.network.GraphQLClient
import com.barberia.core.network.NetworkException
import com.barberia.feature.auth.data.local.SessionStorage
import com.barberia.feature.auth.data.local.StoredSession
import com.barberia.feature.auth.data.remote.AuthRemoteDataSource
import com.barberia.feature.auth.data.repository.AuthRepositoryImpl
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.auth.domain.model.Capability
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class AuthRepositoryImplTest {

    private class FakeStorage(var session: StoredSession? = null) : SessionStorage {
        override suspend fun read() = session
        override suspend fun save(session: StoredSession) { this.session = session }
        override suspend fun clear() { session = null }
    }

    private class FakeClient(private val respond: (String) -> JsonObject) : GraphQLClient {
        override suspend fun execute(document: String, variables: Map<String, Any?>, token: String?) = respond(document)
    }

    private fun json(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private val loginOk = json(
        """{"login":{"token":"T","refreshToken":"R","userId":"u1",
           "capabilities":["OWNER","CLIENT"],"primaryRole":"OWNER",
           "tenant":{"id":"b1","name":"Barber Gold"}}}""",
    )

    @Test fun `login exitoso guarda sesion y mapea a dominio`() = runTest {
        val storage = FakeStorage()
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(FakeClient { loginOk }), storage)

        val result = repo.login("a@b.com", "x") as AppResult.Success

        assertEquals(setOf(Capability.OWNER, Capability.CLIENT), result.value.capabilities)
        assertEquals("Barber Gold", result.value.displayName)
        assertEquals(StoredSession("T", "R"), storage.session)
        assertEquals(result.value, repo.currentUser)
    }

    @Test fun `credenciales invalidas`() = runTest {
        val repo = AuthRepositoryImpl(
            AuthRemoteDataSource(FakeClient { throw NetworkException.GraphQL(listOf("Invalid credentials")) }),
            FakeStorage(),
        )
        assertEquals(AppResult.Failure(AuthFailure.InvalidCredentials), repo.login("a@b.com", "x"))
    }

    @Test fun `sin conexion`() = runTest {
        val repo = AuthRepositoryImpl(
            AuthRemoteDataSource(FakeClient { throw NetworkException.NoConnection(IOException()) }),
            FakeStorage(),
        )
        assertEquals(AppResult.Failure(AuthFailure.NoConnection), repo.login("a@b.com", "x"))
    }

    @Test fun `token invalido al restaurar limpia la sesion`() = runTest {
        val storage = FakeStorage(StoredSession("viejo", "r"))
        val repo = AuthRepositoryImpl(
            AuthRemoteDataSource(FakeClient { throw NetworkException.Http(401) }),
            storage,
        )
        assertEquals(AppResult.Success(null), repo.restoreSession())
        assertNull(storage.session)
    }

    @Test fun `http inesperado es error de servidor sin codigo crudo`() = runTest {
        val repo = AuthRepositoryImpl(
            AuthRemoteDataSource(FakeClient { throw NetworkException.Http(404) }),
            FakeStorage(),
        )
        assertEquals(AppResult.Failure(AuthFailure.ServerError()), repo.login("a@b.com", "x"))
    }

    @Test fun `switchMode sin sesion es sesion expirada`() = runTest {
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(FakeClient { loginOk }), FakeStorage())
        assertEquals(AppResult.Failure(AuthFailure.SessionExpired), repo.switchMode(Capability.OWNER))
    }
}
