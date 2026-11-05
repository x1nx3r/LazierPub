package dev.lazier.pub.server.adapter.outbound.qbittorrent

import dev.lazier.pub.server.QbittorrentConfig
import dev.lazier.pub.server.UpstreamException
import dev.lazier.pub.server.port.GrabQueue
import dev.lazier.pub.shared.Grab
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Outbound adapter: the qBittorrent WebUI API.
 *
 * The session cookie is kept by the shared HttpClient (HttpCookies), so this
 * class only logs in once.
 */
class QbittorrentGrabQueue(
    private val config: QbittorrentConfig,
    private val http: HttpClient,
) : GrabQueue {

    private val base = config.baseUrl.trimEnd('/')
    private var loggedIn = false

    private suspend fun login(force: Boolean = false) {
        if (loggedIn && !force) return

        val response = http.post("$base/api/v2/auth/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("username", config.username)
                        append("password", config.password)
                    },
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw UpstreamException("qBittorrent login", response.status.value)
        }
        loggedIn = true
    }

    override suspend fun list(): List<Grab> {
        login()
        val response = http.get("$base/api/v2/torrents/info") {
            parameter("category", config.category)
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("qBittorrent", response.status.value)
        }
        val torrents: List<QbTorrent> = response.body()
        return torrents.map { it.toGrab() }
    }

    override suspend fun add(downloadUrl: String, category: String?) {
        login()
        val target = category?.trim()?.takeIf { it.isNotEmpty() } ?: config.category
        val response = http.post("$base/api/v2/torrents/add") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("urls", downloadUrl)
                        // No savepath: the category decides placement.
                        append("category", target)
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("qBittorrent", response.status.value)
        }
    }

    /**
     * Files are always kept. Deleting them would delete the book, because the
     * library directory is the download directory.
     */
    override suspend fun remove(hash: String) {
        login()
        val response = http.post("$base/api/v2/torrents/delete") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("hashes", hash)
                        append("deleteFiles", "false")
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("qBittorrent", response.status.value)
        }
    }
}

@Serializable
private data class QbTorrent(
    val hash: String = "",
    val name: String = "",
    val state: String = "",
    val progress: Double = 0.0,
    val size: Long? = null,
    val dlspeed: Long? = null,
    @SerialName("num_seeds") val numSeeds: Int? = null,
    @SerialName("save_path") val savePath: String? = null,
    val category: String? = null,
    val eta: Long? = null,
)

private fun QbTorrent.toGrab(): Grab = Grab(
    hash = hash,
    name = name,
    state = state,
    progress = progress,
    sizeBytes = size,
    speedBytesPerSecond = dlspeed,
    seeders = numSeeds,
    savePath = savePath,
    category = category,
    // qBittorrent reports 8640000 for "unknown".
    etaSeconds = eta?.takeIf { it in 0..8_639_999 },
)
