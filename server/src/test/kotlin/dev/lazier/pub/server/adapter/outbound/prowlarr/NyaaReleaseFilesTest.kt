package dev.lazier.pub.server.adapter.outbound.prowlarr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NyaaReleaseFilesTest {

    private val html = """
        <div class="panel panel-default">
          <div class="torrent-file-list panel-body">
            <ul>
              <li>
                <a class="folder" href=""><i class="fa fa-folder-open"></i>Volume 1</a>
                <ul>
                  <li><i class="fa fa-file"></i>Volume 1.epub <span class="file-size">(1 MiB)</span></li>
                  <li><i class="fa fa-file"></i>Volume 2.PDF <span class="file-size">(2 MiB)</span></li>
                </ul>
              </li>
            </ul>
          </div>
        </div>
    """.trimIndent()

    private fun adapter(body: String = html): NyaaReleaseFiles {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        return NyaaReleaseFiles(HttpClient(engine))
    }

    @Test
    fun readsFormatsFromTheFileList() = runBlocking {
        assertEquals(listOf("EPUB", "PDF"), adapter().formats("https://nyaa.si/view/1"))
    }

    @Test
    fun ignoresOtherHostsWithoutARequest() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        val adapter = NyaaReleaseFiles(HttpClient(engine))

        assertTrue(adapter.formats("https://example.com/view/1").isEmpty())
        assertTrue(adapter.formats("not a url").isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun answersTheSecondCallFromCache() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        val adapter = NyaaReleaseFiles(HttpClient(engine))

        adapter.formats("https://nyaa.si/view/1")
        adapter.formats("https://nyaa.si/view/1")

        assertEquals(1, calls)
    }

    @Test
    fun returnsNothingWhenThePageHasNoFileList() = runBlocking {
        assertTrue(adapter("<html><body>No files here</body></html>").formats("https://nyaa.si/view/1").isEmpty())
    }
}
