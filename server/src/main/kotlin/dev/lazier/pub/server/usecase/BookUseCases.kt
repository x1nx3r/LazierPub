package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.StoredBook
import dev.lazier.pub.shared.BooksResponse

/** List every book the catalog knows about. */
class ListBooks(private val catalog: BookCatalog) {
    operator fun invoke(): BooksResponse {
        val books = catalog.all()
        return BooksResponse(books = books, total = books.size)
    }
}

/** Find one book, including the file the HTTP adapter will stream. */
class FindBook(private val catalog: BookCatalog) {
    operator fun invoke(id: String): StoredBook? = catalog.find(id)
}
