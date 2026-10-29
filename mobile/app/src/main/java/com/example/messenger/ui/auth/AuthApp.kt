package com.example.messenger.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class AuthRoute {
    Welcome,
    Phone,
    Verify,
    Profile,
}

@Composable
fun AuthApp(modifier: Modifier = Modifier) {
    var routeName by rememberSaveable { mutableStateOf(AuthRoute.Welcome.name) }
    var countryName by rememberSaveable { mutableStateOf(Countries.first().name) }
    var digits by rememberSaveable { mutableStateOf("") }
    val route = AuthRoute.valueOf(routeName)
    val country = Countries.first { it.name == countryName }

    AnimatedContent(
        targetState = route,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            if (forward) {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it / 4 } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith
                    slideOutHorizontally { it / 4 } + fadeOut()
            }
        },
        label = "auth",
    ) { current ->
        when (current) {
            AuthRoute.Welcome -> WelcomeScreen(
                onGetStarted = { routeName = AuthRoute.Phone.name },
            )
            AuthRoute.Phone -> PhoneRegistrationScreen(
                country = country,
                digits = digits,
                onCountryChange = { next ->
                    countryName = next.name
                    if (digits.length > next.digitCount) {
                        digits = digits.take(next.digitCount)
                    }
                },
                onDigitsChange = { digits = it },
                onBack = { routeName = AuthRoute.Welcome.name },
                onContinue = { routeName = AuthRoute.Verify.name },
            )
            AuthRoute.Verify -> VerificationScreen(
                phoneLabel = country.displayNumber(digits),
                onBack = { routeName = AuthRoute.Phone.name },
                onEditPhone = { routeName = AuthRoute.Phone.name },
                onVerified = { routeName = AuthRoute.Profile.name },
            )
            AuthRoute.Profile -> ProfileSetupScreen(
                onBack = { routeName = AuthRoute.Verify.name },
            )
        }
    }
}
