package com.example.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
                var signedIn by rememberSaveable { mutableStateOf(false) }
                var displayName by rememberSaveable { mutableStateOf("") }
                var phoneLabel by rememberSaveable { mutableStateOf("") }
                if (signedIn) {
                    MessengerHome(
                        displayName = displayName,
                        phoneLabel = phoneLabel,
                    )
                } else {
                    AuthApp(
                        onEntered = { name, phone ->
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
