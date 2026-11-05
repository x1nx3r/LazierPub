package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.NotFoundException
import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.ProgressStore
import dev.lazier.pub.shared.ProgressRequest
import dev.lazier.pub.shared.ProgressResponse
import dev.lazier.pub.shared.ReadingProgress

class ListProgress(private val store: ProgressStore) {
    operator fun invoke(): ProgressResponse = ProgressResponse(store.all())
}

class GetProgress(private val store: ProgressStore) {
    operator fun invoke(bookId: String): ReadingProgress =
        store.get(bookId) ?: throw NotFoundException("No progress for this book")
}

/**
 * Store a reading position.
 *
 * The book must exist, so a stale client cannot fill the store with ids that
 * will never resolve. The percentage is clamped and the timestamp is the
 * server's, not the caller's.
 */
class SaveProgress(
    private val catalog: BookCatalog,
    private val store: ProgressStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    operator fun invoke(bookId: String, request: ProgressRequest): ReadingProgress {
        if (catalog.find(bookId) == null) throw NotFoundException("No such book")

        val progress = ReadingProgress(
            bookId = bookId,
            locator = request.locator,
            percent = request.percent.coerceIn(0.0, 1.0),
            chapter = request.chapter?.trim()?.takeIf { it.isNotEmpty() },
            finished = request.finished,
            updatedAt = clock(),
        )
        store.put(progress)
        return progress
    }
}

class DeleteProgress(private val store: ProgressStore) {
    operator fun invoke(bookId: String) {
        if (!store.delete(bookId)) throw NotFoundException("No progress for this book")
    }
}
