package dev.lazier.pub.server.adapter.outbound.prowlarr

/**
 * The only format evidence in a Prowlarr search response is the release name.
 * Titles such as "v01-09 (epub)" or "[PDF]" are common; when nothing is named
 * the list stays empty and the client says so.
 */
private val formatPattern = Regex(
    """(?i)(?<![a-z0-9])(epub|pdf|cbz|cbr|mobi|azw3|azw|fb2|djvu|mp3|m4b|flac|zip|rar|7z)(?![a-z0-9])""",
)

internal fun formatsIn(vararg text: String?): List<String> =
    text.filterNotNull()
        .flatMap { value -> formatPattern.findAll(value).map { it.groupValues[1].uppercase() }.toList() }
        .distinct()
