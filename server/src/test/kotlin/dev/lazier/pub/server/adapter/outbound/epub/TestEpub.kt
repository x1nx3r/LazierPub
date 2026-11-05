package dev.lazier.pub.server.adapter.outbound.epub

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds real EPUB archives for tests. */
object TestEpub {

    private val CONTAINER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    fun write(
        directory: Path,
        name: String,
        opf: String,
        files: Map<String, ByteArray> = emptyMap(),
    ): Path {
        val path = directory.resolve(name)
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            fun put(entry: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray())
            put("META-INF/container.xml", CONTAINER.toByteArray())
            put("OEBPS/content.opf", opf.toByteArray())
            files.forEach { (entry, bytes) -> put(entry, bytes) }
        }
        return path
    }
}
