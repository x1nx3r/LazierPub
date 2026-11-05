package dev.lazier.pub.server.adapter.outbound.epub

import dev.lazier.pub.server.port.EmbeddedMetadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Outbound adapter: read one EPUB's OPF file and keep the result.
 *
 * Both the catalog (metadata) and the cover provider need the OPF. Parsing it
 * once and caching by path, size and modification time removes the second pass.
 */
data class OpfDocument(
    val metadata: EmbeddedMetadata,
    val coverEntry: String?,
    val coverMediaType: String?,
)

class OpfCache {

    private data class Key(val modified: Long, val size: Long)
    private class Entry(val key: Key, val document: OpfDocument?)

    private val entries = ConcurrentHashMap<Path, Entry>()

    /** Returns the parsed document, or null when the file is not a readable EPUB. */
    fun read(path: Path): OpfDocument? {
        val key = runCatching {
            Key(Files.getLastModifiedTime(path).toMillis(), Files.size(path))
        }.getOrNull() ?: return null

        entries[path]?.let { if (it.key == key) return it.document }

        val document = runCatching { parse(path) }.getOrNull()
        entries[path] = Entry(key, document)
        return document
    }

    private fun parse(path: Path): OpfDocument? = ZipFile(path.toFile()).use { zip ->
        val opfPath = containerPath(zip) ?: return@use null
        val entry = zip.getEntry(opfPath) ?: return@use null
        val document = zip.getInputStream(entry).use { parseXml(it) } ?: return@use null
        val cover = findCover(zip, opfPath, document)

        OpfDocument(
            metadata = document.metadata(),
            coverEntry = cover?.first,
            coverMediaType = cover?.second,
        )
    }

    // --- container and OPF -------------------------------------------------

    private fun containerPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        val document = zip.getInputStream(entry).use { parseXml(it) } ?: return null
        return document.elements("rootfile")
            .firstOrNull()
            ?.getAttribute("full-path")
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseXml(stream: InputStream): Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newDocumentBuilder().parse(stream)
    }.getOrNull()

    // --- metadata ----------------------------------------------------------

    private fun Document.metadata(): EmbeddedMetadata {
        val title = text(DC, "title")
        val creators = elements("creator", DC)
        val author = pickAuthors(this, creators)
        val calibreSeries = metaContent("calibre:series") ?: elementText(CALIBRE, "series")
        val calibreIndex = metaContent("calibre:series_index")
        val collection = collection(this)

        return EmbeddedMetadata(
            title = title,
            author = author,
            series = calibreSeries ?: collection?.name,
            volume = normalizeVolume(calibreIndex ?: collection?.position),
            language = language(),
            publisher = text(DC, "publisher"),
            published = plausibleDate(text(DC, "date")),
            description = description(),
        )
    }

    /** Prefers creators marked `aut`; falls back to every creator. */
    private fun pickAuthors(document: Document, creators: List<Element>): String? {
        if (creators.isEmpty()) return null

        val autIds = document.elements("meta", OPF)
            .filter { element ->
                element.getAttribute("property") == "role" &&
                    element.textContent.trim().equals("aut", ignoreCase = true)
            }
            .map { it.getAttribute("refines").removePrefix("#") }
            .filter { it.isNotEmpty() }
            .toSet()

        val chosen = creators.filter { it.getAttribute("id") in autIds }.ifEmpty { creators }
        return chosen
            .map { it.textContent.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    }

    private data class Collection(val name: String, val position: String?)

    /**
     * EPUB 3 series: a `belongs-to-collection` entry, optionally refined with
     * `collection-type=series` and `group-position`.
     */
    private fun collection(document: Document): Collection? {
        val metas = document.elements("meta", OPF)
        val collections = metas.filter { it.getAttribute("property") == "belongs-to-collection" }
        if (collections.isEmpty()) return null

        fun typeOf(element: Element): String? {
            element.getAttribute("collection-type").takeIf { it.isNotBlank() }?.let { return it }
            val id = element.getAttribute("id").takeIf { it.isNotBlank() } ?: return null
            return metas.firstOrNull {
                it.getAttribute("refines") == "#$id" && it.getAttribute("property") == "collection-type"
            }?.textContent?.trim()
        }

        fun positionOf(element: Element): String? {
            val id = element.getAttribute("id").takeIf { it.isNotBlank() } ?: return null
            return metas.firstOrNull {
                it.getAttribute("refines") == "#$id" && it.getAttribute("property") == "group-position"
            }?.textContent?.trim()
        }

        val chosen = collections.firstOrNull { typeOf(it).equals("series", ignoreCase = true) }
            ?: collections.first()

        val name = chosen.textContent.trim().takeIf { it.isNotEmpty() } ?: return null
        return Collection(name, positionOf(chosen))
    }

    private fun Document.text(namespace: String, local: String): String? =
        elements(local, namespace).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun Document.metaContent(name: String): String? =
        elements("meta", OPF)
            .firstOrNull { it.getAttribute("name").equals(name, ignoreCase = true) }
            ?.getAttribute("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun Document.elementText(namespace: String, local: String): String? =
        elements(local, namespace).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** Some files carry a placeholder date such as 0101-01-01. */
    private fun plausibleDate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val year = value.take(4).toIntOrNull() ?: return value
        return value.takeIf { year in 1000..2100 }
    }

    private fun Document.language(): String? =
        text(DC, "language")?.substringBefore('-')?.lowercase()

    private fun Document.description(): String? =
        text(DC, "description")
            ?.replace(TAG, " ")
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.take(MAX_DESCRIPTION)
            ?.takeIf { it.isNotEmpty() }

    /** "01" becomes "1", "1.50" becomes "1.5", "SS1" stays. */
    private fun normalizeVolume(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toBigDecimalOrNull()?.let { number ->
            return number.stripTrailingZeros().toPlainString()
        }
        return value
    }

    // --- cover -------------------------------------------------------------

    /**
     * Resolution order follows the specification, then common practice:
     *   1. manifest item with properties="cover-image"   (EPUB 3)
     *   2. metadata meta[@name="cover"] -> manifest item  (EPUB 2)
     *   3. first image item whose href looks like a cover
     *   4. first image item
     */
    private fun findCover(zip: ZipFile, opfPath: String, document: Document): Pair<String, String?>? {
        val items = document.elements("item", OPF)
        if (items.isEmpty()) return null

        fun candidate(element: Element): Pair<String, String?>? {
            val href = element.getAttribute("href").takeIf { it.isNotBlank() } ?: return null
            val entry = resolveEntryPath(opfPath, href)
            if (zip.getEntry(entry) == null) return null
            val mediaType = element.getAttribute("media-type").takeIf { it.isNotBlank() }
            return entry to mediaType
        }

        items.firstOrNull { it.getAttribute("properties").split(' ').contains("cover-image") }
            ?.let { candidate(it)?.let { cover -> return cover } }

        document.metaContent("cover")
            ?.let { id -> items.firstOrNull { it.getAttribute("id") == id } }
            ?.let { candidate(it)?.let { cover -> return cover } }

        val images = items.filter { it.getAttribute("media-type").startsWith("image/") }
        val chosen = images.firstOrNull { it.getAttribute("href").contains("cover", ignoreCase = true) }
            ?: images.firstOrNull()
        return chosen?.let { candidate(it) }
    }

    /** Resolves an OPF-relative href to a ZIP entry path. */
    private fun resolveEntryPath(opfPath: String, href: String): String {
        val cleanHref = href.substringBefore('#').substringBefore('?')
        val decoded = runCatching {
            URLDecoder.decode(cleanHref.replace("+", "%2B"), StandardCharsets.UTF_8)
        }.getOrDefault(cleanHref)

        val directory = opfPath.substringBeforeLast('/', "")
        val joined = if (directory.isEmpty()) decoded else "$directory/$decoded"

        val parts = ArrayDeque<String>()
        for (part in joined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    /** All elements with this local name, optionally restricted to a namespace. */
    private fun Document.elements(localName: String, namespace: String? = null): List<Element> {
        val nodes = getElementsByTagName("*")
        val found = ArrayList<Element>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val name = element.localName ?: element.tagName.substringAfter(':')
            if (!name.equals(localName, ignoreCase = true)) continue
            if (namespace != null && element.namespaceURI != null && element.namespaceURI != namespace) continue
            found += element
        }
        return found
    }

    private companion object {
        const val DC = "http://purl.org/dc/elements/1.1/"
        const val OPF = "http://www.idpf.org/2007/opf"
        const val CALIBRE = "http://calibre.kovidgoyal.net/2009/metadata"
        const val MAX_DESCRIPTION = 1200
        val TAG = Regex("<[^>]+>")
        val WHITESPACE = Regex("\\s+")
    }
}
