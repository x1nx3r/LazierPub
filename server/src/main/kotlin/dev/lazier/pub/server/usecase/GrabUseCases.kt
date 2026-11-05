package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.port.GrabQueue
import dev.lazier.pub.shared.GrabsResponse
import kotlinx.coroutines.delay

/** List the active grabs, which are torrents in the configured category. */
class ListGrabs(private val queue: GrabQueue) {
    suspend operator fun invoke(): GrabsResponse = GrabsResponse(queue.list())
}

/**
 * Start a grab and report the queue afterwards.
 *
 * Validation lives here, not in the route: an empty download link is a caller
 * mistake and becomes HTTP 400 at the edge.
 *
 * qBittorrent does not always show a newly added torrent in its list
 * immediately, so the queue is polled briefly until it grows. Returning an empty
 * list straight after a successful add misled a real client.
 */
class StartGrab(
    private val queue: GrabQueue,
    private val settleMillis: Long = 1_500,
    private val pollMillis: Long = 150,
) {
    suspend operator fun invoke(downloadUrl: String, category: String? = null): GrabsResponse {
        require(downloadUrl.isNotBlank()) { "downloadUrl is required" }

        val before = queue.list().size
        queue.add(downloadUrl.trim(), category?.trim()?.takeIf { it.isNotEmpty() })

        val deadline = System.currentTimeMillis() + settleMillis
        var current = queue.list()
        while (current.size <= before && System.currentTimeMillis() < deadline) {
            delay(pollMillis)
            current = queue.list()
        }
        return GrabsResponse(current)
    }
}

/** Stop seeding. The files stay, because the library is the download folder. */
class StopGrab(private val queue: GrabQueue) {
    suspend operator fun invoke(hash: String) {
        require(hash.isNotBlank()) { "hash is required" }
        queue.remove(hash)
    }
}
