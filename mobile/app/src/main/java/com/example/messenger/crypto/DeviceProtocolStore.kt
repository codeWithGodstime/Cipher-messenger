package com.example.messenger.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID

/**
 * Libsignal state for one username: identity, prekeys, Kyber prekey, and sessions.
 * Remote sessions are keyed by the peer user id and device id.
 *
 * Identity changes are accepted. This app has no safety-number screen, so a peer
 * who reinstalls can still open a new session.
 */
class DeviceProtocolStore(
    context: Context,
    username: String,
    private val identity: IdentityStore,
    private val preKeys: LocalPreKeyStore,
) : SignalProtocolStore {
    private val account = username.trim().lowercase()
    private val lock = Any()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cipher_sessions",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasIdentity(): Boolean = identity.find(account) != null

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identity.find(account) ?: error("No identity key for this account")
    }

    override fun getLocalRegistrationId(): Int = SIGNAL_REGISTRATION_ID

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange = synchronized(lock) {
        val previous = remoteIdentity(address)
        prefs.edit()
            .putString(identitySlot(address), encode(identityKey.serialize()))
            .commit()
        if (previous != null && previous.serialize().contentEquals(identityKey.serialize()).not()) {
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        } else {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = synchronized(lock) {
        remoteIdentity(address)
    }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeys.oneTimeRecord(account, preKeyId)
            ?: throw InvalidKeyIdException("Missing one-time prekey $preKeyId")
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys.saveOneTimeRecord(account, record)
    }

    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.hasOneTimeRecord(account, preKeyId)

    override fun removePreKey(preKeyId: Int) {
        preKeys.deleteOneTimeRecord(account, preKeyId)
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord = synchronized(lock) {
        val raw = prefs.getString(sessionSlot(address), null) ?: return SessionRecord()
        try {
            SessionRecord(decode(raw))
        } catch (_: InvalidMessageException) {
            SessionRecord()
        }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        val records = ArrayList<SessionRecord>(addresses.size)
        for (address in addresses) {
            if (!containsSession(address)) {
                throw NoSessionException(address, "No session for $address")
            }
            records.add(loadSession(address))
        }
        return records
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> = synchronized(lock) {
        val prefix = sessionPrefix(name)
        val devices = ArrayList<Int>()
        for (key in prefs.all.keys) {
            if (!key.startsWith(prefix)) continue
            key.removePrefix(prefix).toIntOrNull()?.let { devices.add(it) }
        }
        devices
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) = synchronized(lock) {
        prefs.edit()
            .putString(sessionSlot(address), encode(record.serialize()))
            .commit()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = synchronized(lock) {
        prefs.contains(sessionSlot(address))
    }

    override fun deleteSession(address: SignalProtocolAddress) = synchronized(lock) {
        prefs.edit().remove(sessionSlot(address)).commit()
    }

    override fun deleteAllSessions(name: String) = synchronized(lock) {
        val prefix = sessionPrefix(name)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) editor.remove(key)
        }
        editor.commit()
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val record = preKeys.signedRecord(account)
            ?: throw InvalidKeyIdException("Missing signed prekey $signedPreKeyId")
        if (record.id != signedPreKeyId) {
            throw InvalidKeyIdException("Missing signed prekey $signedPreKeyId")
        }
        return record
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        val record = preKeys.signedRecord(account) ?: return mutableListOf()
        return mutableListOf(record)
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        preKeys.saveSignedRecord(account, record)
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return preKeys.signedRecord(account)?.id == signedPreKeyId
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) = Unit

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        val record = preKeys.kyberRecord(account)
            ?: throw InvalidKeyIdException("Missing Kyber prekey $kyberPreKeyId")
        if (record.id != kyberPreKeyId) {
            throw InvalidKeyIdException("Missing Kyber prekey $kyberPreKeyId")
        }
        return record
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> {
        val record = preKeys.kyberRecord(account) ?: return mutableListOf()
        return mutableListOf(record)
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        preKeys.saveKyberRecord(account, record)
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return preKeys.kyberRecord(account)?.id == kyberPreKeyId
    }

    override fun markKyberPreKeyUsed(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ECPublicKey,
    ) = synchronized(lock) {
        val token = "$kyberPreKeyId:$signedPreKeyId:${encode(baseKey.serialize())}"
        val slot = usedKyberSlot()
        val existing = prefs.getString(slot, "").orEmpty()
        val seen = existing.lineSequence().filter { it.isNotEmpty() }.toMutableSet()
        if (!seen.add(token)) {
            throw ReusedBaseKeyException("Kyber prekey $kyberPreKeyId was reused with the same base key")
        }
        val written = prefs.edit().putString(slot, seen.joinToString("\n")).commit()
        if (!written) error("Couldn't store the Kyber prekey use")
    }

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord,
    ) = Unit

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? = null

    private fun remoteIdentity(address: SignalProtocolAddress): IdentityKey? {
        val raw = prefs.getString(identitySlot(address), null) ?: return null
        return IdentityKey(decode(raw))
    }

    private fun identitySlot(address: SignalProtocolAddress): String =
        "id:$account:${address.name}:${address.deviceId}"

    private fun sessionSlot(address: SignalProtocolAddress): String =
        "${sessionPrefix(address.name)}${address.deviceId}"

    private fun sessionPrefix(name: String): String = "session:$account:$name:"

    private fun usedKyberSlot(): String = "kyber-used:$account"

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)
}
