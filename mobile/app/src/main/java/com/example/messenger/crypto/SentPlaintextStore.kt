package com.example.messenger.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Plaintext of messages this device sent.
 * Signal ciphertext is decryptable by the recipient, so the sender keeps a copy
 * keyed by the ciphertext that was uploaded.
 */
class SentPlaintextStore(context: Context, username: String) {
    private val account = username.trim().lowercase()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cipher_sent_plaintext",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun remember(ciphertext: String, plaintext: String) {
        if (account.isEmpty() || ciphertext.isEmpty()) return
        prefs.edit().putString(slot(ciphertext), plaintext).commit()
    }

    fun find(ciphertext: String): String? {
        if (account.isEmpty() || ciphertext.isEmpty()) return null
        return prefs.getString(slot(ciphertext), null)
    }

    /** Drops every plaintext copy stored for this account. */
    fun clearAccount() {
        if (account.isEmpty()) {
            prefs.edit().clear().commit()
            return
        }
        val prefix = "$account:"
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) editor.remove(key)
        }
        editor.commit()
    }

    private fun slot(ciphertext: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(ciphertext.toByteArray(Charsets.UTF_8))
        val hash = Base64.encodeToString(digest, Base64.NO_WRAP)
        return "$account:$hash"
    }
}
