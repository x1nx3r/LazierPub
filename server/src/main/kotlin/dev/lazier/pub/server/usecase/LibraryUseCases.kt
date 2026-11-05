package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.NotFoundException
import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.ProgressStore
import dev.lazier.pub.server.port.ShelfStore
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.shared.BooksResponse
import dev.lazier.pub.shared.ContinueItem
import dev.lazier.pub.shared.ContinueResponse
import dev.lazier.pub.shared.SeriesResponse
import dev.lazier.pub.shared.SeriesSummary
import dev.lazier.pub.shared.StatsResponse

enum class BookSort { TITLE, AUTHOR, SERIES, ADDED, SIZE }

/**
 * One library query. The route parses it, this use case applies it.
 *
 * Filtering and paging live here so every client sees the same rules.
 */
data class BookQuery(
    val text: String? = null,
    val kinds: Set<BookKind> = emptySet(),
    val series: String? = null,
    val author: String? = null,
    val shelf: String? = null,
    val sort: BookSort = BookSort.TITLE,
    val descending: Boolean = false,
    val limit: Int = 100,
    val offset: Int = 0,
)

class QueryBooks(
    private val catalog: BookCatalog,
    private val shelves: ShelfStore,
) {

    operator fun invoke(query: BookQuery): BooksResponse {
        var books = catalog.all()

        query.shelf?.trim()?.takeIf { it.isNotEmpty() }?.let { shelfId ->
            val shelf = shelves.find(shelfId) ?: throw NotFoundException("No such shelf")
            val members = shelf.bookIds.toSet()
            books = books.filter { it.id in members }
        }

        query.text?.trim()?.takeIf { it.isNotEmpty() }?.let { needle ->
            books = books.filter { book ->
                book.title.contains(needle, ignoreCase = true) ||
                    book.author?.contains(needle, ignoreCase = true) == true ||
                    book.series?.contains(needle, ignoreCase = true) == true
            }
        }

        if (query.kinds.isNotEmpty()) {
            books = books.filter { it.kind in query.kinds }
        }

        query.series?.trim()?.takeIf { it.isNotEmpty() }?.let { wanted ->
            books = books.filter { it.series?.equals(wanted, ignoreCase = true) == true }
        }

        query.author?.trim()?.takeIf { it.isNotEmpty() }?.let { wanted ->
            books = books.filter { it.author?.contains(wanted, ignoreCase = true) == true }
        }

        val comparator = comparatorFor(query.sort)
        books = if (query.descending) books.sortedWith(comparator.reversed()) else books.sortedWith(comparator)

        val total = books.size
        val limit = query.limit.coerceIn(1, MAX_LIMIT)
        val offset = query.offset.coerceAtLeast(0)

        return BooksResponse(
            books = books.drop(offset).take(limit),
            total = total,
            limit = limit,
            offset = offset,
        )
    }

    private fun comparatorFor(sort: BookSort): Comparator<Book> = when (sort) {
        BookSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        BookSort.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.author ?: "\uFFFF" }
        BookSort.SERIES -> compareBy<Book>(
            { it.series?.lowercase() ?: "\uFFFF" },
            { volumeKey(it.volume) },
            { it.title.lowercase() },
        )
        BookSort.ADDED -> compareBy { it.addedAt ?: 0L }
        BookSort.SIZE -> compareBy { it.sizeBytes ?: 0L }
    }

    companion object {
        const val MAX_LIMIT = 500

        /** Numeric volumes sort in numeric order; "10" must follow "9". */
        fun volumeKey(volume: String?): String {
            val number = volume?.trim()?.toIntOrNull()
            return if (number != null) "0${number.toString().padStart(9, '0')}" else "1${volume.orEmpty()}"
        }
    }
}

/** Series are derived from books, never stored. */
class ListSeries(private val catalog: BookCatalog) {

    operator fun invoke(text: String? = null): SeriesResponse {
        val needle = text?.trim()?.takeIf { it.isNotEmpty() }

        val series = catalog.all()
            .mapNotNull { book -> book.series?.let { it to book } }
            .groupBy({ it.first }, { it.second })
            .filterKeys { name -> needle == null || name.contains(needle, ignoreCase = true) }
            .map { (name, books) ->
                val cover = books
                    .filter { it.hasCover }
                    .minByOrNull { QueryBooks.volumeKey(it.volume) }
                    ?: books.minByOrNull { QueryBooks.volumeKey(it.volume) }

                SeriesSummary(
                    name = name,
                    count = books.size,
                    coverBookId = cover?.id,
                    kinds = books.map { it.kind }.distinct().sortedBy { it.ordinal },
                )
            }
            .sortedBy { it.name.lowercase() }

        return SeriesResponse(series = series, total = series.size)
    }
}

class LibraryStats(private val catalog: BookCatalog) {

    operator fun invoke(): StatsResponse {
        val books = catalog.all()
        return StatsResponse(
            books = books.size,
            epub = books.count { it.kind == BookKind.EPUB },
            pdf = books.count { it.kind == BookKind.PDF },
            other = books.count { it.kind != BookKind.EPUB && it.kind != BookKind.PDF },
            series = books.mapNotNull { it.series }.distinct().size,
            authors = books.mapNotNull { it.author }.distinct().size,
            totalBytes = books.sumOf { it.sizeBytes ?: 0L },
            withCovers = books.count { it.hasCover },
        )
    }
}

/**
 * Reading positions joined with their books, most recent first.
 *
 * A position whose book left the library is skipped: the home screen should not
 * show an entry that cannot open.
 */
class ContinueReading(
    private val catalog: BookCatalog,
    private val progress: ProgressStore,
) {

    operator fun invoke(limit: Int = 20, includeFinished: Boolean = false): ContinueResponse {
        val books = catalog.all().associateBy { it.id }
        val items = progress.all()
            .asSequence()
            .filter { includeFinished || !it.finished }
            .mapNotNull { entry -> books[entry.bookId]?.let { ContinueItem(it, entry) } }
            .take(limit.coerceIn(1, MAX_LIMIT))
            .toList()

        return ContinueResponse(items)
    }

    private companion object {
        const val MAX_LIMIT = 100
    }
}
