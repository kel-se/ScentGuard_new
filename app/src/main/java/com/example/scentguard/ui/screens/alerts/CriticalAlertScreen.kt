package com.example.scentguard.ui.screens.alerts

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.Incident
import com.example.scentguard.data.model.Restaurant
import com.example.scentguard.data.model.StaffAction
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.theme.ErrorRed
import com.example.scentguard.ui.theme.PremiumGreen
import com.example.scentguard.ui.theme.WarningOrange
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.viewmodel.ActionViewModel
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriticalAlertScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    actionViewModel: ActionViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val activeIncidentResource by actionViewModel.activeIncident.collectAsState()
    val liveData by actionViewModel.liveRestaurantData.collectAsState()
    val signalStatus by mainViewModel.signalStatus.collectAsState()
    val sendState by actionViewModel.sendState.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sendState) {
        if (sendState is Resource.Error) {
            snackbarHostState.showSnackbar(sendState.message ?: "Failed to post update.")
            actionViewModel.resetSendState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Critical Alert Feed", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = activeIncidentResource) {
                is Resource.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is Resource.Success -> {
                    val incident = state.data
                    val restaurant = liveData
                    
                    if (user != null && restaurant != null) {
                        val canRespond = restaurant.airStatus == "DANGER" && incident?.status != "CLEARED"
                        
                        CriticalAlertContent(
                            restaurant = restaurant,
                            signalStatus = signalStatus,
                            activeIncident = incident,
                            onSendResponse = { msg -> actionViewModel.sendResponse(user, incident, msg) },
                            isSending = sendState is Resource.Loading,
                            canRespond = canRespond
                        )
                    }
                }
                is Resource.Error -> {
                    Text(
                        text = state.message ?: "Error loading feed",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun CriticalAlertContent(
    restaurant: Restaurant,
    signalStatus: String,
    activeIncident: Incident?,
    onSendResponse: (String) -> Unit,
    isSending: Boolean,
    canRespond: Boolean
) {
    val scrollState = rememberLazyListState()
    val isStale = signalStatus == "Offline"
    
    val hasResponded = activeIncident?.actions?.any { it.isResponse } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .responsiveContainer(maxWidth = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Offline Banner
        if (isStale) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WifiOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SENSOR OFFLINE - SHOWING LAST DATA", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            item {
                DangerHeader(
                    isDanger = restaurant.airStatus == "DANGER",
                    hasResponded = hasResponded
                )
            }

            // Metrics
            item {
                LiveMetricsCard(restaurant)
            }

            // Recommended Action
            item {
                RecommendationCard(restaurant, hasResponded)
            }

            // Response Feed Title
            item {
                Text(
                    text = "Staff Response Feed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Messages
            if (activeIncident != null && activeIncident.actions.isNotEmpty()) {
                items(activeIncident.actions) { response ->
                    StaffResponseItem(response)
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No responses yet. Be the first to update.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Bottom Controls
        ResponseInputSection(onSendResponse, isSending, hasResponded, canRespond)
    }
}

@Composable
fun DangerHeader(isDanger: Boolean, hasResponded: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val color = when {
        !isDanger -> PremiumGreen
        hasResponded -> WarningOrange
        else -> ErrorRed
    }

    val icon = when {
        !isDanger -> Icons.Outlined.CheckCircle
        hasResponded -> Icons.Outlined.AssignmentTurnedIn
        else -> Icons.Outlined.ReportGmailerrorred
    }

    val title = when {
        !isDanger -> "ENVIRONMENT SAFE ✓"
        hasResponded -> "RESPONSE RECORDED ✓"
        else -> "CRITICAL ALERT"
    }

    val subtitle = when {
        !isDanger -> "Conditions have returned to safe levels"
        hasResponded -> "Environment Still DANGER — Monitoring Active"
        else -> "Immediate physical intervention required"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(80.dp).let { if (isDanger && !hasResponded) it.graphicsLayer { scaleX = scale; scaleY = scale } else it },
                shape = CircleShape,
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = color
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LiveMetricsCard(restaurant: Restaurant) {
    ScentGuardCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricItem(
                label = "Gas Level",
                value = "${restaurant.currentGasPpm}",
                unit = "ppm",
                isDanger = restaurant.currentGasPpm >= restaurant.thresholdDanger
            )
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, unit: String, isDanger: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = if (isDanger) ErrorRed else WarningOrange
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun RecommendationCard(restaurant: Restaurant, hasResponded: Boolean) {
    val isDanger = restaurant.airStatus == "DANGER"
    val isGasCritical = restaurant.currentGasPpm >= restaurant.thresholdDanger
    
    val recommendation = when {
        !isDanger -> "Environment has returned to safe parameters. No further action needed."
        hasResponded -> "ScentGuard continues monitoring independently."
        else -> if (isGasCritical) "Inspect and Remove Waste" else "Check Ventilation"
    }

    val color = when {
        !isDanger -> PremiumGreen
        hasResponded -> PremiumGreen
        else -> ErrorRed
    }

    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = color.copy(alpha = 0.05f),
        borderColor = color.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (!isDanger || hasResponded) Icons.Outlined.TrackChanges else Icons.Outlined.Info, 
                    contentDescription = null, 
                    tint = color, 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val statusText = when {
                    !isDanger -> "Status: SAFE"
                    hasResponded -> "Monitoring: ACTIVE"
                    else -> "Recommended Action"
                }
                Text(
                    text = statusText, 
                    fontWeight = FontWeight.Bold, 
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = recommendation,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun StaffResponseItem(response: StaffAction) {
    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(response.startTime.toDate())
    val message = if (response.message.isNotEmpty()) response.message else response.actionType

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = response.staffName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = response.staffName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ResponseInputSection(
    onSend: (String) -> Unit, 
    isSending: Boolean, 
    hasResponded: Boolean,
    canRespond: Boolean
) {
    var text by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Quick Buttons
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Done Removing Waste", "Checking Ventilation", "Area Inspected", "Cleaning...").forEach { chip ->
                    AssistChip(
                        onClick = { if (!isSending && canRespond) onSend(chip) },
                        label = { Text(chip) },
                        enabled = !isSending && canRespond,
                        shape = CircleShape
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Text Input
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { 
                    Text(
                        when {
                            !canRespond -> "Environment is safe"
                            hasResponded -> "Response already recorded"
                            else -> "Write a quick update..."
                        }
                    )
                },
                trailingIcon = {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { 
                                if (text.isNotBlank()) {
                                    onSend(text)
                                    text = ""
                                }
                            },
                            enabled = text.isNotBlank() && canRespond
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 2,
                enabled = !isSending && canRespond
            )
            
            Text(
                text = if (canRespond) "ScentGuard continues monitoring independently." else "No active alert requires a response.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        }
    }
}
