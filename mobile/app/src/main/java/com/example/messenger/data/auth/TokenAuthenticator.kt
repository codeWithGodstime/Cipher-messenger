package com.example.messenger.data.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On 401, rotates tokens once and retries the request with the new access token.
 */
class TokenAuthenticator(
    private val refresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/otp") ||
            path.endsWith("/auth/verify")
        ) {
            return null
        }
        val token = refresher.refreshBlocking() ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
