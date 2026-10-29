package com.example.messenger.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.messenger.data.api.ApiClient

private enum class AuthRoute {
    Welcome,
    Phone,
    Verify,
    Profile,
}

@Composable
fun AuthApp(modifier: Modifier = Modifier) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(ApiClient.repository))
    var routeName by rememberSaveable { mutableStateOf(AuthRoute.Welcome.name) }
    var countryName by rememberSaveable { mutableStateOf(Countries.first().name) }
    var digits by rememberSaveable { mutableStateOf("") }
    val route = AuthRoute.valueOf(routeName)
    val country = Countries.first { it.name == countryName }

    LaunchedEffect(viewModel) {
        viewModel.authEvents.collect { event ->
            when (event) {
                AuthEvent.OpenVerify -> routeName = AuthRoute.Verify.name
                AuthEvent.OpenProfile -> routeName = AuthRoute.Profile.name
            }
        }
    }

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
                onContinue = { viewModel.requestCode(country.e164(digits)) },
                submitting = viewModel.phoneLoading,
                statusMessage = viewModel.phoneError,
                onStatusShown = viewModel::consumePhoneError,
            )
            AuthRoute.Verify -> VerificationScreen(
                phoneLabel = country.displayNumber(digits),
                code = viewModel.code,
                onCodeChange = viewModel::updateCode,
                phase = viewModel.verifyPhase,
                onSubmit = viewModel::submitCode,
                onResend = viewModel::resendCode,
                resendGeneration = viewModel.resendGeneration,
                statusMessage = viewModel.verifyError,
                onStatusShown = viewModel::consumeVerifyError,
                onBack = { routeName = AuthRoute.Phone.name },
                onEditPhone = { routeName = AuthRoute.Phone.name },
                onVerified = { routeName = AuthRoute.Profile.name },
            )
            AuthRoute.Profile -> ProfileSetupScreen(
                onBack = {
                    viewModel.prepareVerify()
                    routeName = if (viewModel.codeStepRequired) {
                        AuthRoute.Verify.name
                    } else {
                        AuthRoute.Phone.name
                    }
                },
                saving = viewModel.profileSaving,
                saved = viewModel.profileSaved,
                statusMessage = viewModel.profileError,
                onStatusShown = viewModel::consumeProfileError,
                onDone = viewModel::saveName,
            )
        }
    }
}
