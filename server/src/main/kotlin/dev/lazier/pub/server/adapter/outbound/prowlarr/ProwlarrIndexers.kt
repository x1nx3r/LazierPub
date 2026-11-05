package dev.lazier.pub.server.adapter.outbound.prowlarr

import dev.lazier.pub.server.ProwlarrConfig
import dev.lazier.pub.server.UpstreamException
import dev.lazier.pub.server.port.IndexerCatalog
import dev.lazier.pub.shared.Indexer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Outbound adapter: the enabled Prowlarr indexers.
 *
 * These become the browse sources. Prowlarr itself already searches all of them
 * at once, so the picker only narrows a search; it never fans out by hand.
 */
class ProwlarrIndexers(
    private val config: ProwlarrConfig,
    private val http: HttpClient,
) : IndexerCatalog {

    override suspend fun list(): List<Indexer> {
        val response = http.get("${config.baseUrl.trimEnd('/')}/api/v1/indexer") {
            header("X-Api-Key", config.apiKey)
        }

        if (!response.status.isSuccess()) {
            throw UpstreamException("Prowlarr", response.status.value)
        }

        return response.body<List<ProwlarrIndexer>>()
            .asSequence()
            .filter { it.enable && it.name.isNotBlank() }
            .map {
                Indexer(
                    id = it.id,
                    name = it.name,
                    protocol = it.protocol,
                    language = it.language,
                    enabled = true,
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
}

@Serializable
private data class ProwlarrIndexer(
    val id: Int = 0,
    val name: String = "",
    val enable: Boolean = false,
    val protocol: String? = null,
    val language: String? = null,
)
