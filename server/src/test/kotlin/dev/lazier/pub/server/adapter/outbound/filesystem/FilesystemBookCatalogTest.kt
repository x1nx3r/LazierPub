package dev.lazier.pub.server.adapter.outbound.filesystem

import java.nio.file.Files
import kotlin.io.path.writeText
import dev.lazier.pub.server.adapter.outbound.epub.EpubMetadataReader
import dev.lazier.pub.server.adapter.outbound.epub.OpfCache
import dev.lazier.pub.server.adapter.outbound.epub.TestEpub
import dev.lazier.pub.shared.BookKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilesystemBookCatalogTest {

    @Test
    fun parsesSeriesAndVolumeFromThePath() {
        val root = Files.createTempDirectory("lazier-library")
        val seriesDir = root.resolve("Fate Strange Fake")
        Files.createDirectories(seriesDir)
        seriesDir.resolve("Fate Strange Fake - Vol.2.epub").writeText("x")
        root.resolve("loose.pdf").writeText("y")

        val books = FilesystemBookCatalog(root.toString()).all()

        assertEquals(2, books.size)

        val volume = books.single { it.title.contains("Vol.2") }
        assertEquals("Fate Strange Fake", volume.series)
        assertEquals("2", volume.volume)
        assertEquals(BookKind.EPUB, volume.kind)

        val loose = books.single { it.title == "loose" }
        assertNull(loose.series)
    }

    @Test
    fun idsAreStableAcrossScans() {
        val root = Files.createTempDirectory("lazier-library")
        root.resolve("book.epub").writeText("x")

        val catalog = FilesystemBookCatalog(root.toString())
        assertEquals(catalog.all().single().id, catalog.all().single().id)
    }

    @Test
    fun findReturnsTheFileForAStreamingResponse() {
        val root = Files.createTempDirectory("lazier-library")
        root.resolve("book.epub").writeText("x")

        val catalog = FilesystemBookCatalog(root.toString())
        val id = catalog.all().single().id
        val stored = catalog.find(id)

        assertEquals("book.epub", stored?.path?.fileName?.toString())
    }

    @Test
    fun ignoresUnknownExtensions() {
        val root = Files.createTempDirectory("lazier-library")
        root.resolve("cover.jpg").writeText("x")
        root.resolve("notes.txt").writeText("y")

        assertEquals(0, FilesystemBookCatalog(root.toString()).all().size)
    }

    @Test
    fun readsMetadataFromTheFile() {
        val root = Files.createTempDirectory("lazier-library")
        val directory = Files.createDirectories(root.resolve("Sword Art Online [Yen Press]"))
        TestEpub.write(
            directory,
            "sao-v03.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Sword Art Online, Vol. 03: Fairy Dance</dc:title>
                <dc:creator>Reki Kawahara and abec</dc:creator>
                <dc:language>en</dc:language>
                <dc:publisher>Yen On</dc:publisher>
              </metadata>
            </package>
            """.trimIndent(),
        )

        val catalog = FilesystemBookCatalog(root.toString(), EpubMetadataReader(OpfCache()))
        val book = catalog.all().single()

        // Title and author from the file, series and volume from the title.
        assertEquals("Sword Art Online, Vol. 03: Fairy Dance", book.title)
        assertEquals("Reki Kawahara and abec", book.author)
        assertEquals("Sword Art Online", book.series)
        assertEquals("3", book.volume)
        assertEquals("Yen On", book.publisher)
        assertEquals(BookKind.EPUB, book.kind)
    }

    @Test
    fun prefersTheOpfOverTheFilename() {
        val root = Files.createTempDirectory("lazier-library")
        TestEpub.write(
            root,
            "ugly-release-name-v99.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Real Title</dc:title>
                <dc:creator>Real Author</dc:creator>
              </metadata>
            </package>
            """.trimIndent(),
        )

        val book = FilesystemBookCatalog(root.toString(), EpubMetadataReader(OpfCache())).all().single()

        assertEquals("Real Title", book.title)
        assertEquals("Real Author", book.author)
    }

    @Test
    fun fallsBackToTheDeepestMeaningfulDirectory() {
        val root = Files.createTempDirectory("lazier-library")
        val directory = Files.createDirectories(
            root.resolve("A Certain Magical Index - Pack/EPUB/A Certain Magical Index"),
        )
        directory.resolve("A Certain Magical Index - Volume 21.epub").writeText("x")

        val book = FilesystemBookCatalog(root.toString()).all().single()

        // Not the pack name, and not "EPUB".
        assertEquals("A Certain Magical Index", book.series)
        assertEquals("21", book.volume)
    }
}
