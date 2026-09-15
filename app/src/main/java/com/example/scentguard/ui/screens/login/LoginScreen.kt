package com.example.scentguard.ui.screens.login

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.*
import com.example.scentguard.R
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardBackground
import com.example.scentguard.ui.components.ScentGuardButton
import com.example.scentguard.ui.components.GoogleButton
import com.example.scentguard.ui.theme.AuthUIConfig
import com.example.scentguard.ui.theme.SoftMint
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
    
    val loginState by viewModel.loginState.collectAsState()
    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState()
    val userProfile by mainViewModel.userProfile.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.icon1))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

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

    LaunchedEffect(loginState, userProfile) {
        if (loginState is Resource.Error) {
            snackbarHostState.showSnackbar(
                message = loginState.message ?: "Login failed",
                duration = SnackbarDuration.Long
            )
            viewModel.resetState()
        }
        
        if (loginState is Resource.Success && userProfile is Resource.Error) {
            if (userProfile.message == "MISSING_PROFILE") {
                Log.d("LoginScreen", "No ScentGuard profile detected for authenticated user. Redirecting to Setup.")
                navController.navigate(Screen.SignUp.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            } else {
                snackbarHostState.showSnackbar(
                    message = "Auth Success, but: ${userProfile.message ?: "Failed to fetch profile"}",
                    duration = SnackbarDuration.Long,
                    actionLabel = "Retry"
                )
            }
        }
    }

    ScentGuardBackground(showBloom = false) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AuthUIConfig.BackgroundGradient)
                    .padding(padding)
            ) {
                val screenHeight = maxHeight
                val isSmallScreen = screenHeight < 700.dp
                
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- Brand Section (Logo + Tagline) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Smarter Air.\nSafer Spaces.",
                            style = if (isSmallScreen) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            lineHeight = if (isSmallScreen) 34.sp else 40.sp,
                            letterSpacing = (-1).sp
                        )

                        Text(
                            text = "DETECT. VENTILATE. PROTECT.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = AuthUIConfig.TaglineLetterSpacing,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isSmallScreen) 120.dp else 160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LottieAnimation(
                                composition = composition,
                                progress = { progress },
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                    
                    // --- Authentication Card ---
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.65f),
                        shape = RoundedCornerShape(topStart = AuthUIConfig.CardCornerRadius, topEnd = AuthUIConfig.CardCornerRadius),
                        color = AuthUIConfig.CardBackground,
                        shadowElevation = AuthUIConfig.CardElevation
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 32.dp, vertical = if (isSmallScreen) 24.dp else 32.dp)
                                .responsiveContainer(maxWidth = 420.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Welcome back",
                                    style = if (isSmallScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                )
                                
                                Text(
                                    text = "Access your restaurant dashboard",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = if (isSmallScreen) 16.dp else 24.dp)
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address") },
                                    modifier = Modifier.fillMaxWidth().height(AuthUIConfig.FieldHeight),
                                    shape = RoundedCornerShape(AuthUIConfig.FieldCornerRadius),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        autoCorrectEnabled = false
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                        unfocusedContainerColor = AuthUIConfig.FieldContainerColor,
                                        focusedContainerColor = AuthUIConfig.FieldContainerColor
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                                
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(imageVector = image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(AuthUIConfig.FieldHeight),
                                    shape = RoundedCornerShape(AuthUIConfig.FieldCornerRadius),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                        unfocusedContainerColor = AuthUIConfig.FieldContainerColor,
                                        focusedContainerColor = AuthUIConfig.FieldContainerColor
                                    )
                                )
                                
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    TextButton(
                                        onClick = { navController.navigate(Screen.ForgotPassword.route) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            "Forgot password?", 
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val isAnyLoading = loginState is Resource.Loading || (loginState is Resource.Success && userProfile is Resource.Loading)
                                
                                ScentGuardButton(
                                    text = "Sign in",
                                    onClick = { viewModel.login(email, password) },
                                    isLoading = isAnyLoading,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 20.dp))

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
                                                val result = credentialManager.getCredential(context, request)
                                                val credential = result.credential
                                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                                val idToken = googleIdTokenCredential.idToken
                                                viewModel.signInWithGoogle(idToken, hashedNonce)
                                            } catch (e: GetCredentialException) {
                                                Log.e("LoginScreen", "Credential failure: ${e.message}", e)
                                                scope.launch { 
                                                    snackbarHostState.showSnackbar("Google failure: ${e.type} - ${e.message}") 
                                                }
                                            } catch (e: Exception) {
                                                Log.e("LoginScreen", "Unexpected error: ${e.message}", e)
                                                scope.launch { 
                                                    snackbarHostState.showSnackbar("Error: ${e.localizedMessage}") 
                                                }
                                            }
                                        }
                                    },
                                    isLoading = loginState is Resource.Loading,
                                    enabled = !isAnyLoading,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 20.dp))

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
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "Sign up",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
