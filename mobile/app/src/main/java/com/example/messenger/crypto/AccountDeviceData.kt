package com.example.messenger.crypto

/**
 * On-device keys and message state for the account that is signed in.
 * [clear] removes that account's copies so the next account cannot read them.
 */
class AccountDeviceData(
    private val username: String,
    private val identity: IdentityStore,
    private val preKeys: LocalPreKeyStore,
    private val sessions: DeviceProtocolStore,
    private val sent: SentPlaintextStore,
) {
    fun clear() {
        val account = username.trim().lowercase()
        sessions.clearAccount()
        sent.clearAccount()
        if (account.isEmpty()) {
            identity.clearAll()
            preKeys.clearAll()
        } else {
            identity.delete(account)
            preKeys.delete(account)
        }
    }
}
