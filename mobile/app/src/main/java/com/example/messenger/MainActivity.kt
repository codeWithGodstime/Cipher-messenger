package com.example.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.messenger.data.api.ApiClient
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.ui.auth.AuthApp
import com.example.messenger.ui.home.MessengerHome
import com.example.messenger.ui.theme.MessengerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                val sessionStore = remember { SessionStore(applicationContext) }
                val tokenRefresher = remember { ApiClient.bind(sessionStore).tokenRefresher }
                var signedIn by remember { mutableStateOf(sessionStore.isLoggedIn) }
                var displayName by remember { mutableStateOf(sessionStore.displayName) }
                var usernameLabel by remember { mutableStateOf(sessionStore.usernameLabel) }
                var authGeneration by remember { mutableIntStateOf(0) }
                var openLogin by remember { mutableStateOf(false) }

                fun leaveSession() {
                    if (!signedIn) return
                    displayName = ""
                    usernameLabel = ""
                    signedIn = false
                    openLogin = true
                    authGeneration++
                }

                LaunchedEffect(tokenRefresher) {
                    tokenRefresher.start(this)
                    tokenRefresher.sessionLost.collect { leaveSession() }
                }

                if (signedIn) {
                    val userId = sessionStore.userId.orEmpty().ifBlank { usernameLabel }
                    key(userId) {
                        val accountStore = remember {
                            object : ViewModelStoreOwner {
                                override val viewModelStore = ViewModelStore()
                            }
                        }
                        DisposableEffect(accountStore) {
                            onDispose { accountStore.viewModelStore.clear() }
                        }
                        CompositionLocalProvider(LocalViewModelStoreOwner provides accountStore) {
                            MessengerHome(
                                sessionStore = sessionStore,
                                userId = userId,
                                displayName = displayName,
                                usernameLabel = usernameLabel,
                                onDisplayNameChanged = { name -> displayName = name },
                                onLoggedOut = { leaveSession() },
                            )
                        }
                    }
                } else {
                    key(authGeneration) {
                        AuthApp(
                            sessionStore = sessionStore,
                            sessionKey = authGeneration,
                            startOnLogin = openLogin,
                            onEntered = { name, username ->
                                sessionStore.markLoggedIn(name, username)
                                displayName = name
                                usernameLabel = username
                                openLogin = false
                                signedIn = true
                            },
                        )
                    }
                }
            }
        }
    }
}
