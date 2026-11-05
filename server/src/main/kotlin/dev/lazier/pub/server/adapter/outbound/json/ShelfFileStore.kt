package dev.lazier.pub.server.adapter.outbound.json

import dev.lazier.pub.server.ConflictException
import dev.lazier.pub.server.port.ShelfStore
import dev.lazier.pub.shared.Shelf
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.UUID

@Serializable
private data class ShelfFile(val shelves: List<Shelf> = emptyList())

/**
 * Manual collections in one JSON file.
 *
 * One user, one writer, no ordering guarantees beyond insertion order. A shelf
 * is a name and an ordered set of book ids; membership is checked by the
 * caller before an add.
 */
class ShelfFileStore(private val file: Path) : ShelfStore {

    private val lock = Any()
    private var loaded = false
    private val shelves = LinkedHashMap<String, Shelf>()

    override fun all(): List<Shelf> = synchronized(lock) {
        load()
        shelves.values.toList()
    }

    override fun find(id: String): Shelf? = synchronized(lock) {
        load()
        shelves[id]
    }

    override fun create(name: String): Shelf = synchronized(lock) {
        load()
        val clean = name.trim()
        require(clean.isNotEmpty()) { "name is required" }
        require(clean.length <= MAX_NAME) { "name is longer than $MAX_NAME characters" }
        if (shelves.values.any { it.name.equals(clean, ignoreCase = true) }) {
            throw ConflictException("A shelf named \"$clean\" already exists")
        }

        val shelf = Shelf(
            id = UUID.randomUUID().toString().replace("-", "").take(ID_LENGTH),
            name = clean,
            createdAt = System.currentTimeMillis(),
        )
        shelves[shelf.id] = shelf
        save()
        shelf
    }

    override fun delete(id: String): Boolean = synchronized(lock) {
        load()
        val removed = shelves.remove(id) != null
        if (removed) save()
        removed
    }

    override fun add(shelfId: String, bookId: String): Shelf? = synchronized(lock) {
        load()
        val shelf = shelves[shelfId] ?: return null
        if (bookId in shelf.bookIds) return shelf

        val updated = shelf.copy(bookIds = shelf.bookIds + bookId)
        shelves[shelfId] = updated
        save()
        updated
    }

    override fun remove(shelfId: String, bookId: String): Shelf? = synchronized(lock) {
        load()
        val shelf = shelves[shelfId] ?: return null
        if (bookId !in shelf.bookIds) return shelf

        val updated = shelf.copy(bookIds = shelf.bookIds - bookId)
        shelves[shelfId] = updated
        save()
        updated
    }

    private fun load() {
        if (loaded) return
        loaded = true
        JsonFiles.read(file, ShelfFile.serializer())
            ?.shelves
            ?.forEach { shelves[it.id] = it }
    }

    private fun save() {
        JsonFiles.write(file, ShelfFile.serializer(), ShelfFile(shelves.values.toList()))
    }

    private companion object {
        const val MAX_NAME = 80
        const val ID_LENGTH = 12
    }
}
