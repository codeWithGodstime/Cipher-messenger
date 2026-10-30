package com.example.messenger.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.Session
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.OtpCall
import com.example.messenger.data.auth.ProfileCall
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.VerifyCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface AuthEvent {
    data object OpenVerify : AuthEvent
    data object OpenProfile : AuthEvent
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    var phoneLoading by mutableStateOf(false)
        private set
    var phoneError by mutableStateOf<String?>(null)
        private set

    var code by mutableStateOf("")
        private set
    var verifyPhase by mutableStateOf(VerifyPhase.Waiting)
        private set
    var verifyError by mutableStateOf<String?>(null)
        private set
    var resendGeneration by mutableIntStateOf(0)
        private set

    var profileSaving by mutableStateOf(false)
        private set
    var profileSaved by mutableStateOf(false)
        private set
    var profileError by mutableStateOf<String?>(null)
        private set

    /** True after a code was sent. False when login skipped the code and opened profile. */
    var codeStepRequired by mutableStateOf(false)
        private set

    private val events = Channel<AuthEvent>(capacity = Channel.BUFFERED)
    val authEvents = events.receiveAsFlow()

    private var phoneE164: String = ""
    private var accessToken: String? = sessionStore.accessToken
    private var resendInFlight = false

    fun requestCode(e164: String) {
        if (phoneLoading) return
        phoneE164 = e164
        viewModelScope.launch {
            phoneLoading = true
            phoneError = null
            when (val result = repository.requestOtp(e164)) {
                is OtpCall.CodeSent -> {
                    codeStepRequired = true
                    prepareVerify()
                    phoneLoading = false
                    events.send(AuthEvent.OpenVerify)
                }
                is OtpCall.SignedIn -> {
                    rememberSession(result.session)
                    codeStepRequired = false
                    phoneLoading = false
                    events.send(AuthEvent.OpenProfile)
                }
                is OtpCall.Failed -> {
                    phoneLoading = false
                    phoneError = result.message
                }
            }
        }
    }

    fun prepareVerify() {
        code = ""
        verifyPhase = VerifyPhase.Waiting
        verifyError = null
    }

    fun updateCode(next: String) {
        if (verifyPhase == VerifyPhase.Checking || verifyPhase == VerifyPhase.Verified) return
        code = next
    }

    fun submitCode(submitted: String) {
        if (verifyPhase == VerifyPhase.Checking || verifyPhase == VerifyPhase.Verified) return
        viewModelScope.launch {
            verifyPhase = VerifyPhase.Checking
            verifyError = null
            when (val result = repository.verify(phoneE164, submitted)) {
                is VerifyCall.Success -> {
                    rememberSession(result.session)
                    verifyPhase = VerifyPhase.Verified
                }
                is VerifyCall.Failed -> {
                    code = ""
                    verifyPhase = VerifyPhase.Waiting
                    verifyError = result.message
                }
            }
        }
    }

    fun resendCode() {
        if (resendInFlight || verifyPhase == VerifyPhase.Checking || phoneE164.isEmpty()) return
        resendInFlight = true
        viewModelScope.launch {
            try {
                when (val result = repository.requestOtp(phoneE164)) {
                    is OtpCall.CodeSent -> {
                        code = ""
                        verifyPhase = VerifyPhase.Waiting
                        resendGeneration += 1
                    }
                    is OtpCall.SignedIn -> {
                        rememberSession(result.session)
                        codeStepRequired = false
                        events.send(AuthEvent.OpenProfile)
                    }
                    is OtpCall.Failed -> verifyError = result.message
                }
            } finally {
                resendInFlight = false
            }
        }
    }

    fun saveName(name: String) {
        val token = accessToken
        if (token == null) {
            profileError = "Sign in again before saving your name."
            return
        }
        if (profileSaving || profileSaved) return
        viewModelScope.launch {
            profileSaving = true
            profileError = null
            when (val result = repository.updateDisplayName(token, name)) {
                is ProfileCall.Success -> {
                    sessionStore.updateDisplayName(result.profile.displayName)
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

    fun consumePhoneError() {
        phoneError = null
    }

    fun consumeVerifyError() {
        verifyError = null
    }

    fun consumeProfileError() {
        profileError = null
    }

    private fun rememberSession(session: Session) {
        accessToken = session.accessToken
        sessionStore.rememberSession(session)
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository, sessionStore) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
