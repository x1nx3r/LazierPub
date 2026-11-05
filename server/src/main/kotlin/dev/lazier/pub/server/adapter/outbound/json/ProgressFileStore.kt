package dev.lazier.pub.server.adapter.outbound.json

import dev.lazier.pub.server.port.ProgressStore
import dev.lazier.pub.shared.ReadingProgress
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
private data class ProgressFile(val entries: List<ReadingProgress> = emptyList())

/**
 * Progress survives a restart in one JSON file.
 *
 * The document is small: one entry per book the user has opened. It is read once
 * and kept in memory, so a position lookup does not touch the disk.
 */
class ProgressFileStore(private val file: Path) : ProgressStore {

    private val lock = Any()
    private var loaded = false
    private val entries = LinkedHashMap<String, ReadingProgress>()

    override fun all(): List<ReadingProgress> = synchronized(lock) {
        load()
        entries.values.sortedByDescending { it.updatedAt }
    }

    override fun get(bookId: String): ReadingProgress? = synchronized(lock) {
        load()
        entries[bookId]
    }

    override fun put(progress: ReadingProgress) = synchronized(lock) {
        load()
        entries[progress.bookId] = progress
        save()
    }

    override fun delete(bookId: String): Boolean = synchronized(lock) {
        load()
        val removed = entries.remove(bookId) != null
        if (removed) save()
        removed
    }

    private fun load() {
        if (loaded) return
        loaded = true
        JsonFiles.read(file, ProgressFile.serializer())
            ?.entries
            ?.forEach { entries[it.bookId] = it }
    }

    private fun save() {
        JsonFiles.write(file, ProgressFile.serializer(), ProgressFile(entries.values.toList()))
    }
}
