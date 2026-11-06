package com.example.messenger.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
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
    val kyber: KyberPreKeyUpload,
    val kyberRecord: KyberPreKeyRecord,
)

class OneTimePreKeyPublic(
    val keyId: Int,
    val publicKey: String,
)

/** Signed prekey fields the server stores for this account. */
class SignedPreKeyUpload(
    val keyId: Int,
    val publicKey: String,
    val signature: String,
)

/** Last-resort Kyber prekey fields the server stores for this account. */
class KyberPreKeyUpload(
    val keyId: Int,
    val publicKey: String,
    val signature: String,
)

class StoredPreKeys(
    val signed: SignedPreKeyRecord,
    val oneTime: List<PreKeyRecord>,
)

/**
 * Prekey records stored on this device, one slot per username.
 * libsignal 0.105 no longer generates these from KeyHelper, so this store
 * follows the same steps: a Curve25519 pair, a Kyber1024 pair, and an identity
 * signature over each public key.
 *
 * One-time prekeys are appended. A pending id list is the batch whose public
 * keys still need to reach the server. Private keys stay on the device until
 * a session consumes them.
 */
class LocalPreKeyStore(context: Context) {
    private val lock = Any()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cipher_prekeys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isPublished(username: String): Boolean = synchronized(lock) {
        prefs.getBoolean(publishedSlot(username), false)
    }

    fun hasSignedPreKey(username: String): Boolean = synchronized(lock) {
        prefs.getString(signedSlot(username), null) != null
    }

    fun isKyberPublished(username: String): Boolean = synchronized(lock) {
        prefs.getBoolean(kyberPublishedSlot(username), false)
    }

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
        val ids = allocateOneTimePreKeyIds(ONE_TIME_PRE_KEY_START, ONE_TIME_PRE_KEY_BATCH, emptySet())
        val oneTime = newOneTimeRecords(ids)
        val kyber = newKyber(identity)
        return GeneratedPreKeys(
            signedKeyId = signed.id,
            signedPublicKey = encode(signedPublic),
            signature = encode(signature),
            oneTime = oneTime.publicKeys,
            signedRecord = signed,
            oneTimeRecords = oneTime.records,
            kyber = kyber.upload,
            kyberRecord = kyber.record,
        )
    }

    /**
     * Stores a first batch and marks its ids as waiting for upload.
     * The published flag stays unset until the server accepts the batch.
     */
    fun storeInitial(username: String, keys: GeneratedPreKeys) = synchronized(lock) {
        val pending = keys.oneTimeRecords.joinToString("\n") { it.id.toString() }
        val next = nextOneTimePreKeyId(keys.oneTimeRecords.maxOf { it.id })
        val written = prefs.edit()
            .putString(signedSlot(username), encode(keys.signedRecord.serialize()))
            .putString(oneTimeSlot(username), encodeRecords(keys.oneTimeRecords))
            .putString(kyberSlot(username), encode(keys.kyberRecord.serialize()))
            .putString(pendingSlot(username), pending)
            .putInt(nextSlot(username), next)
            .putBoolean(publishedSlot(username), false)
            .putBoolean(kyberPublishedSlot(username), false)
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    /**
     * Null when this username is already published and no batch is waiting.
     * Otherwise the one-time public keys that still need an upload.
     */
    fun prepareForUpload(username: String, identity: IdentityKeyPair): List<OneTimePreKeyPublic>? {
        if (isPublished(username) && pendingOneTime(username) == null) return null
        if (!hasSignedPreKey(username)) {
            storeInitial(username, generate(identity))
        }
        return pendingOneTime(username)
            ?: error("Prekeys are stored without a one-time batch to upload.")
    }

    /**
     * Null when this username's Kyber prekey is already on the server.
     * Otherwise the public key that still needs an upload. A missing local
     * record is generated and kept until the upload is confirmed.
     */
    fun prepareKyber(username: String, identity: IdentityKeyPair): KyberPreKeyUpload? = synchronized(lock) {
        if (prefs.getBoolean(kyberPublishedSlot(username), false) && kyberRecord(username) != null) {
            return null
        }
        if (kyberRecord(username) == null) {
            val created = newKyber(identity)
            val written = prefs.edit()
                .putString(kyberSlot(username), encode(created.record.serialize()))
                .putBoolean(kyberPublishedSlot(username), false)
                .commit()
            if (!written) error("Couldn't store prekeys")
            return created.upload
        }
        kyberUploadLocked(username)
    }

    fun confirmKyber(username: String) = synchronized(lock) {
        val written = prefs.edit().putBoolean(kyberPublishedSlot(username), true).commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun signedUpload(username: String): SignedPreKeyUpload? = synchronized(lock) {
        val raw = prefs.getString(signedSlot(username), null) ?: return null
        val record = SignedPreKeyRecord(decode(raw))
        SignedPreKeyUpload(
            keyId = record.id,
            publicKey = encode(record.keyPair.publicKey.serialize()),
            signature = encode(record.signature),
        )
    }

    fun signedRecord(username: String): SignedPreKeyRecord? = synchronized(lock) {
        val raw = prefs.getString(signedSlot(username), null) ?: return null
        SignedPreKeyRecord(decode(raw))
    }

    fun saveSignedRecord(username: String, record: SignedPreKeyRecord) = synchronized(lock) {
        val written = prefs.edit()
            .putString(signedSlot(username), encode(record.serialize()))
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun kyberRecord(username: String): KyberPreKeyRecord? = synchronized(lock) {
        loadKyber(username)
    }

    fun saveKyberRecord(username: String, record: KyberPreKeyRecord) = synchronized(lock) {
        val written = prefs.edit()
            .putString(kyberSlot(username), encode(record.serialize()))
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun oneTimeRecord(username: String, id: Int): PreKeyRecord? = synchronized(lock) {
        loadOneTime(username).firstOrNull { it.id == id }
    }

    fun hasOneTimeRecord(username: String, id: Int): Boolean = oneTimeRecord(username, id) != null

    fun saveOneTimeRecord(username: String, record: PreKeyRecord) = synchronized(lock) {
        val next = loadOneTime(username).filter { it.id != record.id } + record
        writeOneTime(username, next)
    }

    fun deleteOneTimeRecord(username: String, id: Int) = synchronized(lock) {
        writeOneTime(username, loadOneTime(username).filter { it.id != id })
    }

    /** Public keys for the batch that has not been confirmed on the server. */
    fun pendingOneTime(username: String): List<OneTimePreKeyPublic>? = synchronized(lock) {
        val raw = prefs.getString(pendingSlot(username), null) ?: return null
        val ids = raw.lineSequence().mapNotNull { it.toIntOrNull() }.toList()
        if (ids.isEmpty()) return null
        val byId = loadOneTime(username).associateBy { it.id }
        return ids.map { id ->
            val record = byId[id] ?: run {
                prefs.edit().remove(pendingSlot(username)).commit()
                return null
            }
            OneTimePreKeyPublic(id, encode(record.keyPair.publicKey.serialize()))
        }
    }

    /**
     * Adds a batch of new one-time prekeys and marks them pending.
     * A batch already pending is returned unchanged.
     */
    fun stageBatch(username: String): List<OneTimePreKeyPublic> = synchronized(lock) {
        pendingOneTime(username)?.let { return it }
        val existing = loadOneTime(username)
        val ids = allocateOneTimePreKeyIds(
            start = nextStart(username, existing),
            count = ONE_TIME_PRE_KEY_BATCH,
            used = existing.map { it.id }.toSet(),
        )
        if (ids.isEmpty()) return emptyList()
        val created = newOneTimeRecords(ids)
        val written = prefs.edit()
            .putString(oneTimeSlot(username), encodeRecords(existing + created.records))
            .putString(pendingSlot(username), ids.joinToString("\n"))
            .putInt(nextSlot(username), nextOneTimePreKeyId(ids.last()))
            .commit()
        if (!written) error("Couldn't store prekeys")
        created.publicKeys
    }

    /** Drops the pending id list. Private keys and the next id stay on the device. */
    fun discardPending(username: String) = synchronized(lock) {
        val written = prefs.edit().remove(pendingSlot(username)).commit()
        if (!written) error("Couldn't store prekeys")
    }

    /** Drops the pending batch and records that the server has this user's prekeys. */
    fun confirmUpload(username: String) = synchronized(lock) {
        val written = prefs.edit()
            .remove(pendingSlot(username))
            .putBoolean(publishedSlot(username), true)
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun delete(username: String) = synchronized(lock) {
        val written = prefs.edit()
            .remove(signedSlot(username))
            .remove(kyberSlot(username))
            .remove(kyberPublishedSlot(username))
            .remove(oneTimeSlot(username))
            .remove(pendingSlot(username))
            .remove(nextSlot(username))
            .remove(publishedSlot(username))
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun clearAll() = synchronized(lock) {
        val written = prefs.edit().clear().commit()
        if (!written) error("Couldn't store prekeys")
    }

    fun find(username: String): StoredPreKeys? = synchronized(lock) {
        if (!prefs.getBoolean(publishedSlot(username), false)) return null
        val signedRaw = prefs.getString(signedSlot(username), null) ?: return null
        StoredPreKeys(SignedPreKeyRecord(decode(signedRaw)), loadOneTime(username))
    }

    private fun nextStart(username: String, existing: List<PreKeyRecord>): Int {
        val stored = prefs.getInt(nextSlot(username), 0)
        if (stored in 1..ONE_TIME_PRE_KEY_ID_MAX) return stored
        val maxId = existing.maxOfOrNull { it.id } ?: 0
        return if (maxId <= 0 || maxId >= ONE_TIME_PRE_KEY_ID_MAX) 1 else maxId + 1
    }

    private fun newKyber(identity: IdentityKeyPair): KyberBatch {
        val pair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val publicKey = pair.publicKey.serialize()
        val signature = identity.privateKey.calculateSignature(publicKey)
        val record = KyberPreKeyRecord(
            KYBER_PRE_KEY_ID,
            System.currentTimeMillis(),
            pair,
            signature,
        )
        return KyberBatch(
            record = record,
            upload = KyberPreKeyUpload(
                keyId = record.id,
                publicKey = encode(publicKey),
                signature = encode(signature),
            ),
        )
    }

    private fun loadKyber(username: String): KyberPreKeyRecord? {
        val raw = prefs.getString(kyberSlot(username), null) ?: return null
        return KyberPreKeyRecord(decode(raw))
    }

    private fun kyberUploadLocked(username: String): KyberPreKeyUpload? {
        val record = loadKyber(username) ?: return null
        val publicKey = record.keyPair.publicKey.serialize()
        return KyberPreKeyUpload(record.id, encode(publicKey), encode(record.signature))
    }

    private fun writeOneTime(username: String, records: List<PreKeyRecord>) {
        val written = prefs.edit()
            .putString(oneTimeSlot(username), encodeRecords(records))
            .commit()
        if (!written) error("Couldn't store prekeys")
    }

    private fun newOneTimeRecords(ids: List<Int>): OneTimeBatch {
        val records = ArrayList<PreKeyRecord>(ids.size)
        val publicKeys = ArrayList<OneTimePreKeyPublic>(ids.size)
        for (id in ids) {
            val pair = ECKeyPair.generate()
            records += PreKeyRecord(id, pair)
            publicKeys += OneTimePreKeyPublic(id, encode(pair.publicKey.serialize()))
        }
        return OneTimeBatch(records, publicKeys)
    }

    private fun loadOneTime(username: String): List<PreKeyRecord> {
        val raw = prefs.getString(oneTimeSlot(username), null) ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotEmpty() }
            .map { PreKeyRecord(decode(it)) }
            .toList()
    }

    private fun encodeRecords(records: List<PreKeyRecord>): String =
        records.joinToString("\n") { encode(it.serialize()) }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)

    private fun signedSlot(username: String): String = "signed:${slot(username)}"

    private fun kyberSlot(username: String): String = "kyber:${slot(username)}"

    private fun kyberPublishedSlot(username: String): String = "kyber-published:${slot(username)}"

    private fun oneTimeSlot(username: String): String = "onetime:${slot(username)}"

    private fun pendingSlot(username: String): String = "pending:${slot(username)}"

    private fun nextSlot(username: String): String = "next:${slot(username)}"

    private fun publishedSlot(username: String): String = "published:${slot(username)}"

    private fun slot(username: String): String = username.trim().lowercase()

    private class OneTimeBatch(
        val records: List<PreKeyRecord>,
        val publicKeys: List<OneTimePreKeyPublic>,
    )

    private class KyberBatch(
        val record: KyberPreKeyRecord,
        val upload: KyberPreKeyUpload,
    )

    private companion object {
        const val SIGNED_PRE_KEY_ID = 1
        const val KYBER_PRE_KEY_ID = 1
        const val ONE_TIME_PRE_KEY_START = 1
    }
}
