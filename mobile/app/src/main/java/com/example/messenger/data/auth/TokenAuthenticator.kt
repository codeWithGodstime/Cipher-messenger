package com.example.messenger.data.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On 401, retries with a current access token. Refreshes only when the
 * request still carries the same expired token another call has not replaced.
 */
class TokenAuthenticator(
    private val refresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/signup")
        ) {
            return null
        }
        val failed = bearerToken(response.request)
        val token = refresher.refreshBlocking(failed) ?: return null
        if (token == failed) return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private fun bearerToken(request: Request): String? {
        val header = request.header("Authorization") ?: return null
        return header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
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
