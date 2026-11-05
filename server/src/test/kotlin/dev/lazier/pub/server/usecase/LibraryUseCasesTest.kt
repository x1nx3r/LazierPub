package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.NotFoundException
import dev.lazier.pub.server.UnauthorizedException
import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.ProgressStore
import dev.lazier.pub.server.port.ShelfStore
import dev.lazier.pub.server.port.StoredBook
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.shared.LoginRequest
import dev.lazier.pub.shared.ProgressRequest
import dev.lazier.pub.shared.ReadingProgress
import dev.lazier.pub.shared.Shelf
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryUseCasesTest {

    private val books = listOf(
        Book(
            id = "sao-1", title = "Sword Art Online, Vol. 01: Aincrad", author = "Reki Kawahara",
            kind = BookKind.EPUB, series = "Sword Art Online", volume = "1", sizeBytes = 100,
            addedAt = 10, hasCover = true,
        ),
        Book(
            id = "sao-2", title = "Sword Art Online, Vol. 02: Aincrad", author = "Reki Kawahara",
            kind = BookKind.EPUB, series = "Sword Art Online", volume = "2", sizeBytes = 200,
            addedAt = 20, hasCover = false,
        ),
        Book(
            id = "sao-10", title = "Sword Art Online, Vol. 10: Alicization", author = "Reki Kawahara",
            kind = BookKind.PDF, series = "Sword Art Online", volume = "10", sizeBytes = 300,
            addedAt = 30, hasCover = false,
        ),
        Book(
            id = "index-1", title = "A Certain Magical Index - Volume 01", author = "Kazuma Kamachi",
            kind = BookKind.EPUB, series = "A Certain Magical Index", volume = "1", sizeBytes = 400,
            addedAt = 40, hasCover = true,
        ),
    )

    private fun query(shelves: ShelfStore = FakeShelves()) = QueryBooks(FakeCatalog(books), shelves)

    @Test
    fun filtersByTextAcrossTitleAuthorAndSeries() {
        val books = query()(BookQuery(text = "kamachi")).books
        assertEquals(listOf("index-1"), books.map { it.id })

        val bySeries = query()(BookQuery(text = "sword art")).books
        assertEquals(3, bySeries.size)
    }

    @Test
    fun filtersByKindAndSeries() {
        val pdfs = query()(BookQuery(kinds = setOf(BookKind.PDF))).books
        assertEquals(listOf("sao-10"), pdfs.map { it.id })

        val series = query()(BookQuery(series = "sword art online")).books
        assertEquals(3, series.size)
    }

    @Test
    fun reportsTheFilteredTotalBeforePaging() {
        val response = query()(BookQuery(sort = BookSort.TITLE, limit = 2, offset = 1))

        assertEquals(4, response.total)
        assertEquals(2, response.limit)
        assertEquals(1, response.offset)
        assertEquals(2, response.books.size)
    }

    @Test
    fun clampsTheLimit() {
        val response = query()(BookQuery(limit = 10_000))
        assertEquals(QueryBooks.MAX_LIMIT, response.limit)
    }

    @Test
    fun sortsVolumesNumerically() {
        val sorted = query()(BookQuery(sort = BookSort.SERIES)).books
            .filter { it.series == "Sword Art Online" }
            .map { it.volume }

        assertEquals(listOf("1", "2", "10"), sorted)
    }

    @Test
    fun filtersByShelf() {
        val shelves = FakeShelves().apply {
            shelves += Shelf(id = "s1", name = "Favourites", bookIds = listOf("sao-2", "index-1"))
        }

        val response = QueryBooks(FakeCatalog(books), shelves)(BookQuery(shelf = "s1"))

        assertEquals(setOf("sao-2", "index-1"), response.books.map { it.id }.toSet())
    }

    @Test
    fun rejectsAnUnknownShelf() {
        assertFailsWith<NotFoundException> {
            query()(BookQuery(shelf = "missing"))
        }
    }

    @Test
    fun buildsSeriesSummariesWithACover() {
        val series = ListSeries(FakeCatalog(books))()

        assertEquals(setOf("A Certain Magical Index", "Sword Art Online"), series.series.map { it.name }.toSet())
        val sao = series.series.single { it.name == "Sword Art Online" }
        assertEquals(3, sao.count)
        assertEquals("sao-1", sao.coverBookId)
        assertEquals(listOf(BookKind.EPUB, BookKind.PDF), sao.kinds)
    }

    @Test
    fun countsLibraryStatistics() {
        val stats = LibraryStats(FakeCatalog(books))()

        assertEquals(4, stats.books)
        assertEquals(3, stats.epub)
        assertEquals(1, stats.pdf)
        assertEquals(2, stats.series)
        assertEquals(2, stats.authors)
        assertEquals(1000, stats.totalBytes)
        assertEquals(2, stats.withCovers)
    }

    @Test
    fun continueReadingSkipsBooksThatLeftTheLibrary() {
        val progress = FakeProgress(
            ReadingProgress(bookId = "sao-1", percent = 0.5, updatedAt = 10),
            ReadingProgress(bookId = "gone", percent = 0.9, updatedAt = 30),
            ReadingProgress(bookId = "sao-2", percent = 0.2, updatedAt = 20),
        )

        val items = ContinueReading(FakeCatalog(books), progress)()

        assertEquals(listOf("sao-2", "sao-1"), items.items.map { it.book.id })
    }

    @Test
    fun continueReadingHidesFinishedBooksByDefault() {
        val progress = FakeProgress(
            ReadingProgress(bookId = "sao-1", percent = 1.0, finished = true, updatedAt = 10),
            ReadingProgress(bookId = "sao-2", percent = 0.2, finished = false, updatedAt = 20),
        )
        val useCase = ContinueReading(FakeCatalog(books), progress)

        assertEquals(listOf("sao-2"), useCase().items.map { it.book.id })
        assertEquals(2, useCase(includeFinished = true).items.size)
    }

    @Test
    fun savingProgressClampsAndStamps() {
        val store = FakeProgress()
        val save = SaveProgress(FakeCatalog(books), store, clock = { 1234L })

        val saved = save("sao-1", ProgressRequest(percent = 4.2, chapter = "  Aincrad  "))

        assertEquals(1.0, saved.percent)
        assertEquals("Aincrad", saved.chapter)
        assertEquals(1234L, saved.updatedAt)
        assertEquals(saved, store.get("sao-1"))
    }

    @Test
    fun savingProgressRejectsAnUnknownBook() {
        assertFailsWith<NotFoundException> {
            SaveProgress(FakeCatalog(books), FakeProgress())("ghost", ProgressRequest())
        }
    }

    @Test
    fun deletingMissingProgressIsANotFound() {
        assertFailsWith<NotFoundException> {
            DeleteProgress(FakeProgress())("sao-1")
        }
    }

    @Test
    fun loginAcceptsTheConfiguredPasswordOnly() {
        val login = Login(token = "secret-token", password = "hunter2")

        assertEquals("secret-token", login(LoginRequest("hunter2")).token)
        assertFailsWith<UnauthorizedException> { login(LoginRequest("nope")) }
    }

    @Test
    fun loginFallsBackToTheTokenAsPassword() {
        val login = Login(token = "secret-token", password = null)

        assertEquals("secret-token", login(LoginRequest("secret-token")).token)
        assertFailsWith<UnauthorizedException> { login(LoginRequest("hunter2")) }
    }

    @Test
    fun shelvesAddAndRemoveIdempotently() {
        val shelves = FakeShelves()
        val add = AddShelf(shelves, FakeCatalog(books))
        val remove = RemoveBookFromShelf(shelves)

        val shelf = shelves.create("Favourites")
        assertEquals(1, add(shelf.id, "sao-1").bookIds.size)
        assertEquals(1, add(shelf.id, "sao-1").bookIds.size)
        assertEquals(0, remove(shelf.id, "sao-1").bookIds.size)
        assertNull(shelves.find("missing"))
    }

    @Test
    fun shelvingAnUnknownBookFails() {
        val shelves = FakeShelves()
        val shelf = shelves.create("Favourites")

        assertFailsWith<NotFoundException> {
            AddShelf(shelves, FakeCatalog(books))(shelf.id, "ghost")
        }
    }

    // --- fakes ---------------------------------------------------------

    private class FakeCatalog(private val books: List<Book>) : BookCatalog {
        override fun all(): List<Book> = books

        override fun find(id: String): StoredBook? =
            books.firstOrNull { it.id == id }?.let { StoredBook(it, Paths.get("/tmp/${it.id}.epub")) }

        override fun count(): Int = books.size
    }

    private class FakeProgress(vararg initial: ReadingProgress) : ProgressStore {
        private val entries = initial.associateBy { it.bookId }.toMutableMap()

        override fun all(): List<ReadingProgress> = entries.values.sortedByDescending { it.updatedAt }
        override fun get(bookId: String): ReadingProgress? = entries[bookId]
        override fun put(progress: ReadingProgress) {
            entries[progress.bookId] = progress
        }

        override fun delete(bookId: String): Boolean = entries.remove(bookId) != null
    }

    private class FakeShelves : ShelfStore {
        val shelves = mutableListOf<Shelf>()
        private var counter = 0

        override fun all(): List<Shelf> = shelves.toList()
        override fun find(id: String): Shelf? = shelves.firstOrNull { it.id == id }

        override fun create(name: String): Shelf {
            val shelf = Shelf(id = "s${++counter}", name = name.trim())
            shelves += shelf
            return shelf
        }

        override fun delete(id: String): Boolean = shelves.removeAll { it.id == id }

        override fun add(shelfId: String, bookId: String): Shelf? {
            val shelf = find(shelfId) ?: return null
            if (bookId in shelf.bookIds) return shelf
            val updated = shelf.copy(bookIds = shelf.bookIds + bookId)
            shelves[shelves.indexOf(shelf)] = updated
            return updated
        }

        override fun remove(shelfId: String, bookId: String): Shelf? {
            val shelf = find(shelfId) ?: return null
            val updated = shelf.copy(bookIds = shelf.bookIds - bookId)
            shelves[shelves.indexOf(shelf)] = updated
            return updated
        }
    }

    @Test
    fun fakeCatalogAndStoresBehaveForTheTests() {
        // Guards against a fake that silently stops matching the port.
        assertTrue(FakeCatalog(books).count() == 4)
    }
}
