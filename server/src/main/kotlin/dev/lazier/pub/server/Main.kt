package dev.lazier.pub.server

import dev.lazier.pub.server.adapter.inbound.http.HttpApi
import dev.lazier.pub.server.adapter.inbound.http.installHttpApi
import dev.lazier.pub.server.adapter.outbound.epub.EpubCoverProvider
import dev.lazier.pub.server.adapter.outbound.epub.EpubMetadataReader
import dev.lazier.pub.server.adapter.outbound.epub.OpfCache
import dev.lazier.pub.server.adapter.outbound.filesystem.FilesystemBookCatalog
import dev.lazier.pub.server.adapter.outbound.json.ProgressFileStore
import dev.lazier.pub.server.adapter.outbound.json.ShelfFileStore
import dev.lazier.pub.server.adapter.outbound.prowlarr.NyaaReleaseFiles
import dev.lazier.pub.server.adapter.outbound.prowlarr.ProwlarrIndexers
import dev.lazier.pub.server.adapter.outbound.prowlarr.ProwlarrReleaseSearch
import dev.lazier.pub.server.adapter.outbound.qbittorrent.QbittorrentGrabQueue
import dev.lazier.pub.server.usecase.AddShelf
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Paths

const val VERSION = "0.3.0"

/**
 * Composition root.
 *
 * This is the only place that knows about every layer at once: it reads the
 * configuration, chooses adapters, and wires use cases into the HTTP API.
 */
fun main() {
    // A local .env is convenient for development. Real environment variables win.
    val envFile = System.getenv("LAZIER_ENV_FILE")?.let { Paths.get(it) } ?: Paths.get(".env")
    val config = loadConfig(DotEnv.load(envFile) + System.getenv())
    val http = createHttpClient()

    val opfs = OpfCache()
    val catalog = FilesystemBookCatalog(config.booksDir, EpubMetadataReader(opfs))
    val covers = EpubCoverProvider(catalog, opfs, Paths.get(config.coversDir))

    val dataDir = Paths.get(config.dataDir)
    val progress = ProgressFileStore(dataDir.resolve("progress.json"))
    val shelves = ShelfFileStore(dataDir.resolve("shelves.json"))

    val releases = config.prowlarr?.let { ProwlarrReleaseSearch(it, http) }
    val indexers = config.prowlarr?.let { ProwlarrIndexers(it, http) }
    val releaseFiles = config.prowlarr?.let { NyaaReleaseFiles(http) }
    val queue = config.qbittorrent?.let { QbittorrentGrabQueue(it, http) }

    val api = HttpApi(
        queryBooks = QueryBooks(catalog, shelves),
        findBook = FindBook(catalog),
        coverProvider = covers,
        listSeries = ListSeries(catalog),
        stats = LibraryStats(catalog),
        continueReading = ContinueReading(catalog, progress),
        listProgress = ListProgress(progress),
        getProgress = GetProgress(progress),
        saveProgress = SaveProgress(catalog, progress),
        deleteProgress = DeleteProgress(progress),
        listShelves = ListShelves(shelves),
        createShelf = CreateShelf(shelves),
        deleteShelf = DeleteShelf(shelves),
        addShelf = AddShelf(shelves, catalog),
        removeBookFromShelf = RemoveBookFromShelf(shelves),
        login = config.token?.takeIf { it.isNotBlank() }?.let { Login(it, config.password) },
        searchReleases = releases?.let { SearchReleases(it, files = releaseFiles) },
        listIndexers = indexers?.let { ListIndexers(it) },
        listGrabs = queue?.let { ListGrabs(it) },
        startGrab = queue?.let { StartGrab(it) },
        stopGrab = queue?.let { StopGrab(it) },
        booksDir = config.booksDir,
        category = config.qbittorrent?.category.orEmpty(),
        token = config.token,
        bookCount = catalog::count,
    )

    println(
        "LazierPub $VERSION starting on :${config.port} " +
            "booksDir=${config.booksDir} " +
            "dataDir=${config.dataDir} " +
            "prowlarr=${if (releases != null) "on" else "off"} " +
            "qbittorrent=${if (queue != null) "on" else "off"} " +
            "auth=${if (config.token.isNullOrBlank()) "off" else "on"}",
    )

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        installHttpApi(api)
    }.start(wait = true)
}

fun createHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(ClientContentNegotiation) { json(ApiJson) }
    // Keeps the qBittorrent session cookie between calls.
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
    }
}
