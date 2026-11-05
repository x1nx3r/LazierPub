package dev.lazier.pub.data

import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.shared.BooksCodec
import dev.lazier.pub.shared.BooksResponse
import dev.lazier.pub.shared.ConfigResponse
import dev.lazier.pub.shared.ContinueResponse
import dev.lazier.pub.shared.CreateShelfRequest
import dev.lazier.pub.shared.GrabRequest
import dev.lazier.pub.shared.GrabsResponse
import dev.lazier.pub.shared.Indexer
import dev.lazier.pub.shared.LoginRequest
import dev.lazier.pub.shared.LoginResponse
import dev.lazier.pub.shared.ProgressRequest
import dev.lazier.pub.shared.ReadingProgress
import dev.lazier.pub.shared.SearchResponse
import dev.lazier.pub.shared.SeriesResponse
import dev.lazier.pub.shared.Shelf
import dev.lazier.pub.shared.ShelvesResponse
import dev.lazier.pub.shared.StatsResponse
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val status: Int, message: String) : Exception(message)

/**
 * The single way out of the app.
 *
 * Every call is blocking and belongs on Dispatchers.IO. The server is the only
 * source of truth; there is no local database to fall out of step with.
 */
object Api {

    // --- library ---------------------------------------------------------

    fun books(
        kind: BookKind? = null,
        text: String? = null,
        series: String? = null,
        sort: String = "title",
        order: String = "asc",
        limit: Int = 500,
    ): BooksResponse {
        val query = buildList {
            kind?.let { add("kind=" + it.name.lowercase()) }
            text?.takeIf { it.isNotBlank() }?.let { add("q=" + encode(it)) }
            series?.takeIf { it.isNotBlank() }?.let { add("series=" + encode(it)) }
            add("sort=$sort")
            add("order=$order")
            add("limit=$limit")
        }.joinToString("&")
        return BooksCodec.decodeBooks(request("GET", "/books?$query"))
    }

    fun book(id: String): Book = BooksCodec.decodeBook(request("GET", "/books/${encode(id)}"))

    fun series(): SeriesResponse = BooksCodec.decodeSeries(request("GET", "/series"))

    fun stats(): StatsResponse = BooksCodec.decodeStats(request("GET", "/stats"))

    fun config(): ConfigResponse = BooksCodec.decodeConfig(request("GET", "/config"))

    // --- reading ---------------------------------------------------------

    fun continueReading(): ContinueResponse =
        BooksCodec.decodeContinue(request("GET", "/continue?limit=60"))

    fun progress(): List<ReadingProgress> =
        BooksCodec.decodeProgressList(request("GET", "/progress")).entries

    fun putProgress(bookId: String, request: ProgressRequest): ReadingProgress =
        BooksCodec.decodeProgress(
            this.request("PUT", "/progress/${encode(bookId)}", BooksCodec.encodeProgress(request)),
        )

    fun deleteProgress(bookId: String) {
        request("DELETE", "/progress/${encode(bookId)}")
    }

    // --- shelves ---------------------------------------------------------

    fun shelves(): List<Shelf> = BooksCodec.decodeShelves(request("GET", "/shelves")).shelves

    fun createShelf(name: String): Shelf = BooksCodec.decodeShelf(
        request("POST", "/shelves", BooksCodec.encodeShelf(CreateShelfRequest(name))),
    )

    fun deleteShelf(id: String) {
        request("DELETE", "/shelves/${encode(id)}")
    }

    fun addToShelf(shelfId: String, bookId: String): Shelf =
        BooksCodec.decodeShelf(request("PUT", "/shelves/${encode(shelfId)}/books/${encode(bookId)}"))

    fun removeFromShelf(shelfId: String, bookId: String): Shelf =
        BooksCodec.decodeShelf(request("DELETE", "/shelves/${encode(shelfId)}/books/${encode(bookId)}"))

    // --- acquisition -----------------------------------------------------

    /** Browse sources. Prowlarr searches all of them when the filter is empty. */
    fun indexers(): List<Indexer> =
        BooksCodec.decodeIndexers(request("GET", "/indexers")).indexers

    fun search(query: String, indexerIds: List<Int> = emptyList()): SearchResponse {
        val filter = indexerIds.takeIf { it.isNotEmpty() }
            ?.joinToString("&") { "indexerIds=$it" }
            ?.let { "&$it" }
            .orEmpty()
        return BooksCodec.decodeSearch(request("GET", "/search?q=${encode(query)}&limit=60$filter"))
    }

    fun grabs(): GrabsResponse = BooksCodec.decodeGrabs(request("GET", "/grabs"))

    fun grab(downloadUrl: String, title: String?, category: String?): GrabsResponse =
        BooksCodec.decodeGrabs(
            request("POST", "/grabs", BooksCodec.encodeGrab(GrabRequest(downloadUrl, title, category))),
        )

    fun cancelGrab(hash: String) {
        request("DELETE", "/grabs/${encode(hash)}")
    }

    fun login(password: String): LoginResponse =
        BooksCodec.decodeLogin(request("POST", "/auth/login", BooksCodec.encodeLogin(LoginRequest(password))))

    // --- images and files ------------------------------------------------

    fun cover(bookId: String, width: Int?): ByteArray {
        val suffix = width?.let { "?w=$it" } ?: ""
        return requestBytes("/books/${encode(bookId)}/cover$suffix")
    }

    /**
     * Downloads a book to `target`. The final rename means a half-written file
     * never looks like a complete one.
     */
    fun download(bookId: String, target: File, onProgress: (Long, Long) -> Unit) {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        partial.delete()

        val connection = open("/books/${encode(bookId)}/file")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw failure(connection, code)
            val total = connection.contentLengthLong
            var copied = 0L

            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                }
            }

            if (total > 0 && copied != total) {
                throw ApiException(0, "Download stopped at $copied of $total bytes")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
        } finally {
            partial.delete()
            connection.disconnect()
        }
    }

    // --- transport -------------------------------------------------------

    private fun request(method: String, path: String, body: String? = null): String =
        requestBytes(path, method, body).toString(Charsets.UTF_8)

    private fun requestBytes(path: String, method: String = "GET", body: String? = null): ByteArray {
        val connection = open(path, method, body)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw failure(connection, code)

            val stream = connection.inputStream
            return stream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String = "GET", body: String? = null): HttpURLConnection {
        if (Settings.serverUrl.isBlank()) {
            throw ApiException(0, "Set the server address in More → Server.")
        }
        val connection = URL(Settings.serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        if (Settings.token.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer ${Settings.token}")
        }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        return connection
    }

    private fun failure(connection: HttpURLConnection, code: Int): ApiException {
        val text = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")

        val message = runCatching { BooksCodec.decodeError(text).detail }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: when (code) {
                401 -> "Not authorised. Check the token in More → Server."
                404 -> "Not found on the server."
                else -> "Server returned HTTP $code."
            }
        return ApiException(code, message)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
