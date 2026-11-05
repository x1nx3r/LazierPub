package dev.lazier.pub.server.adapter.outbound.epub

import dev.lazier.pub.server.port.EmbeddedMetadata
import dev.lazier.pub.server.port.MetadataReader
import java.nio.file.Path

/** Metadata adapter: the OPF fields, with the cover flag from the same parse. */
class EpubMetadataReader(private val opfs: OpfCache) : MetadataReader {

    override fun read(path: Path): EmbeddedMetadata? {
        val document = opfs.read(path) ?: return null
        return document.metadata.copy(hasCover = document.coverEntry != null)
    }
}
