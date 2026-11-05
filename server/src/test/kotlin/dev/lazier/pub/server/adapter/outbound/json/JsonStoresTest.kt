package dev.lazier.pub.server.adapter.outbound.json

import dev.lazier.pub.server.ConflictException
import dev.lazier.pub.shared.ReadingProgress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonStoresTest {

    @Test
    fun progressSurvivesANewStoreInstance() {
        val file = Files.createTempDirectory("progress").resolve("progress.json")

        ProgressFileStore(file).apply {
            put(ReadingProgress(bookId = "a", percent = 0.1, updatedAt = 10))
            put(ReadingProgress(bookId = "b", percent = 0.9, updatedAt = 20))
        }

        val reopened = ProgressFileStore(file)

        assertEquals(listOf("b", "a"), reopened.all().map { it.bookId })
        assertEquals(0.1, reopened.get("a")?.percent)
    }

    @Test
    fun progressDeletesAndStaysDeleted() {
        val file = Files.createTempDirectory("progress").resolve("progress.json")
        val store = ProgressFileStore(file)
        store.put(ReadingProgress(bookId = "a", updatedAt = 1))

        assertTrue(store.delete("a"))
        assertNull(ProgressFileStore(file).get("a"))
    }

    @Test
    fun aCorruptProgressFileReadsAsEmpty() {
        val file = Files.createTempDirectory("progress").resolve("progress.json")
        Files.writeString(file, "{ this is not json")

        assertEquals(0, ProgressFileStore(file).all().size)
    }

    @Test
    fun shelvesSurviveANewStoreInstance() {
        val file = Files.createTempDirectory("shelves").resolve("shelves.json")
        val created = ShelfFileStore(file).create("  Favourites  ")

        val reopened = ShelfFileStore(file)

        assertEquals("Favourites", created.name)
        assertEquals(12, created.id.length)
        assertEquals(listOf(created), reopened.all())
    }

    @Test
    fun shelfNamesAreUniqueIgnoringCase() {
        val store = ShelfFileStore(Files.createTempDirectory("shelves").resolve("shelves.json"))
        store.create("Favourites")

        assertFailsWith<ConflictException> { store.create("favourites") }
    }

    @Test
    fun shelfNamesAreValidated() {
        val store = ShelfFileStore(Files.createTempDirectory("shelves").resolve("shelves.json"))

        assertFailsWith<IllegalArgumentException> { store.create("   ") }
        assertFailsWith<IllegalArgumentException> { store.create("x".repeat(81)) }
    }

    @Test
    fun shelfMembersAreIdempotent() {
        val store = ShelfFileStore(Files.createTempDirectory("shelves").resolve("shelves.json"))
        val shelf = store.create("Reading")

        assertEquals(listOf("book-1"), store.add(shelf.id, "book-1")?.bookIds)
        assertEquals(listOf("book-1"), store.add(shelf.id, "book-1")?.bookIds)
        assertEquals(emptyList(), store.remove(shelf.id, "book-1")?.bookIds)
        assertEquals(emptyList(), store.remove(shelf.id, "book-1")?.bookIds)
        assertNull(store.add("missing", "book-1"))
    }

    @Test
    fun deletingAShelfIsPersisted() {
        val file = Files.createTempDirectory("shelves").resolve("shelves.json")
        val store = ShelfFileStore(file)
        val shelf = store.create("Temporary")

        assertTrue(store.delete(shelf.id))
        assertEquals(0, ShelfFileStore(file).all().size)
    }
}
