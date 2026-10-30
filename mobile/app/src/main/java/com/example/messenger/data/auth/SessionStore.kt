package com.example.messenger.data.auth

import android.content.Context
import com.example.messenger.data.api.Session

/**
 * Survives process death so a signed-in user lands on chats on the next launch.
 * Tokens stay in private app prefs until a real logout path clears them.
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false) && !accessToken.isNullOrBlank()

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }

    val displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty()

    val phoneLabel: String
        get() = prefs.getString(KEY_PHONE_LABEL, "").orEmpty()

    fun rememberSession(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_DISPLAY_NAME, session.profile.displayName)
            .putString(KEY_PHONE_LABEL, session.profile.phone)
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

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "cipher_session"
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PHONE_LABEL = "phone_label"
    }
}
