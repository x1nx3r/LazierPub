package dev.lazier.pub.server.adapter.outbound.epub

import dev.lazier.pub.server.port.BookCatalog
import dev.lazier.pub.server.port.Cover
import dev.lazier.pub.server.port.CoverProvider
import dev.lazier.pub.shared.BookKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

/**
 * Outbound adapter: take the cover image out of an EPUB and cache it on disk.
 *
 * The OPF is parsed by [OpfCache], which the catalog also uses, so a book is
 * inspected once per change.
 *
 * Cached by book id, source modification time and requested width, so a
 * replaced file or a new width needs no invalidation logic.
 */
class EpubCoverProvider(
    private val catalog: BookCatalog,
    private val opfs: OpfCache,
    private val cacheDir: Path,
) : CoverProvider {

    override suspend fun cover(id: String, width: Int?): Cover? = withContext(Dispatchers.IO) {
        val stored = catalog.find(id) ?: return@withContext null
        if (stored.book.kind != BookKind.EPUB) return@withContext null

        val source = stored.path
        val modified = runCatching { Files.getLastModifiedTime(source).toMillis() }.getOrDefault(0L)
        val etag = "\"$id-$modified${width?.let { "-w$it" } ?: ""}\""

        findCached(id, modified, width)?.let { return@withContext it.copy(etag = etag) }

        val document = opfs.read(source) ?: return@withContext null
        val entryPath = document.coverEntry ?: return@withContext null

        val extracted = runCatching { readEntry(source, entryPath, document.coverMediaType) }.getOrNull()
            ?: return@withContext null

        val scaled = width?.let { runCatching { downscale(extracted, it) }.getOrNull() }
        val result = scaled ?: extracted

        runCatching { writeCache(id, modified, width, result) }
        Cover(bytes = result.bytes, contentType = result.contentType, etag = etag)
    }

    // --- extraction -------------------------------------------------------

    private data class Extracted(val bytes: ByteArray, val contentType: String, val extension: String)

    private fun readEntry(source: Path, entryPath: String, mediaType: String?): Extracted? =
        ZipFile(source.toFile()).use { zip ->
            val entry = zip.getEntry(entryPath) ?: return@use null
            val bytes = zip.getInputStream(entry).use(InputStream::readBytes)
            if (bytes.isEmpty()) return@use null

            val extension = entryPath.substringAfterLast('.', "img").lowercase()
            Extracted(bytes = bytes, contentType = mediaType ?: contentTypeFor(extension), extension = extension)
        }

    /**
     * Draws the image at the requested width. Returns null when the image is
     * already narrow enough, or when ImageIO cannot decode it (SVG, for example).
     * The caller then serves the original bytes.
     */
    private fun downscale(cover: Extracted, width: Int): Extracted? {
        val source = ImageIO.read(ByteArrayInputStream(cover.bytes)) ?: return null
        if (source.width <= width) return null

        val height = (source.height.toDouble() * width / source.width).roundToInt().coerceAtLeast(1)
        val hasAlpha = source.colorModel.hasAlpha()
        val type = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val output = BufferedImage(width, height, type)

        val graphics = output.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }

        val format = if (hasAlpha) "png" else "jpg"
        val bytes = encode(output, format) ?: return null
        return Extracted(
            bytes = bytes,
            contentType = if (hasAlpha) "image/png" else "image/jpeg",
            extension = format,
        )
    }

    private fun encode(image: BufferedImage, format: String): ByteArray? {
        val writers = ImageIO.getImageWritersByFormatName(format)
        if (!writers.hasNext()) return null
        val writer = writers.next()

        return runCatching {
            ByteArrayOutputStream().use { out ->
                ImageIO.createImageOutputStream(out).use { stream ->
                    writer.output = stream
                    val parameters = writer.defaultWriteParam
                    if (parameters.canWriteCompressed()) {
                        parameters.compressionMode = ImageWriteParam.MODE_EXPLICIT
                        parameters.compressionQuality = 0.85f
                    }
                    writer.write(null, IIOImage(image, null, null), parameters)
                }
                out.toByteArray()
            }
        }.also { writer.dispose() }.getOrNull()
    }

    // --- cache ------------------------------------------------------------

    private fun findCached(id: String, modified: Long, width: Int?): Cover? {
        if (!Files.isDirectory(cacheDir)) return null
        val prefix = prefixOf(id, modified, width)
        val hit = runCatching {
            Files.list(cacheDir).use { stream ->
                stream.filter { it.fileName.toString().startsWith(prefix) }.findFirst().orElse(null)
            }
        }.getOrNull() ?: return null

        val extension = hit.fileName.toString().substringAfterLast('.')
        return runCatching { Cover(Files.readAllBytes(hit), contentTypeFor(extension)) }.getOrNull()
    }

    private fun writeCache(id: String, modified: Long, width: Int?, cover: Extracted) {
        Files.createDirectories(cacheDir)
        val keepCurrent = "$id-$modified."

        // Drop entries for older versions of this book, keep every width at this version.
        runCatching {
            Files.list(cacheDir).use { stream ->
                stream.filter { path ->
                    val name = path.fileName.toString()
                    name.startsWith("$id-") && !name.startsWith(keepCurrent) && !name.contains("-$modified-w")
                }.forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }

        val target = cacheDir.resolve("${prefixOf(id, modified, width)}${cover.extension}")
        Files.write(target, cover.bytes)
    }

    private fun prefixOf(id: String, modified: Long, width: Int?): String =
        if (width == null) "$id-$modified." else "$id-$modified-w$width."

    private fun contentTypeFor(extension: String): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
}
