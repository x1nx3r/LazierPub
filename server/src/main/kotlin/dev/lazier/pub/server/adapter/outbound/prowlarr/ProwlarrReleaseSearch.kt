package dev.lazier.pub.server.adapter.outbound.prowlarr

import dev.lazier.pub.server.ProwlarrConfig
import dev.lazier.pub.server.UpstreamException
import dev.lazier.pub.server.port.ReleaseSearch
import dev.lazier.pub.shared.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Outbound adapter: indexer search through Prowlarr. */
class ProwlarrReleaseSearch(
    private val config: ProwlarrConfig,
    private val http: HttpClient,
) : ReleaseSearch {

    override suspend fun search(
        query: String,
        limit: Int,
        indexerIds: List<Int>,
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val response = http.get("${config.baseUrl.trimEnd('/')}/api/v1/search") {
            header("X-Api-Key", config.apiKey)
            parameter("query", query)
            parameter("type", "search")
            // One value only. "7000,7020" is rejected with HTTP 400.
            parameter("categories", config.bookCategories)
            parameter("limit", limit)
            // Prowlarr binds an array from repeated keys, not from "1,2".
            indexerIds.forEach { parameter("indexerIds", it) }
        }

        if (!response.status.isSuccess()) {
            throw UpstreamException("Prowlarr", response.status.value)
        }

        val releases: List<ProwlarrRelease> = response.body()
        return releases
            .map { it.toSearchResult() }
            .filter { it.downloadUrl.isNotBlank() }
            // Seeders first: a size-sorted list picks dead torrents.
            .sortedWith(
                compareByDescending<SearchResult> { it.seeders ?: 0 }
                    .thenBy { it.sizeBytes ?: Long.MAX_VALUE },
            )
            // Some indexers ignore Prowlarr's limit; enforce it here.
            .take(limit.coerceAtLeast(1))
    }
}

@Serializable
private data class ProwlarrRelease(
    val title: String = "",
    val guid: String? = null,
    val downloadUrl: String? = null,
    val magnetUrl: String? = null,
    val fileName: String? = null,
    val protocol: String? = null,
    val size: Long? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val grabs: Int? = null,
    val indexerId: Int? = null,
    val indexer: String? = null,
    val infoUrl: String? = null,
    val publishDate: String? = null,
    val indexerFlags: List<String> = emptyList(),
    val categories: List<ProwlarrCategory> = emptyList(),
)

@Serializable
private data class ProwlarrCategory(
    val id: Int = 0,
    val name: String? = null,
)

private fun ProwlarrRelease.toSearchResult(): SearchResult = SearchResult(
    title = title,
    downloadUrl = downloadLink(),
    indexer = indexer,
    indexerId = indexerId,
    protocol = protocol,
    sizeBytes = size,
    seeders = seeders,
    leechers = leechers,
    grabs = grabs,
    infoUrl = infoUrl,
    publishDate = publishDate,
    categories = categories.mapNotNull { it.name?.takeIf(String::isNotBlank) }.distinct(),
    formats = formatsIn(title, fileName),
    freeleech = indexerFlags.any { it.equals("freeleech", ignoreCase = true) },
)

/**
 * A real magnet first. Prowlarr's `magnetUrl` is sometimes its own download
 * endpoint with the API key in the query, which must not reach the phone.
 * `downloadUrl` is the fallback for clients that need a proxy link.
 */
private fun ProwlarrRelease.downloadLink(): String {
    val magnet = sequenceOf(guid, magnetUrl)
        .filterNotNull()
        .firstOrNull { it.startsWith("magnet:", ignoreCase = true) }
    return magnet ?: downloadUrl ?: magnetUrl ?: guid.orEmpty()
}

