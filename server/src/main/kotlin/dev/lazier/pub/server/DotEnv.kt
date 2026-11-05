package dev.lazier.pub.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads a small `.env` file.
 *
 * The real process environment always wins, so a value exported in the shell
 * overrides the file. No dependency, no reflection, and no attempt to mutate the
 * JVM environment: the caller merges the result with System.getenv().
 */
object DotEnv {

    fun load(file: Path = Paths.get(".env")): Map<String, String> {
        if (!Files.isRegularFile(file)) return emptyMap()

        return runCatching {
            Files.readAllLines(file)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    val key = line.substring(0, separator).trim()
                    val raw = line.substring(separator + 1).trim()
                    val value = raw.removeSurrounding("\"").removeSurrounding("'")
                    key to value
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}
