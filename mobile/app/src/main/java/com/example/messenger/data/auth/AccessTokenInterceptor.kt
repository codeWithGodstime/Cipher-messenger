package com.example.messenger.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Replaces a stale Authorization header with the latest stored access token
 * so in-flight calls pick up a refresh that finished after they started.
 */
class AccessTokenInterceptor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header("Authorization") == null) {
            return chain.proceed(original)
        }
        val token = sessionStore.accessToken
        if (token.isNullOrBlank()) {
            return chain.proceed(original)
        }
        return chain.proceed(
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}
