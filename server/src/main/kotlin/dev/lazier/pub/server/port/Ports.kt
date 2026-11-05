package dev.lazier.pub.server.port

import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.Grab
import dev.lazier.pub.shared.Indexer
import dev.lazier.pub.shared.ReadingProgress
import dev.lazier.pub.shared.SearchResult
import dev.lazier.pub.shared.Shelf
import java.nio.file.Path

/**
 * Ports: what the application needs from the outside world.
 *
 * Both directions depend on these interfaces, never on each other. The inbound
 * adapter (HTTP) calls use cases; the use cases call these ports; the outbound
 * adapters implement them.
 */

/** A book plus where its bytes live. */
data class StoredBook(val book: Book, val path: Path)

interface BookCatalog {
    fun all(): List<Book>
    fun find(id: String): StoredBook?

    /** Cheap count for health checks. Must not read book metadata. */
    fun count(): Int = all().size
}

interface ReleaseSearch {
    suspend fun search(
        query: String,
        limit: Int,
        indexerIds: List<Int> = emptyList(),
    ): List<SearchResult>
}

/** The indexers Prowlarr can search, for the browse source picker. */
interface IndexerCatalog {
    suspend fun list(): List<Indexer>
}

/**
 * File formats read from a release's detail page, when the indexer publishes
 * one. Prowlarr's search response does not include the files inside a torrent.
 */
interface ReleaseFiles {
    suspend fun formats(infoUrl: String): List<String>
}

interface GrabQueue {
    suspend fun list(): List<Grab>
    suspend fun add(downloadUrl: String, category: String? = null)
    suspend fun remove(hash: String)
}

/** A cover image, ready to stream. */
data class Cover(
    val bytes: ByteArray,
    val contentType: String,
    val etag: String? = null,
)

interface CoverProvider {
    /**
     * Returns null when the book has no cover, or the format has none.
     * A width asks for a downscaled variant; null returns the original bytes.
     */
    suspend fun cover(id: String, width: Int? = null): Cover?
}

/**
 * Metadata read from inside a book file. Every field is optional: the catalog
 * falls back to the path for anything the file does not declare.
 *
 * `hasCover` is not metadata. It lives here so the catalog can answer "is a
 * cover worth requesting" without opening the archive a second time.
 */
data class EmbeddedMetadata(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val volume: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val published: String? = null,
    val description: String? = null,
    val hasCover: Boolean = false,
)

interface MetadataReader {
    /** Returns null when the file has no metadata the server can read. */
    fun read(path: Path): EmbeddedMetadata?
}

/** Reading positions, keyed by book id. */
interface ProgressStore {
    fun all(): List<ReadingProgress>
    fun get(bookId: String): ReadingProgress?
    fun put(progress: ReadingProgress)
    fun delete(bookId: String): Boolean
}

/** Manual collections. */
interface ShelfStore {
    fun all(): List<Shelf>
    fun find(id: String): Shelf?
    fun create(name: String): Shelf
    fun delete(id: String): Boolean
    fun add(shelfId: String, bookId: String): Shelf?
    fun remove(shelfId: String, bookId: String): Shelf?
}
