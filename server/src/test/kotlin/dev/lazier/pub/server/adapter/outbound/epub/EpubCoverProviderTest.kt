package dev.lazier.pub.server.adapter.outbound.epub

import dev.lazier.pub.server.adapter.outbound.filesystem.FilesystemBookCatalog
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpubCoverProviderTest {

    private val coverBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private val container = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun writeEpub(
        dir: Path,
        name: String,
        opf: String,
        files: Map<String, ByteArray>,
    ): Path {
        val path = dir.resolve(name)
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            fun put(entry: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray())
            put("META-INF/container.xml", container.toByteArray())
            put("OEBPS/content.opf", opf.toByteArray())
            files.forEach { (entry, bytes) -> put(entry, bytes) }
        }
        return path
    }

    private fun providerFor(library: Path, cache: Path) =
        EpubCoverProvider(FilesystemBookCatalog(library.toString()), OpfCache(), cache)

    @Test
    fun readsTheEpub3CoverImageProperty() = runBlocking {
        val library = Files.createTempDirectory("cover-lib")
        writeEpub(
            library,
            "epub3.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">x</dc:identifier><dc:title>Test</dc:title>
              </metadata>
              <manifest>
                <item id="cover" href="images/art.png" media-type="image/png" properties="cover-image"/>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
            """.trimIndent(),
            mapOf("OEBPS/images/art.png" to coverBytes),
        )

        val provider = providerFor(library, Files.createTempDirectory("cover-cache"))
        val id = FilesystemBookCatalog(library.toString()).all().single().id
        val cover = provider.cover(id)

        assertContentEquals(coverBytes, cover?.bytes)
        assertEquals("image/png", cover?.contentType)
    }

    @Test
    fun readsTheEpub2MetaCover() = runBlocking {
        val library = Files.createTempDirectory("cover-lib")
        writeEpub(
            library,
            "epub2.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">x</dc:identifier><dc:title>Test</dc:title>
                <meta name="cover" content="thumb"/>
              </metadata>
              <manifest>
                <item id="thumb" href="thumb.jpg" media-type="image/jpeg"/>
              </manifest>
              <spine/>
            </package>
            """.trimIndent(),
            mapOf("OEBPS/thumb.jpg" to coverBytes),
        )

        val provider = providerFor(library, Files.createTempDirectory("cover-cache"))
        val id = FilesystemBookCatalog(library.toString()).all().single().id
        val cover = provider.cover(id)

        assertContentEquals(coverBytes, cover?.bytes)
        assertEquals("image/jpeg", cover?.contentType)
    }

    @Test
    fun returnsNullWhenTheEpubHasNoImage() = runBlocking {
        val library = Files.createTempDirectory("cover-lib")
        writeEpub(
            library,
            "plain.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">x</dc:identifier><dc:title>Test</dc:title>
              </metadata>
              <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
            """.trimIndent(),
            emptyMap(),
        )

        val provider = providerFor(library, Files.createTempDirectory("cover-cache"))
        val id = FilesystemBookCatalog(library.toString()).all().single().id

        assertNull(provider.cover(id))
    }

    @Test
    fun returnsNullForNonEpubFiles() = runBlocking {
        val library = Files.createTempDirectory("cover-lib")
        Files.write(library.resolve("book.pdf"), byteArrayOf(0x25, 0x50, 0x44, 0x46))

        val provider = providerFor(library, Files.createTempDirectory("cover-cache"))
        val id = FilesystemBookCatalog(library.toString()).all().single().id

        assertNull(provider.cover(id))
    }

    @Test
    fun cachesTheExtractedCover() = runBlocking {
        val library = Files.createTempDirectory("cover-lib")
        val cache = Files.createTempDirectory("cover-cache")
        writeEpub(
            library,
            "cached.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">x</dc:identifier><dc:title>Test</dc:title>
              </metadata>
              <manifest>
                <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>
              </manifest>
              <spine/>
            </package>
            """.trimIndent(),
            mapOf("OEBPS/cover.png" to coverBytes),
        )

        val provider = providerFor(library, cache)
        val id = FilesystemBookCatalog(library.toString()).all().single().id

        val first = provider.cover(id)
        val second = provider.cover(id)

        assertContentEquals(first?.bytes, second?.bytes)
        assertEquals(first?.etag, second?.etag)

        val cached = Files.list(cache).use { it.toList() }
        assertEquals(1, cached.size)
        assertEquals("image/png", second?.contentType)
    }
}
