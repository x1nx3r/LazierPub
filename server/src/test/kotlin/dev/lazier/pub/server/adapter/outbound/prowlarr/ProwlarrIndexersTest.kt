package dev.lazier.pub.server.adapter.outbound.prowlarr

import dev.lazier.pub.server.ProwlarrConfig
import dev.lazier.pub.shared.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProwlarrIndexersTest {

    private fun adapterReturning(body: String): ProwlarrIndexers {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(ApiJson) }
        }
        return ProwlarrIndexers(ProwlarrConfig("http://prowlarr", "test-key"), http)
    }

    @Test
    fun listsEnabledIndexersByDisplayName() = runBlocking {
        val adapter = adapterReturning(
            """[
                {"id":2,"name":"Zeta","enable":true,"protocol":"torrent"},
                {"id":1,"name":"Alpha","enable":true,"protocol":"torrent","language":"en-US"},
                {"id":3,"name":"Off","enable":false}
            ]""",
        )

        val indexers = adapter.list()

        assertEquals(listOf("Alpha", "Zeta"), indexers.map { it.name })
        assertEquals(1, indexers.first().id)
        assertEquals("en-US", indexers.first().language)
        assertTrue(indexers.all { it.enabled })
    }
}
