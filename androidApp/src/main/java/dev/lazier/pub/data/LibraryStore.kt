package dev.lazier.pub.data

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.lazier.pub.shared.ApiJson
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.shared.ContinueItem
import dev.lazier.pub.shared.Grab
import dev.lazier.pub.shared.ProgressRequest
import dev.lazier.pub.shared.ReadingProgress
import dev.lazier.pub.shared.SeriesSummary
import dev.lazier.pub.shared.StatsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * One copy of the library for the whole app.
 *
 * Compose observes these values, so a refresh redraws every screen that is
 * open. The server remains the source of truth; nothing here is persisted.
 */
object LibraryStore {

    var books by mutableStateOf<List<Book>>(emptyList())
        private set
    var series by mutableStateOf<List<SeriesSummary>>(emptyList())
        private set
    var stats by mutableStateOf<StatsResponse?>(null)
        private set
    var continueItems by mutableStateOf<List<ContinueItem>>(emptyList())
        private set
    var progress by mutableStateOf<Map<String, ReadingProgress>>(emptyMap())
        private set
    var grabs by mutableStateOf<List<Grab>>(emptyList())
        private set
    var grabsError by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    fun book(id: String): Book? = books.firstOrNull { it.id == id }

    /**
     * What the library screens may show. Hiding a format is a display rule, so
     * the raw lists stay intact: [book] still resolves a direct route, and the
     * setting can bring the files back without another request.
     */
    val visibleBooks: List<Book> by derivedStateOf {
        if (Settings.showPdf) books else books.filter { it.kind == BookKind.EPUB }
    }

    val visibleSeries: List<SeriesSummary> by derivedStateOf {
        if (Settings.showPdf) {
            series
        } else {
            // Keep the server's series order and covers, but recount volumes
            // from the books that remain visible.
            val grouped = visibleBooks
                .filter { !it.series.isNullOrBlank() }
                .groupBy { it.series!!.lowercase() }
            series.mapNotNull { summary ->
                val shown = grouped[summary.name.lowercase()] ?: return@mapNotNull null
                summary.copy(
                    count = shown.size,
                    coverBookId = shown.firstOrNull { it.hasCover }?.id ?: shown.first().id,
                    kinds = shown.map { it.kind }.distinct().sortedBy { it.ordinal },
                )
            }
        }
    }

    val visibleContinueItems: List<ContinueItem> by derivedStateOf {
        if (Settings.showPdf) continueItems else continueItems.filter { it.book.kind == BookKind.EPUB }
    }

    fun progressFor(id: String): ReadingProgress? = progress[id]

    fun booksInSeries(name: String): List<Book> =
        visibleBooks.filter { it.series.equals(name, ignoreCase = true) }
            // EPUB and PDF twins of one title: show the readable one.
            .groupBy { it.title }
            .map { (_, group) -> group.firstOrNull { it.kind == BookKind.EPUB } ?: group.first() }
            .sortedWith(compareBy<Book>({ volumeKey(it.volume) }, { it.title }))

    suspend fun refresh() {
        if (loading) return
        loading = true
        error = null
        try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val booksCall = async { Api.books() }
                    val seriesCall = async { Api.series() }
                    val statsCall = async { Api.stats() }
                    val continueCall = async { Api.continueReading() }
                    val progressCall = async { Api.progress() }

                    books = booksCall.await().books
                    series = seriesCall.await().series
                    stats = statsCall.await()
                    continueItems = continueCall.await().items
                    progress = progressCall.await().associateBy { it.bookId }
                }
            }
            loaded = true
        } catch (failure: Exception) {
            error = failure.message ?: "Could not reach the server."
        } finally {
            loading = false
        }
    }

    suspend fun refreshProgress() = withContext(Dispatchers.IO) {
        runCatching {
            progress = Api.progress().associateBy { it.bookId }
            continueItems = Api.continueReading().items
        }
    }

    suspend fun refreshGrabs() = withContext(Dispatchers.IO) {
        runCatching { Api.grabs().grabs }
            .onSuccess {
                grabs = it
                grabsError = null
            }
            .onFailure {
                grabsError = it.message ?: "Could not reach qBittorrent."
            }
    }

    /** The reader calls this; a failure must never interrupt reading. */
    suspend fun saveProgress(
        bookId: String,
        locatorJson: String?,
        percent: Double,
        chapter: String?,
        finished: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val locator = locatorJson?.let { ApiJson.parseToJsonElement(it) }
                val saved = Api.putProgress(
                    bookId,
                    ProgressRequest(locator = locator, percent = percent, chapter = chapter, finished = finished),
                )
                progress = progress + (bookId to saved)
            }
        }
    }

    suspend fun shelves(): List<dev.lazier.pub.shared.Shelf> =
        withContext(Dispatchers.IO) { Api.shelves() }

    fun volumeKey(volume: String?): String {
        val number = volume?.trim()?.toIntOrNull()
        return if (number != null) "0${number.toString().padStart(9, '0')}" else "1${volume.orEmpty()}"
    }
}
