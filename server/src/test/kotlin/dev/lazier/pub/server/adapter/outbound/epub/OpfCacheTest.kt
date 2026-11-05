package dev.lazier.pub.server.adapter.outbound.epub

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpfCacheTest {

    private val cache = OpfCache()
    private val coverBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Test
    fun readsCalibreSeriesAndVolume() {
        val directory = Files.createTempDirectory("opf-calibre")
        val path = TestEpub.write(
            directory,
            "sao.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"
                        xmlns:calibre="http://calibre.kovidgoyal.net/2009/metadata">
                <dc:title>Sword Art Online, Vol. 03: Fairy Dance</dc:title>
                <dc:creator id="c1">Reki Kawahara and abec</dc:creator>
                <meta property="role" refines="#c1" scheme="marc:relators">aut</meta>
                <meta name="calibre:series" content="Sword Art Online"/>
                <meta name="calibre:series_index" content="03.00"/>
                <dc:language>en-US</dc:language>
                <dc:publisher>Yen On</dc:publisher>
                <dc:date>2017-05-30</dc:date>
                <dc:description>&lt;p&gt;Kirito returns.&lt;/p&gt;</dc:description>
                <meta name="cover" content="cover"/>
              </metadata>
              <manifest>
                <item id="cover" href="cover.png" media-type="image/png"/>
              </manifest>
            </package>
            """.trimIndent(),
            mapOf("OEBPS/cover.png" to coverBytes),
        )

        val document = cache.read(path)

        assertEquals("Sword Art Online, Vol. 03: Fairy Dance", document?.metadata?.title)
        assertEquals("Reki Kawahara and abec", document?.metadata?.author)
        assertEquals("Sword Art Online", document?.metadata?.series)
        assertEquals("3", document?.metadata?.volume)
        assertEquals("en", document?.metadata?.language)
        assertEquals("Yen On", document?.metadata?.publisher)
        assertEquals("2017-05-30", document?.metadata?.published)
        assertEquals("Kirito returns.", document?.metadata?.description)
        assertEquals("OEBPS/cover.png", document?.coverEntry)
        assertEquals("image/png", document?.coverMediaType)
    }

    @Test
    fun readsEpub3BelongsToCollection() {
        val directory = Files.createTempDirectory("opf-epub3")
        val path = TestEpub.write(
            directory,
            "index.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>A Certain Magical Index - Volume 21</dc:title>
                <dc:creator>Kazuma Kamachi</dc:creator>
                <meta property="belongs-to-collection" id="col">A Certain Magical Index</meta>
                <meta refines="#col" property="collection-type">series</meta>
                <meta refines="#col" property="group-position">21</meta>
              </metadata>
              <manifest>
                <item id="art" href="art.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
            </package>
            """.trimIndent(),
            mapOf("OEBPS/art.jpg" to coverBytes),
        )

        val document = cache.read(path)

        assertEquals("A Certain Magical Index", document?.metadata?.series)
        assertEquals("21", document?.metadata?.volume)
        assertEquals("OEBPS/art.jpg", document?.coverEntry)
    }

    @Test
    fun prefersTheCollectionMarkedAsSeries() {
        val directory = Files.createTempDirectory("opf-collections")
        val path = TestEpub.write(
            directory,
            "multi.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test</dc:title>
                <meta property="belongs-to-collection" id="a">A Bundle</meta>
                <meta refines="#a" property="collection-type">set</meta>
                <meta property="belongs-to-collection" id="b">The Real Series</meta>
                <meta refines="#b" property="collection-type">series</meta>
                <meta refines="#b" property="group-position">4</meta>
              </metadata>
            </package>
            """.trimIndent(),
        )

        val document = cache.read(path)

        assertEquals("The Real Series", document?.metadata?.series)
        assertEquals("4", document?.metadata?.volume)
    }

    @Test
    fun leavesSeriesEmptyWhenTheFileDeclaresNone() {
        val directory = Files.createTempDirectory("opf-plain")
        val path = TestEpub.write(
            directory,
            "plain.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Standalone Story</dc:title>
                <dc:creator>Someone</dc:creator>
              </metadata>
            </package>
            """.trimIndent(),
        )

        val document = cache.read(path)

        assertEquals("Standalone Story", document?.metadata?.title)
        assertNull(document?.metadata?.series)
        assertNull(document?.metadata?.volume)
        assertNull(document?.coverEntry)
    }

    @Test
    fun joinsEveryCreatorWhenNoneIsMarkedAuthor() {
        val directory = Files.createTempDirectory("opf-authors")
        val path = TestEpub.write(
            directory,
            "authors.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test</dc:title>
                <dc:creator>Kazuma Kamachi</dc:creator>
                <dc:creator>Narita Ryohgo</dc:creator>
              </metadata>
            </package>
            """.trimIndent(),
        )

        assertEquals("Kazuma Kamachi, Narita Ryohgo", cache.read(path)?.metadata?.author)
    }

    @Test
    fun returnsNullForABrokenArchive() {
        val directory = Files.createTempDirectory("opf-broken")
        val path = directory.resolve("broken.epub")
        Files.write(path, "not a zip".toByteArray())

        assertNull(cache.read(path))
    }

    @Test
    fun reReadsAfterTheFileChanges() {
        val directory = Files.createTempDirectory("opf-cache")
        val first = opfWithTitle("First")
        val path = TestEpub.write(directory, "cached.epub", first)

        assertEquals("First", cache.read(path)?.metadata?.title)

        val rewritten = directory.resolve("rewritten.epub")
        TestEpub.write(directory, "rewritten.epub", opfWithTitle("Second"))
        Files.move(rewritten, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis() + 5_000))

        assertEquals("Second", cache.read(path)?.metadata?.title)
    }

    private fun opfWithTitle(title: String) = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:creator>Someone</dc:creator>
          </metadata>
        </package>
    """.trimIndent()

    @Test
    fun adapterMarksCoverPresence() {
        val directory = Files.createTempDirectory("opf-adapter")
        val path = TestEpub.write(
            directory,
            "cover.epub",
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>With Cover</dc:title>
              </metadata>
              <manifest>
                <item id="c" href="c.png" media-type="image/png" properties="cover-image"/>
              </manifest>
            </package>
            """.trimIndent(),
            mapOf("OEBPS/c.png" to coverBytes),
        )

        assertTrue(EpubMetadataReader(cache).read(path)?.hasCover == true)
        assertFalse(EpubMetadataReader(cache).read(directory.resolve("missing.epub"))?.hasCover == true)
    }
}
