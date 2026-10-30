package com.example.messenger.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.ApiClient
import com.example.messenger.data.api.Session
import com.example.messenger.data.auth.AuthCall
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.ProfileCall
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.TokenRefresher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

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
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(
                repository = repository,
                sessionStore = sessionStore,
                tokens = ApiClient.bind(sessionStore).tokenRefresher,
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
