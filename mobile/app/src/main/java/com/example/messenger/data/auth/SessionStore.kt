package com.example.messenger.data.auth

import android.content.Context
import android.util.Base64
import com.example.messenger.data.api.Profile
import com.example.messenger.data.api.Session
import com.example.messenger.data.api.TokenPair

/**
 * Survives process death so a signed-in user lands on chats on the next launch.
 * Tokens stay in private app prefs until logout (or a failed refresh) clears them.
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile
    private var cachedAccess: String? = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }

    @Volatile
    private var cachedRefresh: String? = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }

    @Volatile
    private var cachedAccessExpiresAt: Long = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false) &&
            !accessToken.isNullOrBlank() &&
            !refreshToken.isNullOrBlank()

    val accessToken: String?
        get() = cachedAccess

    val refreshToken: String?
        get() = cachedRefresh

    /** Epoch millis when the access token expires. 0 when unknown (refresh soon). */
    val accessExpiresAtMillis: Long
        get() {
            val stored = cachedAccessExpiresAt
            if (stored > 0L) return stored
            return jwtExpMillis(cachedAccess) ?: 0L
        }

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    val displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty()

    val usernameLabel: String
        get() = prefs.getString(KEY_USERNAME_LABEL, "").orEmpty()
            .ifBlank { prefs.getString(KEY_PHONE_LABEL, "").orEmpty() }

    fun rememberSession(session: Session) {
        synchronized(lock) {
            cachedAccess = session.accessToken
            cachedRefresh = session.refreshToken
            cachedAccessExpiresAt = expiresAtFrom(session.expiresIn, session.accessToken)
            prefs.edit()
                .putString(KEY_ACCESS, session.accessToken)
                .putString(KEY_REFRESH, session.refreshToken)
                .putLong(KEY_ACCESS_EXPIRES_AT, cachedAccessExpiresAt)
                .putString(KEY_USER_ID, session.profile.id)
                .putString(KEY_DISPLAY_NAME, session.profile.displayName)
                .putString(KEY_USERNAME_LABEL, session.profile.username)
                .commit()
        }
    }

    fun rememberTokens(pair: TokenPair) {
        synchronized(lock) {
            cachedAccess = pair.accessToken
            cachedRefresh = pair.refreshToken
            cachedAccessExpiresAt = expiresAtFrom(pair.expiresIn, pair.accessToken)
            prefs.edit()
                .putString(KEY_ACCESS, pair.accessToken)
                .putString(KEY_REFRESH, pair.refreshToken)
                .putLong(KEY_ACCESS_EXPIRES_AT, cachedAccessExpiresAt)
                .commit()
        }
    }

    fun rememberProfile(profile: Profile) {
        prefs.edit()
            .putString(KEY_USER_ID, profile.id)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_USERNAME_LABEL, profile.username)
            .apply()
    }

    fun updateDisplayName(displayName: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply()
    }

    fun markLoggedIn(displayName: String, usernameLabel: String) {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_USERNAME_LABEL, usernameLabel)
            .apply()
    }

    /** True when the access token is missing, untracked, or within [skewMs] of expiry. */
    fun isAccessExpiringSoon(skewMs: Long = DEFAULT_SKEW_MS): Boolean {
        if (accessToken.isNullOrBlank()) return true
        val expiresAt = accessExpiresAtMillis
        if (expiresAt <= 0L) return true
        return System.currentTimeMillis() >= expiresAt - skewMs
    }

    fun clear() {
        synchronized(lock) {
            cachedAccess = null
            cachedRefresh = null
            cachedAccessExpiresAt = 0L
            prefs.edit().clear().commit()
        }
    }

    private fun expiresAtFrom(expiresInSeconds: Int, accessToken: String): Long {
        val fromJwt = jwtExpMillis(accessToken)
        if (fromJwt != null && fromJwt > 0L) return fromJwt
        val lifetimeMs = expiresInSeconds.coerceAtLeast(0).toLong() * 1_000L
        return System.currentTimeMillis() + lifetimeMs
    }

    private fun jwtExpMillis(jwt: String?): Long? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val padded = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
            val payload = String(Base64.decode(padded, Base64.URL_SAFE), Charsets.UTF_8)
            val match = EXP_CLAIM.find(payload) ?: return@runCatching null
            match.groupValues[1].toLong() * 1_000L
        }.getOrNull()
    }

    private companion object {
        const val PREFS = "cipher_session"
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_USERNAME_LABEL = "username_label"
        const val KEY_PHONE_LABEL = "phone_label"
        const val DEFAULT_SKEW_MS = 60_000L
        val EXP_CLAIM = Regex("\"exp\"\\s*:\\s*(\\d+)")
    }
}
