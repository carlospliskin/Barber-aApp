package com.barberia.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpGraphQLClientTest {
    private val server = MockWebServer()
    private lateinit var client: OkHttpGraphQLClient

    @Before fun setUp() {
        server.start()
        client = OkHttpGraphQLClient(server.url("/graphql").toString(), io = Dispatchers.Unconfined)
    }

    @After fun tearDown() = server.close()

    @Test fun `devuelve data y manda el token`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"data":{"ping":"pong"}}""").build())

        val data = client.execute("{ping}", token = "abc")

        assertEquals("pong", data["ping"]!!.jsonPrimitive.content)
        assertEquals("Bearer abc", server.takeRequest().headers["Authorization"])
    }

    @Test fun `errores graphql se convierten en excepcion tipada`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"errors":[{"message":"Invalid credentials"}]}""").build())
        try {
            client.execute("{x}")
            fail("Debió lanzar")
        } catch (e: NetworkException.GraphQL) {
            assertEquals(listOf("Invalid credentials"), e.errors)
        }
    }

    @Test fun `codigo http no exitoso`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        val error = runCatching { client.execute("{x}") }.exceptionOrNull()
        assertTrue(error is NetworkException.Http && error.code == 500)
    }
}
