package dev.lazier.pub.server

/** An upstream service answered with an error. The HTTP adapter maps this to 502. */
class UpstreamException(
    val upstream: String,
    val status: Int,
    detail: String? = null,
) : RuntimeException("$upstream returned HTTP $status${detail?.let { ": $it" }.orEmpty()}")

/** The caller named something that does not exist. Maps to 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** The caller asked for something that conflicts with current state. Maps to 409. */
class ConflictException(message: String) : RuntimeException(message)

/** The caller did not authenticate. Maps to 401. */
class UnauthorizedException(message: String) : RuntimeException(message)

/** An upstream is not configured. Maps to 503. */
class NotConfiguredException(message: String) : RuntimeException(message)
