package com.example.messenger.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.messenger.crypto.IdentityStore
import com.example.messenger.crypto.LocalPreKeyStore
import com.example.messenger.data.api.ApiClient
import com.example.messenger.data.api.OneTimePreKeyRequest
import com.example.messenger.data.api.Session
import org.signal.libsignal.protocol.IdentityKeyPair
import com.example.messenger.data.auth.AuthCall
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.ProfileCall
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.TokenRefresher
import com.example.messenger.data.keys.KeyCall
import com.example.messenger.data.keys.KeyRepository
import com.example.messenger.data.keys.KyberPreKeyUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthMode {
    Login,
    Signup,
}

sealed interface AuthEvent {
    data object OpenProfile : AuthEvent
    data object EnterHome : AuthEvent
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
    private val tokens: TokenRefresher,
    private val keys: KeyRepository,
    private val identity: IdentityStore,
    private val preKeys: LocalPreKeyStore,
    private val kyber: KyberPreKeyUpload,
) : ViewModel() {
    var usernameLoading by mutableStateOf(false)
        private set
    var usernameError by mutableStateOf<String?>(null)
        private set

    var profileSaving by mutableStateOf(false)
        private set
    var profileSaved by mutableStateOf(false)
        private set
    var profileError by mutableStateOf<String?>(null)
        private set

    private val events = Channel<AuthEvent>(capacity = Channel.BUFFERED)
    val authEvents = events.receiveAsFlow()

    private var lastUsername: String = ""

    fun submitUsername(raw: String, mode: AuthMode) {
        if (usernameLoading) return
        val username = raw.trim().lowercase()
        if (username.isEmpty()) {
            usernameError = "Enter a username to continue."
            return
        }
        lastUsername = username
        viewModelScope.launch {
            usernameLoading = true
            usernameError = null
            val result = when (mode) {
                AuthMode.Login -> repository.login(username)
                AuthMode.Signup -> repository.signup(username)
            }
            when (result) {
                is AuthCall.Success -> {
                    rememberSession(result.session)
                    val identityReady = when (mode) {
                        AuthMode.Signup -> createAndUploadIdentity(result.session)
                        AuthMode.Login -> useLocalIdentity(result.session)
                    }
                    if (!identityReady) {
                        usernameLoading = false
                        return@launch
                    }
                    usernameLoading = false
                    if (mode == AuthMode.Signup || result.session.profile.displayName.isBlank()) {
                        events.send(AuthEvent.OpenProfile)
                    } else {
                        events.send(AuthEvent.EnterHome)
                    }
                }
                is AuthCall.Failed -> {
                    usernameLoading = false
                    usernameError = result.message
                }
            }
        }
    }

    fun saveName(name: String) {
        if (profileSaving || profileSaved) return
        viewModelScope.launch {
            val token = tokens.ensureFreshAccessToken()
            if (token == null) {
                profileError = "Sign in again before saving your name."
                return@launch
            }
            profileSaving = true
            profileError = null
            when (val result = repository.updateDisplayName(token, name)) {
                is ProfileCall.Success -> {
                    sessionStore.rememberProfile(result.profile)
                    profileSaving = false
                    profileSaved = true
                }
                is ProfileCall.Failed -> {
                    profileSaving = false
                    profileError = result.message
                }
            }
        }
    }

    fun consumeUsernameError() {
        usernameError = null
    }

    fun consumeProfileError() {
        profileError = null
    }

    fun usernameLabel(): String = lastUsername.ifBlank { sessionStore.usernameLabel }

    private fun rememberSession(session: Session) {
        sessionStore.rememberSession(session)
        lastUsername = session.profile.username
    }

    private suspend fun useLocalIdentity(session: Session): Boolean {
        val username = session.profile.username
        val existing = withContext(Dispatchers.IO) { runCatching { identity.find(username) } }
        return existing.fold(
            onSuccess = { stored ->
                if (stored != null) uploadPreKeys(session, stored) else createAndUploadIdentity(session)
            },
            onFailure = { error ->
                Log.w(TAG, "identity lookup failed", error)
                usernameError = "Couldn't read the identity key stored for this username."
                false
            },
        )
    }

    private suspend fun createAndUploadIdentity(session: Session): Boolean {
        val username = session.profile.username
        val created = withContext(Dispatchers.IO) {
            runCatching { identity.generate() }
                .onFailure { error -> Log.w(TAG, "identity key unavailable", error) }
                .getOrNull()
        }
        if (created == null) {
            usernameError = "Couldn't create your identity key on this device."
            return false
        }
        val publicKey = identity.publicKeyBase64(created)
        return when (val result = keys.publishIdentityKey(session.accessToken, publicKey)) {
            KeyCall.Ok -> {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { identity.save(username, created) }.isSuccess
                }
                if (!saved) {
                    usernameError = "Couldn't store your identity key on this device."
                    return false
                }
                uploadPreKeys(session, created)
            }
            is KeyCall.Failed -> {
                usernameError = result.message
                false
            }
        }
    }

    /**
     * Uploads a signed prekey and one-time prekeys for an identity already stored
     * on this device. Private keys are saved before the upload. A failed upload
     * leaves them pending so the next sign-in sends the same keys.
     */
    private suspend fun uploadPreKeys(session: Session, identityKey: IdentityKeyPair): Boolean {
        val username = session.profile.username
        val oneTime = withContext(Dispatchers.IO) {
            runCatching { preKeys.prepareForUpload(username, identityKey) }
                .onFailure { error -> Log.w(TAG, "prekey preparation failed", error) }
                .getOrNull()
        }
        if (oneTime == null) {
            val published = withContext(Dispatchers.IO) {
                runCatching { preKeys.isPublished(username) }.getOrDefault(false)
            }
            if (published) return publishKyber(session)
            usernameError = "Couldn't create your prekeys on this device."
            return false
        }
        val signed = withContext(Dispatchers.IO) {
            runCatching { preKeys.signedUpload(username) }.getOrNull()
        }
        if (signed == null) {
            usernameError = "Couldn't create your prekeys on this device."
            return false
        }
        when (val uploaded = keys.publishSignedPreKey(
            session.accessToken,
            signed.keyId,
            signed.publicKey,
            signed.signature,
        )) {
            is KeyCall.Failed -> {
                usernameError = uploaded.message
                return false
            }
            KeyCall.Ok -> Unit
        }
        val body = oneTime.map { OneTimePreKeyRequest(it.keyId, it.publicKey) }
        when (val uploaded = keys.publishOneTimePreKeys(session.accessToken, body)) {
            is KeyCall.Failed -> {
                if (uploaded.status != HTTP_CONFLICT) {
                    usernameError = uploaded.message
                    return false
                }
                if (!uploadFreshOneTimePreKeys(session, username)) return false
            }
            KeyCall.Ok -> Unit
        }
        if (!publishKyber(session)) return false
        val saved = withContext(Dispatchers.IO) {
            runCatching { preKeys.confirmUpload(username) }.isSuccess
        }
        if (!saved) {
            usernameError = "Couldn't store your prekeys on this device."
        }
        return saved
    }

    private suspend fun publishKyber(session: Session): Boolean {
        return when (val result = kyber.publishIfNeeded(session.profile.username, session.accessToken)) {
            null, KeyCall.Ok -> true
            is KeyCall.Failed -> {
                usernameError = result.message
                false
            }
        }
    }

    /**
     * The pending ids are already on the server with different public keys.
     * Drops that batch and uploads the next ids. The caller confirms the upload.
     */
    private suspend fun uploadFreshOneTimePreKeys(session: Session, username: String): Boolean {
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
                preKeys.discardPending(username)
                preKeys.stageBatch(username)
            }.onFailure { error -> Log.w(TAG, "prekey generation failed", error) }
                .getOrNull()
        }
        if (fresh.isNullOrEmpty()) {
            usernameError = "Couldn't create your prekeys on this device."
            return false
        }
        val body = fresh.map { OneTimePreKeyRequest(it.keyId, it.publicKey) }
        return when (val uploaded = keys.publishOneTimePreKeys(session.accessToken, body)) {
            KeyCall.Ok -> true
            is KeyCall.Failed -> {
                usernameError = uploaded.message
                false
            }
        }
    }

    private companion object {
        const val TAG = "IdentityKey"
        const val HTTP_CONFLICT = 409
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            val bound = ApiClient.bind(sessionStore)
            val identity = IdentityStore(appContext)
            val preKeys = LocalPreKeyStore(appContext)
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(
                repository = repository,
                sessionStore = sessionStore,
                tokens = bound.tokenRefresher,
                keys = bound.keyRepository,
                identity = identity,
                preKeys = preKeys,
                kyber = KyberPreKeyUpload(identity, preKeys, bound.keyRepository),
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
