package com.example.scentguard.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.scentguard.R
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardBackground
import com.example.scentguard.utils.Resource
import com.example.scentguard.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    var startAnimation by remember { mutableStateOf(false) }
    var isTimerFinished by remember { mutableStateOf(false) }
    
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "alpha"
    )
    
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 1200),
        label = "scale"
    )

    val userProfile by mainViewModel.userProfile.collectAsState()
    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState()
    val isAuthenticated by mainViewModel.isUserAuthenticated.collectAsState()

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500)
        isTimerFinished = true
        
        // Safety Rescue: If we are still on this screen after 6 seconds, force navigate to login
        delay(3500)
        if (navController.currentBackStackEntry?.destination?.route == Screen.Splash.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    LaunchedEffect(isTimerFinished, userProfile, onboardingCompleted, isAuthenticated) {
        if (isTimerFinished) {
            if (!isAuthenticated) {
                // User is definitely not logged in - go to login
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            } else if (onboardingCompleted != null) {
                // User is authenticated, wait for profile or error
                when (userProfile) {
                    is Resource.Success -> {
                        val destination = if (onboardingCompleted == true) Screen.Dashboard.route else Screen.Onboarding.route
                        navController.navigate(destination) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                    is Resource.Error -> {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                    else -> {
                        // Keep waiting or let the 6s safety rescue take over
                    }
                }
            }
        }
    }

    ScentGuardBackground(showBloom = true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_abstract_air_waves),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.3f),
                alignment = Alignment.TopCenter
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.scentguard_icon),
                    contentDescription = "ScentGuard Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scaleAnim)
                        .alpha(alphaAnim)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "ScentGuard",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-1.5).sp,
                    modifier = Modifier.alpha(alphaAnim)
                )
                
                Text(
                    text = "Smart Air Management",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.alpha(alphaAnim)
                )

                Spacer(modifier = Modifier.height(64.dp))
                
                // Show a subtle loading indicator if we are authenticated but waiting for data
                if (isTimerFinished && isAuthenticated && userProfile is Resource.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                }
            }
            
            Text(
                text = "Version 1.1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .alpha(alphaAnim * 0.5f)
            )
        }
    }
}
