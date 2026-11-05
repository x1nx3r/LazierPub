package dev.lazier.pub.server.adapter.inbound.http

import dev.lazier.pub.server.ConflictException
import dev.lazier.pub.server.NotConfiguredException
import dev.lazier.pub.server.NotFoundException
import dev.lazier.pub.server.UnauthorizedException
import dev.lazier.pub.server.UpstreamException
import dev.lazier.pub.server.VERSION
import dev.lazier.pub.server.port.CoverProvider
import dev.lazier.pub.server.usecase.AddShelf
import dev.lazier.pub.server.usecase.BookQuery
import dev.lazier.pub.server.usecase.BookSort
import dev.lazier.pub.server.usecase.ContinueReading
import dev.lazier.pub.server.usecase.CreateShelf
import dev.lazier.pub.server.usecase.DeleteProgress
import dev.lazier.pub.server.usecase.DeleteShelf
import dev.lazier.pub.server.usecase.FindBook
import dev.lazier.pub.server.usecase.GetProgress
import dev.lazier.pub.server.usecase.LibraryStats
import dev.lazier.pub.server.usecase.ListGrabs
import dev.lazier.pub.server.usecase.ListIndexers
import dev.lazier.pub.server.usecase.ListProgress
import dev.lazier.pub.server.usecase.ListSeries
import dev.lazier.pub.server.usecase.ListShelves
import dev.lazier.pub.server.usecase.Login
import dev.lazier.pub.server.usecase.QueryBooks
import dev.lazier.pub.server.usecase.RemoveBookFromShelf
import dev.lazier.pub.server.usecase.SaveProgress
import dev.lazier.pub.server.usecase.SearchReleases
import dev.lazier.pub.server.usecase.StartGrab
import dev.lazier.pub.server.usecase.StopGrab
import dev.lazier.pub.shared.ApiJson
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.shared.ConfigResponse
import dev.lazier.pub.shared.CreateShelfRequest
import dev.lazier.pub.shared.ErrorResponse
import dev.lazier.pub.shared.GrabRequest
import dev.lazier.pub.shared.HealthResponse
import dev.lazier.pub.shared.LoginRequest
import dev.lazier.pub.shared.ProgressRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

/**
 * Everything the inbound adapter needs, built by the composition root.
 *
 * A null use case means the upstream is not configured, and the matching route
 * answers 503 with the variable names to set.
 */
class HttpApi(
    val queryBooks: QueryBooks,
    val findBook: FindBook,
    val coverProvider: CoverProvider,
    val listSeries: ListSeries,
    val stats: LibraryStats,
    val continueReading: ContinueReading,
    val listProgress: ListProgress,
    val getProgress: GetProgress,
    val saveProgress: SaveProgress,
    val deleteProgress: DeleteProgress,
    val listShelves: ListShelves,
    val createShelf: CreateShelf,
    val deleteShelf: DeleteShelf,
    val addShelf: AddShelf,
    val removeBookFromShelf: RemoveBookFromShelf,
    val login: Login?,
    val searchReleases: SearchReleases?,
    val listIndexers: ListIndexers?,
    val listGrabs: ListGrabs?,
    val startGrab: StartGrab?,
    val stopGrab: StopGrab?,
    val booksDir: String,
    val category: String,
    val token: String?,
    val bookCount: () -> Int,
)

fun Application.installHttpApi(api: HttpApi) {

    install(ContentNegotiation) { json(ApiJson) }
    // PartialContent gives /books/{id}/file Range support, which PDF readers need.
    install(PartialContent)
    install(CallLogging)

    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", cause.message))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("conflict", cause.message))
        }
        exception<NotConfiguredException> { call, cause ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("not_configured", cause.message))
        }
        exception<UpstreamException> { call, cause ->
            call.application.log.warn("upstream failure: {}", cause.message)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("upstream_error", cause.message))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("unhandled failure", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", cause.message))
        }
    }

    routing {
        // --- public ------------------------------------------------------

        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    version = VERSION,
                    booksDir = api.booksDir,
                    bookCount = api.bookCount(),
                ),
            )
        }

        /** Non-secret summary so a client can configure itself. */
        get("/config") {
            call.respond(
                ConfigResponse(
                    version = VERSION,
                    booksDir = api.booksDir,
                    bookCount = api.bookCount(),
                    tokenRequired = !api.token.isNullOrBlank(),
                    prowlarrConfigured = api.searchReleases != null,
                    qbittorrentConfigured = api.listGrabs != null,
                    category = api.category,
                ),
            )
        }

        post("/auth/login") {
            val login = api.login
            if (login == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("not_configured", "Set LAZIER_TOKEN before clients can log in."),
                )
                return@post
            }
            call.respond(login(call.receive<LoginRequest>()))
        }

        // --- library -----------------------------------------------------

        get("/books") {
            if (call.notAuthorized(api.token)) return@get
            call.respond(api.queryBooks(call.bookQuery()))
        }

        get("/books/{id}") {
            if (call.notAuthorized(api.token)) return@get
            val stored = api.findBook(call.parameters["id"].orEmpty())
                ?: throw NotFoundException("No such book")
            call.respond(stored.book)
        }

        get("/books/{id}/file") {
            if (call.notAuthorized(api.token)) return@get
            val stored = api.findBook(call.parameters["id"].orEmpty())
                ?: throw NotFoundException("No such book")

            val name = stored.path.fileName.toString()
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, name)
                    .toString(),
            )
            call.respondFile(stored.path.toFile())
        }

        /** Cover art, extracted from the EPUB and cached on disk. */
        get("/books/{id}/cover") {
            if (call.notAuthorized(api.token)) return@get
            val cover = api.coverProvider.cover(
                call.parameters["id"].orEmpty(),
                call.coverWidth(),
            ) ?: throw NotFoundException("No cover for this book")

            val etag = cover.etag
            if (etag != null && call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }

            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            if (etag != null) call.response.header(HttpHeaders.ETag, etag)
            call.respondBytes(cover.bytes, ContentType.parse(cover.contentType))
        }

        get("/series") {
            if (call.notAuthorized(api.token)) return@get
            call.respond(api.listSeries(call.request.queryParameters["q"]))
        }

        get("/stats") {
            if (call.notAuthorized(api.token)) return@get
            call.respond(api.stats())
        }

        // --- reading progress --------------------------------------------

        get("/progress") {
            if (call.notAuthorized(api.token)) return@get
            call.respond(api.listProgress())
        }

        get("/continue") {
            if (call.notAuthorized(api.token)) return@get
            val includeFinished = call.request.queryParameters["includeFinished"]
                ?.equals("true", ignoreCase = true)
                ?: false
            call.respond(api.continueReading(call.intParameter("limit", 20), includeFinished))
        }

        get("/progress/{bookId}") {
            if (call.notAuthorized(api.token)) return@get
            call.respond(api.getProgress(call.parameters["bookId"].orEmpty()))
        }

        put("/progress/{bookId}") {
            if (call.notAuthorized(api.token)) return@put
            val request = call.receive<ProgressRequest>()
            call.respond(api.saveProgress(call.parameters["bookId"].orEmpty(), request))
        }

        delete("/progress/{bookId}") {
            if (call.notAuthorized(api.token)) return@delete
            api.deleteProgress(call.parameters["bookId"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }

        // --- shelves -----------------------------------------------------

        get("/shelves") {
            if (call.notAuthorized(api.token)) return@get
            call.respond(api.listShelves())
        }

        post("/shelves") {
            if (call.notAuthorized(api.token)) return@post
            val request = call.receive<CreateShelfRequest>()
            call.respond(HttpStatusCode.Created, api.createShelf(request.name))
        }

        delete("/shelves/{id}") {
            if (call.notAuthorized(api.token)) return@delete
            api.deleteShelf(call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }

        put("/shelves/{id}/books/{bookId}") {
            if (call.notAuthorized(api.token)) return@put
            call.respond(
                api.addShelf(
                    call.parameters["id"].orEmpty(),
                    call.parameters["bookId"].orEmpty(),
                ),
            )
        }

        delete("/shelves/{id}/books/{bookId}") {
            if (call.notAuthorized(api.token)) return@delete
            call.respond(
                api.removeBookFromShelf(
                    call.parameters["id"].orEmpty(),
                    call.parameters["bookId"].orEmpty(),
                ),
            )
        }

        // --- acquisition -------------------------------------------------

        get("/search") {
            if (call.notAuthorized(api.token)) return@get
            val search = api.searchReleases ?: throw NotConfiguredException(
                "Set PROWLARR_URL and PROWLARR_API_KEY.",
            )
            call.respond(
                search(
                    query = call.request.queryParameters["q"].orEmpty(),
                    limit = call.intParameter("limit", 60),
                    indexerIds = call.indexerIds(),
                ),
            )
        }

        /** The sources the browse picker offers. Same upstream as /search. */
        get("/indexers") {
            if (call.notAuthorized(api.token)) return@get
            val indexers = api.listIndexers ?: throw NotConfiguredException(
                "Set PROWLARR_URL and PROWLARR_API_KEY.",
            )
            call.respond(indexers())
        }

        get("/grabs") {
            if (call.notAuthorized(api.token)) return@get
            val grabs = api.listGrabs ?: throw NotConfiguredException(
                "Set QBITTORRENT_URL, QBITTORRENT_USERNAME and QBITTORRENT_PASSWORD.",
            )
            call.respond(grabs())
        }

        post("/grabs") {
            if (call.notAuthorized(api.token)) return@post
            val start = api.startGrab ?: throw NotConfiguredException("qBittorrent is not configured.")
            val request = call.receive<GrabRequest>()
            call.respond(HttpStatusCode.Accepted, start(request.downloadUrl, request.category))
        }

        delete("/grabs/{hash}") {
            if (call.notAuthorized(api.token)) return@delete
            val stop = api.stopGrab ?: throw NotConfiguredException("qBittorrent is not configured.")
            stop(call.parameters["hash"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}


/** True when the request was rejected, so routes can `return@get` early. */
private suspend fun ApplicationCall.notAuthorized(expected: String?): Boolean {
    if (expected.isNullOrBlank()) return false

    val supplied = request.header(HttpHeaders.Authorization)
        ?.removePrefix("Bearer ")
        ?.trim()

    if (supplied == expected) return false

    respond(
        HttpStatusCode.Unauthorized,
        ErrorResponse("unauthorized", "Send Authorization: Bearer <token>"),
    )
    return true
}

/** Parses `/books` query parameters. Bad values are 400, not silent defaults. */
private fun ApplicationCall.bookQuery(): BookQuery {
    val parameters = request.queryParameters

    val kinds = parameters["kind"]
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map { raw ->
            BookKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown kind: $raw")
        }
        ?.toSet()
        ?: emptySet()

    val sort = parameters["sort"]?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        BookSort.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown sort: $raw")
    } ?: BookSort.TITLE

    val order = parameters["order"]?.trim()?.takeIf { it.isNotEmpty() }
    require(order == null || order.equals("asc", true) || order.equals("desc", true)) {
        "order must be asc or desc"
    }

    return BookQuery(
        text = parameters["q"],
        kinds = kinds,
        series = parameters["series"],
        author = parameters["author"],
        shelf = parameters["shelf"],
        sort = sort,
        descending = order.equals("desc", ignoreCase = true),
        limit = intParameter("limit", 100),
        offset = intParameter("offset", 0),
    )
}

private fun ApplicationCall.intParameter(name: String, default: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    return raw.toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer")
}

/**
 * Source filter for `/search`. Accepts repeated keys (`indexerIds=1&indexerIds=2`)
 * and a comma list, so both a phone and `curl` are easy. Unknown values are 400.
 */
private fun ApplicationCall.indexerIds(): List<Int> =
    request.queryParameters.getAll("indexerIds")
        .orEmpty()
        .flatMap { it.split(',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { raw ->
            raw.toIntOrNull() ?: throw IllegalArgumentException("indexerIds must be integers")
        }
        .distinct()

/** `w` asks for a downscaled cover. */
private fun ApplicationCall.coverWidth(): Int? {
    val raw = request.queryParameters["w"] ?: return null
    val width = raw.toIntOrNull()
    require(width != null && width >= MIN_COVER_WIDTH) { "w must be an integer of at least $MIN_COVER_WIDTH" }
    return width.coerceAtMost(MAX_COVER_WIDTH)
}

private const val MIN_COVER_WIDTH = 32
private const val MAX_COVER_WIDTH = 2048
