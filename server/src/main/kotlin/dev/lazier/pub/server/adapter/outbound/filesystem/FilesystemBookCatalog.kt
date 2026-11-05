package dev.lazier.pub.server.adapter.outbound.filesystem

import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.MetadataReader
import dev.lazier.pub.server.port.StoredBook
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * Outbound adapter: a directory scan as the book catalog.
 *
 * Metadata comes from the file when the format declares it, from the title when
 * the title names a volume, and from the path last. Each field falls back on its
 * own, so a book with an author but no series still gets a series.
 *
 * Replace this with a database adapter and nothing above it changes.
 */
class FilesystemBookCatalog(
    rootDir: String,
    private val metadata: MetadataReader? = null,
) : BookCatalog {

    private val root: Path = Paths.get(rootDir).toAbsolutePath().normalize()

    override fun all(): List<Book> {
        if (!Files.isDirectory(root)) return emptyList()

        val books = mutableListOf<Book>()
        Files.walk(root).use { stream ->
            stream.forEach { path ->
                if (Files.isRegularFile(path) && isBook(path)) {
                    books += toBook(path)
                }
            }
        }
        return books.sortedBy { it.title.lowercase() }
    }

    override fun count(): Int {
        if (!Files.isDirectory(root)) return 0
        var total = 0
        Files.walk(root).use { stream ->
            stream.forEach { path -> if (Files.isRegularFile(path) && isBook(path)) total++ }
        }
        return total
    }

    override fun find(id: String): StoredBook? {
        if (!Files.isDirectory(root)) return null
        var hit: StoredBook? = null
        Files.walk(root).use { stream ->
            stream.forEach { path ->
                if (hit == null && Files.isRegularFile(path) && isBook(path)) {
                    val book = toBook(path)
                    if (book.id == id) hit = StoredBook(book, path)
                }
            }
        }
        return hit
    }

    private fun isBook(path: Path): Boolean = kindOf(path) != BookKind.UNKNOWN

    private fun kindOf(path: Path): BookKind = BookKind.fromExtension(extensionOf(path))

    private fun extensionOf(path: Path): String =
        path.fileName.toString().substringAfterLast('.', "").lowercase()

    private fun toBook(path: Path): Book {
        val relative = root.relativize(path)
        val segments = relative.map { it.toString() }
        val fileName = path.fileName.toString()
        val stem = fileName.substringBeforeLast('.').trim()
        val kind = kindOf(path)

        val embedded = if (kind == BookKind.EPUB) metadata?.read(path) else null

        val title = embedded?.title?.takeIf { it.isNotBlank() } ?: stem
        val fromTitle = SeriesParser.parse(title)
        val fromFileName = SeriesParser.parse(stem)

        val series = SeriesParser.canonicalSeries(
            embedded?.series
                ?: fromTitle.series
                ?: fromFileName.series
                ?: pathSeries(segments),
        )

        val volume = embedded?.volume
            ?: fromTitle.volume
            ?: fromFileName.volume
            ?: FILE_VOLUME.find(stem)?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" }

        return Book(
            id = idOf(relative.toString()),
            title = title,
            author = embedded?.author,
            kind = kind,
            series = series,
            volume = volume,
            sizeBytes = runCatching { Files.size(path) }.getOrNull(),
            addedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrNull(),
            language = embedded?.language,
            publisher = embedded?.publisher,
            publishedAt = embedded?.published,
            description = embedded?.description,
            hasCover = embedded?.hasCover == true,
        )
    }

    /**
     * The deepest directory that is not a format folder. Archive releases nest
     * books as `Pack/EPUB/Series/Volume.epub`, so the first directory is the
     * pack name while the last one is the series.
     */
    private fun pathSeries(segments: List<String>): String? {
        val directories = segments.dropLast(1).filter { it.isNotBlank() }
        if (directories.isEmpty()) return null

        val meaningful = directories.filterNot { it.uppercase() in FORMAT_DIRECTORIES }
        return (meaningful.lastOrNull() ?: directories.first()).takeIf { it.isNotBlank() }
    }

    private fun idOf(relativePath: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(relativePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private companion object {
        /** Matches "v01", "vol.1", "Volume 3". */
        val FILE_VOLUME = Regex("""(?i)\b(?:v|vol\.?|volume)\s*0*(\d{1,3})\b""")

        val FORMAT_DIRECTORIES = setOf("EPUB", "PDF", "CBZ", "CBR", "MOBI", "AZW3")
    }
}
