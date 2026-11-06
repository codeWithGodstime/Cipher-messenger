package com.example.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
                    tokenRefresher.sessionLost.collect { leaveSession() }
                }

                if (signedIn) {
                    MessengerHome(
                        sessionStore = sessionStore,
                        displayName = displayName,
                        usernameLabel = usernameLabel,
                        onLoggedOut = { leaveSession() },
                    )
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
