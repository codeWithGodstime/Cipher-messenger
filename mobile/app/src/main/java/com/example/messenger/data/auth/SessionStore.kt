package com.example.messenger.data.auth

import android.content.Context
import com.example.messenger.data.api.Profile
import com.example.messenger.data.api.Session
import com.example.messenger.data.api.TokenPair

/**
 * Survives process death so a signed-in user lands on chats on the next launch.
 * Tokens stay in private app prefs until a real logout path clears them.
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false) &&
            !accessToken.isNullOrBlank() &&
            !refreshToken.isNullOrBlank()

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }

    /** Epoch millis when the access token expires. 0 when unknown (refresh soon). */
    val accessExpiresAtMillis: Long
        get() = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    val displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty()

    val phoneLabel: String
        get() = prefs.getString(KEY_PHONE_LABEL, "").orEmpty()

    fun rememberSession(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, expiresAtFrom(session.expiresIn))
            .putString(KEY_USER_ID, session.profile.id)
            .putString(KEY_DISPLAY_NAME, session.profile.displayName)
            .putString(KEY_PHONE_LABEL, session.profile.phone)
            .apply()
    }

    fun rememberTokens(pair: TokenPair) {
        prefs.edit()
            .putString(KEY_ACCESS, pair.accessToken)
            .putString(KEY_REFRESH, pair.refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, expiresAtFrom(pair.expiresIn))
            .apply()
    }

    fun rememberProfile(profile: Profile) {
        prefs.edit()
            .putString(KEY_USER_ID, profile.id)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_PHONE_LABEL, profile.phone)
            .apply()
    }

    fun updateDisplayName(displayName: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply()
    }

    fun markLoggedIn(displayName: String, phoneLabel: String) {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_PHONE_LABEL, phoneLabel)
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
        prefs.edit().clear().apply()
    }

    private fun expiresAtFrom(expiresInSeconds: Int): Long {
        val lifetimeMs = expiresInSeconds.coerceAtLeast(0).toLong() * 1_000L
        return System.currentTimeMillis() + lifetimeMs
    }

    private companion object {
        const val PREFS = "cipher_session"
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PHONE_LABEL = "phone_label"
        const val DEFAULT_SKEW_MS = 60_000L
    }
}
