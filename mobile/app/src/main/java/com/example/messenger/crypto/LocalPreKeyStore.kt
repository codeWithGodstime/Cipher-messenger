package com.example.messenger.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * Signed prekey and one-time prekeys for one username.
 * Private key material stays in the serialized libsignal records.
 */
class GeneratedPreKeys(
    val signedKeyId: Int,
    val signedPublicKey: String,
    val signature: String,
    val oneTime: List<OneTimePreKeyPublic>,
    val signedRecord: SignedPreKeyRecord,
    val oneTimeRecords: List<PreKeyRecord>,
)

class OneTimePreKeyPublic(
    val keyId: Int,
    val publicKey: String,
)

class StoredPreKeys(
    val signed: SignedPreKeyRecord,
    val oneTime: List<PreKeyRecord>,
)

/**
 * Prekey records stored on this device, one slot per username.
 * libsignal 0.105 no longer generates these from KeyHelper, so this store
 * follows the same steps: a Curve25519 pair, and an identity signature
 * over the signed prekey public key.
 */
class LocalPreKeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cipher_prekeys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isPublished(username: String): Boolean =
        prefs.getBoolean(publishedSlot(username), false)

    fun generate(identity: IdentityKeyPair): GeneratedPreKeys {
        val signedPair = ECKeyPair.generate()
        val signedPublic = signedPair.publicKey.serialize()
        val signature = identity.privateKey.calculateSignature(signedPublic)
        val signed = SignedPreKeyRecord(
            SIGNED_PRE_KEY_ID,
            System.currentTimeMillis(),
            signedPair,
            signature,
        )
        val oneTimeRecords = ArrayList<PreKeyRecord>(ONE_TIME_PRE_KEY_COUNT)
        val oneTimePublic = ArrayList<OneTimePreKeyPublic>(ONE_TIME_PRE_KEY_COUNT)
        val start = ONE_TIME_PRE_KEY_START - 1
        for (offset in 0 until ONE_TIME_PRE_KEY_COUNT) {
            val id = ((start + offset) % (MEDIUM_MAX_VALUE - 1)) + 1
            val pair = ECKeyPair.generate()
            oneTimeRecords += PreKeyRecord(id, pair)
            oneTimePublic += OneTimePreKeyPublic(id, encode(pair.publicKey.serialize()))
        }
        return GeneratedPreKeys(
            signedKeyId = signed.id,
            signedPublicKey = encode(signedPublic),
            signature = encode(signature),
            oneTime = oneTimePublic,
            signedRecord = signed,
            oneTimeRecords = oneTimeRecords,
        )
    }

    fun save(username: String, keys: GeneratedPreKeys) {
        val oneTime = keys.oneTimeRecords.joinToString("\n") { encode(it.serialize()) }
        val written = prefs.edit()
            .putString(signedSlot(username), encode(keys.signedRecord.serialize()))
            .putString(oneTimeSlot(username), oneTime)
            .putBoolean(publishedSlot(username), true)
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun find(username: String): StoredPreKeys? {
        if (!isPublished(username)) return null
        val signedRaw = prefs.getString(signedSlot(username), null) ?: return null
        val oneTimeRaw = prefs.getString(oneTimeSlot(username), null) ?: return null
        val oneTime = oneTimeRaw.lineSequence()
            .filter { it.isNotEmpty() }
            .map { PreKeyRecord(decode(it)) }
            .toList()
        return StoredPreKeys(SignedPreKeyRecord(decode(signedRaw)), oneTime)
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)

    private fun signedSlot(username: String): String = "signed:${slot(username)}"

    private fun oneTimeSlot(username: String): String = "onetime:${slot(username)}"

    private fun publishedSlot(username: String): String = "published:${slot(username)}"

    private fun slot(username: String): String = username.trim().lowercase()

    private companion object {
        const val SIGNED_PRE_KEY_ID = 1
        const val ONE_TIME_PRE_KEY_START = 1
        const val ONE_TIME_PRE_KEY_COUNT = 100

        /** libsignal medium integer range used by prekey ids. */
        const val MEDIUM_MAX_VALUE = 0xFFFFFF
    }
}
