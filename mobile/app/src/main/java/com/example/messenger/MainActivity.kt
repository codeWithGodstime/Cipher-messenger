package com.example.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
                var signedIn by remember { mutableStateOf(sessionStore.isLoggedIn) }
                var displayName by remember { mutableStateOf(sessionStore.displayName) }
                var phoneLabel by remember { mutableStateOf(sessionStore.phoneLabel) }
                if (signedIn) {
                    MessengerHome(
                        displayName = displayName,
                        phoneLabel = phoneLabel,
                    )
                } else {
                    AuthApp(
                        sessionStore = sessionStore,
                        onEntered = { name, phone ->
                            sessionStore.markLoggedIn(name, phone)
                            displayName = name
                            phoneLabel = phone
                            signedIn = true
                        },
                    )
                }
            }
        }
    }
}
