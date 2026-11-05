package dev.lazier.pub.server.adapter.outbound.prowlarr

import dev.lazier.pub.server.ProwlarrConfig
import dev.lazier.pub.shared.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProwlarrReleaseSearchTest {

    private fun adapterReturning(
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): ProwlarrReleaseSearch {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(ApiJson) }
        }
        return ProwlarrReleaseSearch(ProwlarrConfig("http://prowlarr", "test-key"), http)
    }

    @Test
    fun ranksBySeedersNotSize() = runBlocking {
        val adapter = adapterReturning(
            """[
                {"title":"dead torrent","guid":"magnet:?xt=1","size":10,"seeders":0},
                {"title":"popular","guid":"magnet:?xt=2","size":10,"seeders":42}
            ]""",
        )
        assertEquals(listOf("popular", "dead torrent"), adapter.search("x", 10).map { it.title })
    }

    @Test
    fun takesTheMagnetFromGuidWhenNothingElseIsPresent() = runBlocking {
        val adapter = adapterReturning("""[{"title":"a","guid":"magnet:?xt=abc","seeders":1}]""")
        assertEquals("magnet:?xt=abc", adapter.search("x", 10).single().downloadUrl)
    }

    @Test
    fun prefersMagnetUrlOverDownloadUrl() = runBlocking {
        val adapter = adapterReturning(
            """[{"title":"a","guid":"g","magnetUrl":"magnet:?xt=m","downloadUrl":"http://d","seeders":1}]""",
        )
        assertEquals("magnet:?xt=m", adapter.search("x", 10).single().downloadUrl)
    }

    @Test
    fun dropsResultsWithNoDownloadLink() = runBlocking {
        val adapter = adapterReturning("""[{"title":"nothing","seeders":5}]""")
        assertTrue(adapter.search("x", 10).isEmpty())
    }

    @Test
    fun readsFreeleechFromIndexerFlags() = runBlocking {
        val adapter = adapterReturning(
            """[{"title":"a","guid":"magnet:?xt=1","seeders":1,"indexerFlags":["freeleech"]}]""",
        )
        assertTrue(adapter.search("x", 10).single().freeleech)
    }

    @Test
    fun readsSourceAndReleaseMetadata() = runBlocking {
        val adapter = adapterReturning(
            """[{
                "title":"Lazy Dungeon Master v01-09 (epub)",
                "fileName":"Lazy Dungeon Master v01-09.torrent",
                "guid":"magnet:?xt=1",
                "indexerId":7,
                "indexer":"Nyaa.si",
                "protocol":"torrent",
                "grabs":42,
                "size":2048,
                "seeders":3,
                "publishDate":"2026-03-18T04:04:00Z",
                "categories":[{"id":7000,"name":"Books"}]
            }]""",
        )

        val result = adapter.search("x", 10).single()

        assertEquals(7, result.indexerId)
        assertEquals("Nyaa.si", result.indexer)
        assertEquals("torrent", result.protocol)
        assertEquals(42, result.grabs)
        assertEquals(listOf("Books"), result.categories)
        assertEquals(listOf("EPUB"), result.formats)
    }

    @Test
    fun readsEveryNamedFormatWithoutRepeats() = runBlocking {
        val adapter = adapterReturning(
            """[{"title":"Volume 1 [EPUB] + Volume 2 (pdf) + extras epub","guid":"magnet:?xt=1","seeders":1}]""",
        )
        assertEquals(listOf("EPUB", "PDF"), adapter.search("x", 10).single().formats)
    }

    @Test
    fun leavesFormatsEmptyWhenTheNameDoesNotSay() = runBlocking {
        val adapter = adapterReturning(
            """[{"title":"Light Novel Collection","fileName":"Light Novel Collection.torrent","guid":"magnet:?xt=1","seeders":1}]""",
        )
        assertTrue(adapter.search("x", 10).single().formats.isEmpty())
    }

    @Test
    fun passesTheSourceFilterAsRepeatedKeys() = runBlocking {
        var sent: List<String> = emptyList()
        val adapter = adapterReturning("[]") { request ->
            sent = request.url.parameters.getAll("indexerIds").orEmpty()
        }

        adapter.search("x", 10, listOf(1, 4))

        assertEquals(listOf("1", "4"), sent)
    }
}
