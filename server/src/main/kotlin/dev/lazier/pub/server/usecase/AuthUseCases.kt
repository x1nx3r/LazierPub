package dev.lazier.pub.server.usecase

import dev.lazier.pub.server.UnauthorizedException
import dev.lazier.pub.shared.LoginRequest
import dev.lazier.pub.shared.LoginResponse
import java.security.MessageDigest

/**
 * Exchange a password for the server token.
 *
 * `LAZIER_PASSWORD` wins when it is set, so the token can stay out of the app
 * and out of muscle memory. Without it, the token itself is the password, which
 * keeps an existing single-variable deployment working.
 *
 * The comparison is constant-time. A wrong password is a 401, not a 400: the
 * caller may retry.
 */
class Login(
    private val token: String,
    private val password: String?,
) {

    operator fun invoke(request: LoginRequest): LoginResponse {
        val expected = password?.takeIf { it.isNotBlank() } ?: token
        if (!constantTimeEquals(request.password, expected)) {
            throw UnauthorizedException("Wrong password")
        }
        return LoginResponse(token = token)
    }

    private fun constantTimeEquals(supplied: String, expected: String): Boolean =
        MessageDigest.isEqual(supplied.toByteArray(), expected.toByteArray())
}
