package dev.lazier.pub.server.adapter.outbound.filesystem

/**
 * Derives a series name and volume number from a book title.
 *
 * Real EPUBs often carry no series metadata at all. The title then holds the
 * only reliable signal, and it follows a few shapes:
 *
 *     Sword Art Online, Vol. 03: Fairy Dance
 *     A Certain Magical Index - Volume 21
 *     A Certain Magical Index: New Testament - Volume 01
 *     A Certain Magical Index - Volume SS1
 *
 * The parser is conservative. It only reports a series when the title names a
 * volume, because splitting on any dash or colon turns stand-alone titles into
 * false series.
 */
object SeriesParser {

    data class Result(val series: String?, val volume: String?)

    private val COMMA_VOLUME = Regex(
        """^(?<series>.+?)\s*,\s*Vol\.?\s*(?<volume>\d+|[IVXLC]+)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val DASH_VOLUME = Regex(
        """^(?<series>.+?)\s+[-–—]\s+Volume\s+(?<volume>\d+|SS\d*)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val PLAIN_VOLUME = Regex(
        """^(?<series>.+?)\s+Volume\s+(?<volume>\d+|SS\d*)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(title: String): Result {
        val clean = title.trim()
        if (clean.isEmpty()) return Result(null, null)

        for (pattern in listOf(COMMA_VOLUME, DASH_VOLUME, PLAIN_VOLUME)) {
            val match = pattern.find(clean) ?: continue
            val series = canonicalSeries(match.groups["series"]?.value)
            if (series != null) return Result(series, normalizeVolume(match.groups["volume"]?.value))
        }
        return Result(null, null)
    }

    /**
     * One spelling per series. A title gives "Index: New Testament" while a
     * directory gives "Index New Testament"; without this they become two
     * series in the same library.
     */
    fun canonicalSeries(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value
            .replace(':', ' ')
            .replace(WHITESPACE, " ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private val WHITESPACE = Regex("\\s+")

    private fun normalizeVolume(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value.toIntOrNull()?.toString() ?: value
    }
}
