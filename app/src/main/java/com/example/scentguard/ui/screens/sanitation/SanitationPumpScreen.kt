package com.example.scentguard.ui.screens.sanitation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.HistoryItem
import com.example.scentguard.data.model.Restaurant
import com.example.scentguard.ui.components.ScentGuardButton
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.utils.Resource
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanitationPumpScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    viewModel: SanitationViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    val liveData by mainViewModel.liveRestaurantData.collectAsState()
    val signalStatus by mainViewModel.signalStatus.collectAsState()
    val history by viewModel.sanitationHistory.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    val isOnline = signalStatus == "Active" || signalStatus == "Weak"
    val isManager = user?.role?.uppercase() == "MANAGER"

    var showTriggerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(liveData?.id) {
        liveData?.id?.let { rid ->
            viewModel.fetchSanitationHistory(rid)
        }
    }

    if (showTriggerDialog) {
        AlertDialog(
            onDismissRequest = { showTriggerDialog = false },
            title = { Text("Start Sanitation?", fontWeight = FontWeight.Bold) },
            text = { Text("Ensure the area is clear and the sanitation system is ready before proceeding.") },
            confirmButton = {
                TextButton(onClick = {
                    liveData?.id?.let { viewModel.triggerSanitation(it) }
                    showTriggerDialog = false
                }) {
                    Text("Start Now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTriggerDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sanitation Pump", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                PumpHeroCard(
                    status = liveData?.pumpStatus ?: "OFF",
                    mode = liveData?.pumpMode ?: "AUTO",
                    isOnline = isOnline
                )
            }

            if (!isOnline) {
                item {
                    OfflineWarningCard()
                }
            }

            item {
                PumpControlSection(
                    isManager = isManager,
                    isOnline = isOnline,
                    currentMode = liveData?.pumpMode ?: "AUTO",
                    isPumpOn = liveData?.pumpStatus == "ON",
                    onModeChange = { mode -> 
                        liveData?.id?.let { viewModel.updatePumpMode(it, mode) }
                    },
                    onTrigger = { showTriggerDialog = true }
                )
            }

            item {
                SanitationMetricsSection(liveData)
            }

            item {
                HistorySection(history)
            }

            item {
                SafetyNoticeSection()
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun PumpHeroCard(status: String, mode: String, isOnline: Boolean) {
    val isActive = status == "ON" && isOnline
    val statusColor = if (isActive) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val flowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flowScale"
    )

    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isActive) Color(0xFF34C759).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        borderColor = if (isActive) Color(0xFF34C759).copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(flowScale)
                            .background(Color(0xFF34C759).copy(alpha = 0.1f), CircleShape)
                    )
                }
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = if (isActive) Color(0xFF34C759).copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Opacity,
                            null,
                            tint = if (isActive) Color(0xFF34C759) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Text(
                "Sanitation Pump",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (!isOnline) "Offline" else if (isActive) "Active" else "Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = if (isActive) "Automatic sanitation cycle running" else "No sanitation cycle currently running",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun OfflineWarningCard() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Hardware Offline", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Pump controls are disabled until connection is restored.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun PumpControlSection(
    isManager: Boolean,
    isOnline: Boolean,
    currentMode: String,
    isPumpOn: Boolean,
    onModeChange: (String) -> Unit,
    onTrigger: () -> Unit
) {
    Column {
        Text("Operation Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        
        ScentGuardCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeCard("AUTO", currentMode == "AUTO", isManager && isOnline, Modifier.weight(1f)) { onModeChange("AUTO") }
                    ModeCard("OFF", currentMode == "OFF", isManager && isOnline, Modifier.weight(1f)) { onModeChange("OFF") }
                }
                
                Spacer(Modifier.height(20.dp))
                
                Button(
                    onClick = onTrigger,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = isManager && isOnline && !isPumpOn,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPumpOn) "Cycle in Progress" else "Start Manual Sanitation", fontWeight = FontWeight.Bold)
                }
                
                if (!isManager) {
                    Text(
                        "Only managers can control the sanitation pump.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun ModeCard(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SanitationMetricsSection(restaurant: Restaurant?) {
    Column {
        Text("Runtime Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoMetricCard("Last Cycle", restaurant?.lastSanitationTime?.let { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it.toDate()) } ?: "Never", Modifier.weight(1f))
            val durationSeconds = restaurant?.sanitationDurationSeconds ?: 50
            InfoMetricCard("Duration", "${durationSeconds}s", Modifier.weight(1f))
        }
    }
}

@Composable
fun InfoMetricCard(label: String, value: String, modifier: Modifier) {
    ScentGuardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun HistorySection(history: List<HistoryItem>) {
    Column {
        Text("Recent Sanitation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        
        ScentGuardCard(modifier = Modifier.fillMaxWidth()) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                        Text("No sanitation cycles yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(top = 12.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    history.forEachIndexed { index, item ->
                        HistoryRow(item)
                        if (index < history.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRow(item: HistoryItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(item.timestamp.toDate()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Text("50s", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SafetyNoticeSection() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Safety Notice", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "The sanitation pump should only operate when the sanitation system is ready. Check the pump, water supply, and surrounding area before manual activation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
