package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.GrabQueue
import dev.lazier.pub.server.port.IndexerCatalog
import dev.lazier.pub.server.port.ReleaseFiles
import dev.lazier.pub.server.port.ReleaseSearch
import dev.lazier.pub.server.port.StoredBook
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.Grab
import dev.lazier.pub.shared.Indexer
import dev.lazier.pub.shared.SearchResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Use cases tested through their ports only. No HTTP, no mock engine, no
 * upstream services.
 */
class UseCaseTest {

    private class FakeCatalog(private val books: List<Book>) : BookCatalog {
        override fun all(): List<Book> = books
        override fun find(id: String): StoredBook? =
            books.firstOrNull { it.id == id }?.let { StoredBook(it, Paths.get("/tmp/${it.id}")) }
    }

    private class FakeSearch(private val results: List<SearchResult>) : ReleaseSearch {
        var calls = 0
        var lastIndexerIds: List<Int> = emptyList()
        override suspend fun search(
            query: String,
            limit: Int,
            indexerIds: List<Int>,
        ): List<SearchResult> {
            calls++
            lastIndexerIds = indexerIds
            return results
        }
    }

    private class FakeIndexers(private val indexers: List<Indexer>) : IndexerCatalog {
        override suspend fun list(): List<Indexer> = indexers
    }

    private class FakeFiles(private val formats: Map<String, List<String>>) : ReleaseFiles {
        var calls = 0
        override suspend fun formats(infoUrl: String): List<String> {
            calls++
            return formats[infoUrl].orEmpty()
        }
    }

    private class FakeQueue : GrabQueue {
        private val items = mutableListOf<Grab>()
        override suspend fun list(): List<Grab> = items.toList()
        override suspend fun add(downloadUrl: String, category: String?) {
            items += Grab(hash = "hash-${items.size}", name = downloadUrl, state = "downloading", progress = 0.0)
        }
        override suspend fun remove(hash: String) {
            items.removeAll { it.hash == hash }
        }
    }

    @Test
    fun listBooksReportsTheTotal() {
        val list = ListBooks(FakeCatalog(listOf(Book("1", "A"), Book("2", "B"))))
        assertEquals(2, list().total)
    }

    @Test
    fun findBookReturnsNullWhenMissing() {
        assertNull(FindBook(FakeCatalog(listOf(Book("1", "A"))))("nope"))
    }

    @Test
    fun blankSearchDoesNotCallTheUpstream() = runBlocking {
        val search = FakeSearch(emptyList())
        val response = SearchReleases(search)("   ")

        assertEquals(0, search.calls)
        assertTrue(response.results.isEmpty())
    }

    @Test
    fun searchPassesTheSourceFilter() = runBlocking {
        val search = FakeSearch(emptyList())

        SearchReleases(search)("x", indexerIds = listOf(2, 5))

        assertEquals(listOf(2, 5), search.lastIndexerIds)
    }

    @Test
    fun listIndexersReturnsTheCatalog() = runBlocking {
        val catalog = FakeIndexers(
            listOf(Indexer(1, "Nyaa.si", protocol = "torrent"), Indexer(2, "Other")),
        )

        val response = ListIndexers(catalog)()

        assertEquals(listOf("Nyaa.si", "Other"), response.indexers.map { it.name })
        assertEquals("torrent", response.indexers.first().protocol)
    }

    @Test
    fun searchFillsFormatsFromTheDetailPage() = runBlocking {
        val found = SearchResult("a", "magnet:?x", infoUrl = "https://nyaa.si/view/1")
        val files = FakeFiles(mapOf("https://nyaa.si/view/1" to listOf("EPUB")))

        val response = SearchReleases(FakeSearch(listOf(found)), files)("x")

        assertEquals(listOf("EPUB"), response.results.single().formats)
    }

    @Test
    fun searchSkipsTheDetailPageWhenTheNameNamesAFormat() = runBlocking {
        val found = SearchResult(
            "a",
            "magnet:?x",
            infoUrl = "https://nyaa.si/view/1",
            formats = listOf("PDF"),
        )
        val files = FakeFiles(emptyMap())

        SearchReleases(FakeSearch(listOf(found)), files)("x")

        assertEquals(0, files.calls)
    }

    @Test
    fun searchEnrichesOnlyTheHead() = runBlocking {
        val found = (1..12).map {
            SearchResult("$it", "magnet:?x$it", infoUrl = "https://nyaa.si/view/$it")
        }
        val files = FakeFiles(found.associate { it.infoUrl!! to listOf("EPUB") })

        val response = SearchReleases(FakeSearch(found), files)("x")

        assertEquals(10, files.calls)
        assertTrue(response.results.take(10).all { it.formats == listOf("EPUB") })
        assertTrue(response.results.drop(10).all { it.formats.isEmpty() })
    }

    @Test
    fun startGrabRejectsABlankUrl() {
        // assertFailsWith returns the exception, so this test needs a block body
        // to stay a Unit-returning JUnit method.
        runBlocking {
            assertFailsWith<IllegalArgumentException> { StartGrab(FakeQueue())("  ") }
        }
    }

    @Test
    fun startGrabReturnsTheUpdatedQueue() = runBlocking {
        val queue = FakeQueue()
        val response = StartGrab(queue)("magnet:?xt=1")
        assertEquals(1, response.grabs.size)
    }

    /** qBittorrent can lag behind add(); the use case must not report an empty queue. */
    @Test
    fun startGrabWaitsForALaggingQueue() = runBlocking {
        val lagging = object : GrabQueue {
            private val items = mutableListOf<Grab>()
            private var pending: String? = null
            private var pollsLeft = 0

            override suspend fun list(): List<Grab> {
                val waiting = pending
                if (waiting != null) {
                    if (pollsLeft == 0) {
                        items += Grab(hash = "h", name = waiting, state = "downloading", progress = 0.0)
                        pending = null
                    } else {
                        pollsLeft--
                    }
                }
                return items.toList()
            }

            override suspend fun add(downloadUrl: String, category: String?) {
                pending = downloadUrl
                pollsLeft = 2
            }

            override suspend fun remove(hash: String) {
                items.removeAll { it.hash == hash }
            }
        }

        val response = StartGrab(lagging, settleMillis = 1_000, pollMillis = 10)("magnet:?xt=lag")

        assertEquals(1, response.grabs.size)
    }

    @Test
    fun stopGrabRemovesOnlyThatHash() = runBlocking {
        val queue = FakeQueue()
        StartGrab(queue)("magnet:?xt=1")
        StartGrab(queue)("magnet:?xt=2")

        StopGrab(queue)("hash-0")

        val remaining = ListGrabs(queue)().grabs
        assertEquals(1, remaining.size)
        assertEquals("hash-1", remaining.single().hash)
    }
}
