package com.example.messenger.crypto

import org.signal.libsignal.protocol.SignalProtocolAddress

/** One device per account. Libsignal device ids are 1 through 127. */
const val SIGNAL_DEVICE_ID = 1

/**
 * Registration id stored in each session. This app has one device per account,
 * so every install uses the same id.
 */
const val SIGNAL_REGISTRATION_ID = 1

fun signalAddress(userId: String): SignalProtocolAddress =
    SignalProtocolAddress(userId, SIGNAL_DEVICE_ID)
