package dev.lazier.pub.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The contract between the server and every client.
 *
 * Both sides compile against these types, so a field rename breaks the build
 * instead of breaking a phone at runtime.
 */

/**
 * The format family. EPUB and CBZ are both ZIP archives, so the server confirms
 * the kind from the archive contents when it can.
 */
@Serializable
enum class BookKind {
    EPUB,
    PDF,
    CBZ,
    CBR,
    MOBI,
    AZW3,
    UNKNOWN,
    ;

    companion object {
        /** Maps a file extension to a kind. Unknown extensions return UNKNOWN. */
        fun fromExtension(extension: String): BookKind = when (extension.lowercase()) {
            "epub" -> EPUB
            "pdf" -> PDF
            "cbz" -> CBZ
            "cbr" -> CBR
            "mobi" -> MOBI
            "azw3" -> AZW3
            else -> UNKNOWN
        }

        /** True for kinds the reader can open today. */
        val readable: Set<BookKind> = setOf(EPUB, PDF)
    }
}

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String? = null,
    val kind: BookKind = BookKind.UNKNOWN,
    val series: String? = null,
    val volume: String? = null,
    val sizeBytes: Long? = null,
    /** File modification time in epoch milliseconds. Drives "recently added". */
    val addedAt: Long? = null,
    val language: String? = null,
    val publisher: String? = null,
    val publishedAt: String? = null,
    val description: String? = null,
    /** True when the server can serve a cover for this book. */
    val hasCover: Boolean = false,
)

@Serializable
data class BooksResponse(
    val books: List<Book>,
    val total: Int = books.size,
    val limit: Int? = null,
    val offset: Int = 0,
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val booksDir: String,
    val bookCount: Int,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val detail: String? = null,
)

@Serializable
data class SearchResult(
    val title: String,
    val downloadUrl: String,
    val indexer: String? = null,
    val indexerId: Int? = null,
    val protocol: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val grabs: Int? = null,
    val infoUrl: String? = null,
    val publishDate: String? = null,
    val categories: List<String> = emptyList(),
    /**
     * Uppercase file extensions named by the release, e.g. EPUB or PDF.
     * Empty when the release name does not say, which is common.
     */
    val formats: List<String> = emptyList(),
    val freeleech: Boolean = false,
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResult>,
)

/** One Prowlarr indexer, offered as a browse source. */
@Serializable
data class Indexer(
    val id: Int,
    val name: String,
    val protocol: String? = null,
    val language: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class IndexersResponse(
    val indexers: List<Indexer>,
)

@Serializable
data class GrabRequest(
    val downloadUrl: String,
    val title: String? = null,
    /** qBittorrent category. Defaults to the server's configured category. */
    val category: String? = null,
)

@Serializable
data class Grab(
    val hash: String,
    val name: String,
    val state: String,
    val progress: Double,
    val sizeBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val seeders: Int? = null,
    val savePath: String? = null,
    val category: String? = null,
    val etaSeconds: Long? = null,
)

@Serializable
data class GrabsResponse(
    val grabs: List<Grab>,
)

@Serializable
data class ConfigResponse(
    val version: String,
    val booksDir: String,
    val bookCount: Int,
    val tokenRequired: Boolean,
    val prowlarrConfigured: Boolean,
    val qbittorrentConfigured: Boolean,
    val category: String,
)

/** A series derived from book metadata. No stored state. */
@Serializable
data class SeriesSummary(
    val name: String,
    val count: Int,
    val coverBookId: String? = null,
    val kinds: List<BookKind> = emptyList(),
)

@Serializable
data class SeriesResponse(val series: List<SeriesSummary>, val total: Int = series.size)

@Serializable
data class StatsResponse(
    val books: Int,
    val epub: Int,
    val pdf: Int,
    val other: Int,
    val series: Int,
    val authors: Int,
    val totalBytes: Long,
    val withCovers: Int,
)

/**
 * A reading position. The locator is Readium's `Locator` JSON, stored verbatim,
 * because it survives a change of font size. A page number does not.
 */
@Serializable
data class ReadingProgress(
    val bookId: String,
    val locator: JsonElement? = null,
    val percent: Double = 0.0,
    val chapter: String? = null,
    val finished: Boolean = false,
    val updatedAt: Long = 0,
)

@Serializable
data class ProgressResponse(val entries: List<ReadingProgress>)

@Serializable
data class ProgressRequest(
    val locator: JsonElement? = null,
    val percent: Double = 0.0,
    val chapter: String? = null,
    val finished: Boolean = false,
)

@Serializable
data class ContinueItem(val book: Book, val progress: ReadingProgress)

@Serializable
data class ContinueResponse(val items: List<ContinueItem>)

/** A manual collection. The store persists it; the catalog does not. */
@Serializable
data class Shelf(
    val id: String,
    val name: String,
    val bookIds: List<String> = emptyList(),
    val createdAt: Long = 0,
)

@Serializable
data class ShelvesResponse(val shelves: List<Shelf>)

@Serializable
data class CreateShelfRequest(val name: String)

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(val token: String, val expiresAt: Long? = null)

/** One JSON configuration for both ends of the contract. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * Explicit serializers, so consumers do not need the serialization compiler
 * plugin just to read a response.
 */
object BooksCodec {
    fun decodeBooks(json: String): BooksResponse =
        ApiJson.decodeFromString(BooksResponse.serializer(), json)

    fun encodeBooks(value: BooksResponse): String =
        ApiJson.encodeToString(BooksResponse.serializer(), value)

    fun decodeHealth(json: String): HealthResponse =
        ApiJson.decodeFromString(HealthResponse.serializer(), json)

    fun decodeConfig(json: String): ConfigResponse =
        ApiJson.decodeFromString(ConfigResponse.serializer(), json)

    fun decodeBook(json: String): Book =
        ApiJson.decodeFromString(Book.serializer(), json)

    fun decodeSeries(json: String): SeriesResponse =
        ApiJson.decodeFromString(SeriesResponse.serializer(), json)

    fun decodeStats(json: String): StatsResponse =
        ApiJson.decodeFromString(StatsResponse.serializer(), json)

    fun decodeContinue(json: String): ContinueResponse =
        ApiJson.decodeFromString(ContinueResponse.serializer(), json)

    fun decodeProgress(json: String): ReadingProgress =
        ApiJson.decodeFromString(ReadingProgress.serializer(), json)

    fun decodeProgressList(json: String): ProgressResponse =
        ApiJson.decodeFromString(ProgressResponse.serializer(), json)

    fun encodeProgress(value: ProgressRequest): String =
        ApiJson.encodeToString(ProgressRequest.serializer(), value)

    fun decodeShelves(json: String): ShelvesResponse =
        ApiJson.decodeFromString(ShelvesResponse.serializer(), json)

    fun decodeShelf(json: String): Shelf =
        ApiJson.decodeFromString(Shelf.serializer(), json)

    fun encodeShelf(value: CreateShelfRequest): String =
        ApiJson.encodeToString(CreateShelfRequest.serializer(), value)

    fun decodeSearch(json: String): SearchResponse =
        ApiJson.decodeFromString(SearchResponse.serializer(), json)

    fun decodeIndexers(json: String): IndexersResponse =
        ApiJson.decodeFromString(IndexersResponse.serializer(), json)

    fun decodeGrabs(json: String): GrabsResponse =
        ApiJson.decodeFromString(GrabsResponse.serializer(), json)

    fun encodeGrab(value: GrabRequest): String =
        ApiJson.encodeToString(GrabRequest.serializer(), value)

    fun encodeLogin(value: LoginRequest): String =
        ApiJson.encodeToString(LoginRequest.serializer(), value)

    fun decodeLogin(json: String): LoginResponse =
        ApiJson.decodeFromString(LoginResponse.serializer(), json)

    fun decodeError(json: String): ErrorResponse =
        ApiJson.decodeFromString(ErrorResponse.serializer(), json)
}
