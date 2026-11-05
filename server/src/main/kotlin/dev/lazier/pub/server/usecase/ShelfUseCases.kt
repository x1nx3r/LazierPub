package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.NotFoundException
import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.ShelfStore
import dev.lazier.pub.shared.Shelf
import dev.lazier.pub.shared.ShelvesResponse

class ListShelves(private val store: ShelfStore) {
    operator fun invoke(): ShelvesResponse = ShelvesResponse(store.all())
}

class CreateShelf(private val store: ShelfStore) {
    operator fun invoke(name: String): Shelf = store.create(name)
}

class DeleteShelf(private val store: ShelfStore) {
    operator fun invoke(id: String) {
        if (!store.delete(id)) throw NotFoundException("No such shelf")
    }
}

/**
 * Add and remove are idempotent; both return the updated shelf.
 *
 * The book must exist in the catalog, so a shelf cannot collect ids that will
 * never resolve.
 */
class AddShelf(
    private val store: ShelfStore,
    private val catalog: BookCatalog,
) {

    operator fun invoke(shelfId: String, bookId: String): Shelf {
        if (store.find(shelfId) == null) throw NotFoundException("No such shelf")
        if (catalog.find(bookId) == null) throw NotFoundException("No such book")
        return store.add(shelfId, bookId) ?: throw NotFoundException("No such shelf")
    }
}

class RemoveBookFromShelf(private val store: ShelfStore) {
    operator fun invoke(shelfId: String, bookId: String): Shelf =
        store.remove(shelfId, bookId) ?: throw NotFoundException("No such shelf")
}
