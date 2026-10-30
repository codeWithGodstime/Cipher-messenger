package com.example.messenger.data.auth

import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.api.RefreshRequest
import com.example.messenger.data.api.TokenPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Keeps the access token fresh: proactive refresh before expiry, and single-flight
 * refresh for OkHttp 401 retries. Uses a plain (unauthenticated) API client so refresh
 * does not recurse through the authenticator.
 */
class TokenRefresher(
    private val sessionStore: SessionStore,
    private val api: MessengerApi,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var loopJob: Job? = null

    private val _sessionLost = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sessionLost: SharedFlow<Unit> = _sessionLost.asSharedFlow()

    /**
     * Returns a usable access token, refreshing first when it is close to expiry.
     */
    suspend fun ensureFreshAccessToken(): String? = mutex.withLock {
        if (!sessionStore.isAccessExpiringSoon()) {
            return sessionStore.accessToken
        }
        return refreshLocked()
    }

    /** Forces a refresh (used after HTTP 401). */
    suspend fun refreshNow(): String? = mutex.withLock { refreshLocked() }

    /** Blocking wrapper for OkHttp's Authenticator thread. */
    fun refreshBlocking(): String? = runBlocking { refreshNow() }

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var failureBackoffMs = INITIAL_FAILURE_BACKOFF_MS
            while (isActive) {
                if (sessionStore.refreshToken.isNullOrBlank()) {
                    delay(IDLE_POLL_MS)
                    continue
                }
                val waitMs = waitBeforeRefreshMs()
                if (waitMs > 0L) {
                    delay(waitMs)
                }
                val token = ensureFreshAccessToken()
                if (token == null) {
                    delay(failureBackoffMs)
                    failureBackoffMs = (failureBackoffMs * 2).coerceAtMost(MAX_FAILURE_BACKOFF_MS)
                } else {
                    failureBackoffMs = INITIAL_FAILURE_BACKOFF_MS
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** Clears tokens and notifies the UI to return to auth. */
    fun signOut() {
        stop()
        sessionStore.clear()
        emitSessionLost()
    }

    private fun waitBeforeRefreshMs(): Long {
        val expiresAt = sessionStore.accessExpiresAtMillis
        if (expiresAt <= 0L || sessionStore.isAccessExpiringSoon()) {
            return 0L
        }
        val target = expiresAt - REFRESH_SKEW_MS
        return (target - System.currentTimeMillis()).coerceIn(0L, MAX_SLEEP_MS)
    }

    private suspend fun refreshLocked(): String? {
        val refresh = sessionStore.refreshToken ?: run {
            emitSessionLost()
            return null
        }
        return when (val result = callRefresh(refresh)) {
            is RefreshCall.Ok -> {
                sessionStore.rememberTokens(result.pair)
                result.pair.accessToken
            }
            is RefreshCall.Failed -> {
                if (result.clearSession) {
                    sessionStore.clear()
                    emitSessionLost()
                }
                null
            }
        }
    }

    private suspend fun callRefresh(refreshToken: String): RefreshCall {
        return try {
            val response = api.refresh(RefreshRequest(refreshToken))
            if (!response.isSuccessful) {
                val unauthorized = response.code() == 401
                RefreshCall.Failed(
                    message = errorMessage(json, response.code(), response.errorBody()?.string()),
                    clearSession = unauthorized,
                )
            } else {
                val pair = json.decodeFromString<TokenPair>(response.body()?.string().orEmpty())
                RefreshCall.Ok(pair)
            }
        } catch (_: IOException) {
            RefreshCall.Failed(message = "Can't reach the server.", clearSession = false)
        } catch (_: SerializationException) {
            RefreshCall.Failed(message = "The server returned a response the app could not read.", clearSession = false)
        }
    }

    private fun emitSessionLost() {
        _sessionLost.tryEmit(Unit)
    }

    private sealed interface RefreshCall {
        data class Ok(val pair: TokenPair) : RefreshCall
        data class Failed(val message: String, val clearSession: Boolean) : RefreshCall
    }

    private companion object {
        const val REFRESH_SKEW_MS = 60_000L
        const val MAX_SLEEP_MS = 5 * 60_000L
        const val IDLE_POLL_MS = 5_000L
        const val INITIAL_FAILURE_BACKOFF_MS = 2_000L
        const val MAX_FAILURE_BACKOFF_MS = 60_000L
    }
}
