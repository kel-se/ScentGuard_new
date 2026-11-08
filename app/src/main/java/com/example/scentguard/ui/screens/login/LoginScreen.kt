package com.example.scentguard.ui.screens.login

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.R
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardBackground
import com.example.scentguard.ui.components.ScentGuardButton
import com.example.scentguard.ui.components.GoogleButton
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.viewmodel.LoginViewModel
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

import java.security.MessageDigest
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    viewModel: LoginViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val loginState by viewModel.loginState.collectAsState()
    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState()
    val userProfile by mainViewModel.userProfile.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)

    LaunchedEffect(Unit) {
        viewModel.resetState()
    }

    LaunchedEffect(loginState, onboardingCompleted, userProfile) {
        if (loginState is Resource.Success && onboardingCompleted != null && userProfile is Resource.Success) {
            val destination = if (onboardingCompleted == true) Screen.Dashboard.route else Screen.Onboarding.route
            navController.navigate(destination) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is Resource.Error) {
            val rawMsg = loginState.message ?: ""
            errorMessage = when {
                rawMsg.contains("network", ignoreCase = true) || rawMsg.contains("connection", ignoreCase = true) || rawMsg.contains("unknownhost", ignoreCase = true) ->
                    "Connection problem. Please check your internet connection and try again."
                rawMsg.contains("no user", ignoreCase = true) || rawMsg.contains("not found", ignoreCase = true) || rawMsg.contains("no account", ignoreCase = true) ->
                    "No account found. Please sign up first or try another sign-in method."
                else ->
                    "Unable to sign in. Please check your credentials and try again."
            }
            viewModel.resetState()
        }
    }

    LaunchedEffect(userProfile) {
        if (loginState is Resource.Success && userProfile is Resource.Error) {
            if (userProfile.message == "MISSING_PROFILE") {
                Log.d("LoginScreen", "No ScentGuard profile detected for authenticated user. Redirecting to Setup.")
                navController.navigate(Screen.SignUp.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            } else {
                errorMessage = "Authentication succeeded, but failed to load profile. Please try again."
            }
        }
    }

    ScentGuardBackground {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .responsiveContainer(maxWidth = 420.dp)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start 
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Image(
                        painter = painterResource(id = R.drawable.ic_scentguard_logo_vector),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    Text(
                        text = "Welcome Back",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    
                    Text(
                        text = "Sign in to access your restaurant dashboard.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )

                    if (errorMessage != null) {
                        AuthErrorNotice(message = errorMessage!!)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            autoCorrectEnabled = false
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 24.dp), 
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(
                            onClick = { navController.navigate(Screen.ForgotPassword.route) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Forgot password?", 
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    val isAnyLoading = loginState is Resource.Loading || (loginState is Resource.Success && userProfile is Resource.Loading)
                    
                    ScentGuardButton(
                        text = "Sign In",
                        onClick = { viewModel.login(email, password) },
                        isLoading = isAnyLoading,
                        maxWidth = 420.dp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Text(
                            text = " or continue with ", 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    GoogleButton(
                        onClick = {
                            val rawNonce = UUID.randomUUID().toString()
                            val bytes = rawNonce.toByteArray()
                            val md = MessageDigest.getInstance("SHA-256")
                            val digest = md.digest(bytes)
                            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId("75241057260-05s50pjcl5a7sambng2qa999femjcr2i.apps.googleusercontent.com")
                                .setAutoSelectEnabled(false)
                                .setNonce(hashedNonce)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            scope.launch {
                                try {
                                    errorMessage = null
                                    val result = credentialManager.getCredential(context, request)
                                    val credential = result.credential
                                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                    val idToken = googleIdTokenCredential.idToken
                                    viewModel.signInWithGoogle(idToken, hashedNonce)
                                } catch (_: GetCredentialCancellationException) {
                                    Log.d("LoginScreen", "Google Sign-In canceled by user.")
                                } catch (e: GetCredentialException) {
                                    Log.e("LoginScreen", "Credential failure: ${e.message}", e)
                                    errorMessage = when {
                                        e.message?.contains("no credential", ignoreCase = true) == true ||
                                        e.message?.contains("not found", ignoreCase = true) == true -> 
                                            "No account found. Please sign up first or try another sign-in method."
                                        else -> 
                                            "Unable to sign in with Google. Please try again."
                                    }
                                } catch (e: java.net.UnknownHostException) {
                                    errorMessage = "Connection problem. Please check your internet connection and try again."
                                } catch (e: java.io.IOException) {
                                    errorMessage = "Connection problem. Please check your internet connection and try again."
                                } catch (e: Exception) {
                                    Log.e("LoginScreen", "Unexpected error: ${e.message}", e)
                                    errorMessage = "Unable to sign in with Google. Please try again."
                                }
                            }
                        },
                        isLoading = loginState is Resource.Loading,
                        enabled = !isAnyLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an account? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { navController.navigate(Screen.SignUp.route) },
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                text = "Sign up",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun AuthErrorNotice(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
