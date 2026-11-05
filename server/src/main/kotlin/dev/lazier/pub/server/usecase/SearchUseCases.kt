package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.port.IndexerCatalog
import dev.lazier.pub.server.port.ReleaseFiles
import dev.lazier.pub.server.port.ReleaseSearch
import dev.lazier.pub.shared.IndexersResponse
import dev.lazier.pub.shared.SearchResponse
import dev.lazier.pub.shared.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Search the indexers.
 *
 * A blank query returns nothing instead of asking the upstream, because Prowlarr
 * answers a blank query with its whole catalogue. An empty indexer list searches
 * every source; otherwise Prowlarr narrows to those ids.
 *
 * The first few results may get their file formats from the indexer's detail
 * page. That fetch is capped, parallel and fail-soft, so a slow page delays the
 * answer by at most a few seconds and never fails the search.
 */
class SearchReleases(
    private val search: ReleaseSearch,
    private val files: ReleaseFiles? = null,
    private val defaultLimit: Int = 60,
    private val enrichLimit: Int = 10,
) {
    suspend operator fun invoke(
        query: String,
        limit: Int = defaultLimit,
        indexerIds: List<Int> = emptyList(),
    ): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return SearchResponse(query = "", results = emptyList())
        }
        val found = search.search(
            query = trimmed,
            limit = limit.coerceIn(1, MAX_LIMIT),
            indexerIds = indexerIds,
        )
        return SearchResponse(query = trimmed, results = enrich(found))
    }

    private suspend fun enrich(results: List<SearchResult>): List<SearchResult> {
        val source = files ?: return results
        if (results.isEmpty()) return results

        val head = results.take(enrichLimit)
        val tail = results.drop(enrichLimit)
        val deadline = System.currentTimeMillis() + ENRICH_BUDGET_MILLIS
        val enriched = ArrayList<SearchResult>(head.size)

        // Sequential batches, bounded overall. A page that finishes before the
        // deadline keeps its formats even when a later batch runs out of time.
        var start = 0
        while (start < head.size) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break

            val end = minOf(start + ENRICH_PARALLEL, head.size)
            val batch = coroutineScope {
                head.subList(start, end).map { result ->
                    async {
                        enrichOne(
                            source = source,
                            result = result,
                            timeoutMillis = minOf(remaining, ENRICH_TIMEOUT_MILLIS),
                        )
                    }
                }.awaitAll()
            }
            enriched += batch
            start = end
        }

        if (enriched.size < head.size) enriched += head.drop(enriched.size)
        return enriched + tail
    }

    private suspend fun enrichOne(
        source: ReleaseFiles,
        result: SearchResult,
        timeoutMillis: Long,
    ): SearchResult {
        if (result.formats.isNotEmpty()) return result
        val url = result.infoUrl ?: return result

        val extra = withTimeoutOrNull(timeoutMillis) {
            runCatching { source.formats(url) }.getOrDefault(emptyList())
        }.orEmpty()
        return if (extra.isEmpty()) result else result.copy(formats = extra)
    }

    private companion object {
        const val MAX_LIMIT = 200
        const val ENRICH_PARALLEL = 4
        const val ENRICH_TIMEOUT_MILLIS = 2_500L
        const val ENRICH_BUDGET_MILLIS = 5_000L
    }
}

/** The sources the browse picker offers. */
class ListIndexers(
    private val catalog: IndexerCatalog,
) {
    suspend operator fun invoke(): IndexersResponse = IndexersResponse(catalog.list())
}
