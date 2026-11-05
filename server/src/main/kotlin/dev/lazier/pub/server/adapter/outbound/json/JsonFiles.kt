package dev.lazier.pub.server.adapter.outbound.json

import dev.lazier.pub.shared.ApiJson
import kotlinx.serialization.KSerializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Small JSON documents on disk.
 *
 * Writes go to a temporary file in the same directory, are flushed by the
 * filesystem move, and replace the target atomically. A reader therefore sees
 * either the old document or the new one, never half of each.
 */
internal object JsonFiles {

    fun <T> read(file: Path, serializer: KSerializer<T>): T? {
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            ApiJson.decodeFromString(serializer, Files.readString(file))
        }.getOrNull()
    }

    fun <T> write(file: Path, serializer: KSerializer<T>, value: T) {
        val target = file.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, "${target.fileName}.", ".tmp")
        Files.writeString(temporary, ApiJson.encodeToString(serializer, value))
        runCatching {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
