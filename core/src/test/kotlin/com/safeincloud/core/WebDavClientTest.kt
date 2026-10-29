package com.safeincloud.core

import com.safeincloud.core.sync.WebDavClient
import com.safeincloud.core.sync.WebDavConfig
import com.safeincloud.core.sync.WebDavException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    private fun client(engine: MockEngine): WebDavClient =
        WebDavClient(
            WebDavConfig(baseUrl = "https://dav.example.com/"),
            engine
        )

    @Test
    fun `get treats 409 as missing when head probe is 404`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond("", HttpStatusCode.Conflict)
                HttpMethod.Head -> respond("", HttpStatusCode.NotFound)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val webDav = client(engine)
        try {
            assertNull(webDav.get("SafeInCloud/vault-test.sicv"))
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `get keeps bytes on 200`() = runBlocking {
        val payload = byteArrayOf(1, 2, 3, 4)
        val engine = MockEngine {
            respond(payload, HttpStatusCode.OK)
        }
        val webDav = client(engine)
        try {
            assertArrayEquals(payload, webDav.get("SafeInCloud/vault-test.sicv"))
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `get reports 409 path conflict when resource exists`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond("", HttpStatusCode.Conflict)
                HttpMethod.Head -> respond("", HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val webDav = client(engine)
        try {
            val failure = runCatching { webDav.get("SafeInCloud/vault-test.sicv") }.exceptionOrNull()
            assertTrue(failure is WebDavException)
            assertTrue(failure?.message.orEmpty().contains("409"))
        } finally {
            webDav.close()
        }
    }
}
