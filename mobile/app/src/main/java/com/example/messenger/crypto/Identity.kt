package com.example.messenger.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.libsignal.protocol.IdentityKeyPair

/**
 * Identity key pairs stored on this device, one slot per username.
 * A lookup reads only that username's slot.
 */
class IdentityStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cipher_identity",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_GCM,
    )

    fun find(username: String): IdentityKeyPair? {
        val existing = prefs.getString(slot(username), null) ?: return null
        return IdentityKeyPair(Base64.decode(existing, Base64.NO_WRAP))
    }

    fun generate(): IdentityKeyPair = IdentityKeyPair.generate()

    fun save(username: String, identity: IdentityKeyPair) {
        prefs.edit()
            .putString(slot(username), Base64.encodeToString(identity.serialize(), Base64.NO_WRAP))
            .commit()
    }

    fun publicKeyBase64(identity: IdentityKeyPair): String =
        Base64.encodeToString(identity.publicKey.serialize(), Base64.NO_WRAP)

    private fun slot(username: String): String = "identity:${username.trim().lowercase()}"
}
