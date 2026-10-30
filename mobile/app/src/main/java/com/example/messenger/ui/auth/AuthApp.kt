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
import com.example.messenger.data.auth.SessionStore

private enum class AuthRoute {
    Welcome,
    Username,
    Profile,
}

@Composable
fun AuthApp(
    sessionStore: SessionStore,
    modifier: Modifier = Modifier,
    onEntered: (displayName: String, usernameLabel: String) -> Unit = { _, _ -> },
) {
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(ApiClient.repository, sessionStore),
    )
    var routeName by rememberSaveable { mutableStateOf(AuthRoute.Welcome.name) }
    var modeName by rememberSaveable { mutableStateOf(AuthMode.Signup.name) }
    var username by rememberSaveable { mutableStateOf("") }
    val route = AuthRoute.valueOf(routeName)
    val mode = AuthMode.valueOf(modeName)

    fun goBack() {
        when (route) {
            AuthRoute.Welcome -> Unit
            AuthRoute.Username -> routeName = AuthRoute.Welcome.name
            AuthRoute.Profile -> routeName = AuthRoute.Username.name
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.authEvents.collect { event ->
            when (event) {
                AuthEvent.OpenProfile -> routeName = AuthRoute.Profile.name
                AuthEvent.EnterHome -> {
                    val label = viewModel.usernameLabel()
                    val name = sessionStore.displayName.ifBlank { label }
                    onEntered(name, label)
                }
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
                onCreateAccount = {
                    modeName = AuthMode.Signup.name
                    routeName = AuthRoute.Username.name
                },
                onSignIn = {
                    modeName = AuthMode.Login.name
                    routeName = AuthRoute.Username.name
                },
            )
            AuthRoute.Username -> UsernameAuthScreen(
                mode = mode,
                username = username,
                onUsernameChange = { username = it },
                onBack = { goBack() },
                onContinue = { viewModel.submitUsername(username, mode) },
                submitting = viewModel.usernameLoading,
                statusMessage = viewModel.usernameError,
                onStatusShown = viewModel::consumeUsernameError,
            )
            AuthRoute.Profile -> ProfileSetupScreen(
                onBack = { goBack() },
                saving = viewModel.profileSaving,
                saved = viewModel.profileSaved,
                statusMessage = viewModel.profileError,
                onStatusShown = viewModel::consumeProfileError,
                onDone = viewModel::saveName,
                onEnter = { displayName ->
                    onEntered(displayName, viewModel.usernameLabel().ifBlank { username })
                },
            )
        }
    }
}
