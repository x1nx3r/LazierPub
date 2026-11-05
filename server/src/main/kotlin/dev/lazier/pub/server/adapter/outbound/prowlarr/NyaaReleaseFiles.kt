package dev.lazier.pub.server.adapter.outbound.prowlarr

import dev.lazier.pub.server.port.ReleaseFiles
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Outbound adapter: the file list on a Nyaa release page.
 *
 * Nyaa prints every file inside a torrent, which turns "Light Novel v01-03"
 * into EPUB or CBZ before the reader spends a download. Other indexers do not
 * share this HTML, so the adapter stays narrow: it refuses every host except
 * Nyaa's and fails soft on any error.
 */
class NyaaReleaseFiles(
    private val http: HttpClient,
) : ReleaseFiles {

    private val cache = ConcurrentHashMap<String, List<String>>()

    override suspend fun formats(infoUrl: String): List<String> {
        if (!isNyaa(infoUrl)) return emptyList()
        cache[infoUrl]?.let { return it }

        val response = http.get(infoUrl) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; LazierPub/0.3)")
        }
        if (!response.status.isSuccess()) return emptyList()

        val html = response.bodyAsText()
        val block = fileList.find(html)?.groupValues?.get(1) ?: return emptyList()
        val formats = formatsIn(tags.replace(block, " "))
        if (formats.isEmpty()) return emptyList()

        if (cache.size >= MAX_CACHE) cache.clear()
        cache[infoUrl] = formats
        return formats
    }

    private fun isNyaa(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        return host == "nyaa.si" || host == "www.nyaa.si"
    }

    private companion object {
        const val MAX_CACHE = 500

        /** The panel that lists every file in the torrent. */
        val fileList = Regex("""(?s)<div class="torrent-file-list[^"]*">(.*?)</div>""")

        /** File names sit next to icons and a size span; only the text matters. */
        val tags = Regex("<[^>]+>")
    }
}
