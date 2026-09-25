package com.example.scentguard.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.data.model.MascotAvatars
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.*
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    val liveData by mainViewModel.liveRestaurantData.collectAsState()
    val recentActivity by mainViewModel.recentActivity.collectAsState()
    val signalStatus by mainViewModel.signalStatus.collectAsState()
    
    // Unify "Online" status with ViewModel's Signal Status
    val isOnline = signalStatus == "Active" || signalStatus == "Weak"

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val lazyListState = rememberLazyListState()
    val isNavVisible = lazyListState.isScrollingUp()

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = Screen.Dashboard.route,
        drawerState = drawerState,
        onNavigate = { route ->
            scope.launch { drawerState.close() }
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        onLogout = {
            scope.launch { drawerState.close() }
            mainViewModel.logout()
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Dashboard.route) { inclusive = true }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth > 600.dp
            
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "ScentGuard",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        DashboardHeader(user)
                    }
                    
                    if (isWideScreen) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Box(modifier = Modifier.weight(1.2f)) {
                                    AirQualityHero(
                                        gasLevel = if (isOnline) liveData?.currentGasPpm ?: 0 else 0,
                                        airStatus = liveData?.airStatus ?: "SAFE",
                                        onViewAnalytics = { navController.navigate(Screen.Reports.route) },
                                        onCriticalAction = { navController.navigate(Screen.CriticalAlert.route) }
                                    )
                                }
                                Box(modifier = Modifier.weight(0.8f)) {
                                    StatisticsSection(
                                        fanStatus = liveData?.fanStatus ?: "OFF",
                                        signalStatus = signalStatus
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            AirQualityHero(
                                gasLevel = if (isOnline) liveData?.currentGasPpm ?: 0 else 0,
                                airStatus = liveData?.airStatus ?: "SAFE",
                                onViewAnalytics = { navController.navigate(Screen.Reports.route) },
                                onCriticalAction = { navController.navigate(Screen.CriticalAlert.route) }
                            )
                        }
                        item {
                            StatisticsSection(
                                fanStatus = liveData?.fanStatus ?: "OFF",
                                signalStatus = signalStatus
                            )
                        }
                    }
                    
                    item {
                        MetricsGrid(
                            isWideScreen = isWideScreen,
                            gasLevel = if (isOnline) liveData?.currentGasPpm ?: 0 else 0,
                            temp = if (isOnline) liveData?.temperature ?: 0f else 0f,
                            pumpStatus = liveData?.pumpStatus ?: "OFF",
                            onPumpClick = { navController.navigate(Screen.SanitationPump.route) }
                        )
                    }

                    if (user?.role?.uppercase() == "MANAGER") {
                        item {
                            ScentGuardFanControl(
                                initialMode = when(liveData?.fanMode?.uppercase()) {
                                    "ON" -> com.example.scentguard.ui.components.FanMode.ON
                                    "OFF" -> com.example.scentguard.ui.components.FanMode.OFF
                                    else -> com.example.scentguard.ui.components.FanMode.AUTO
                                },
                                onModeChange = { mode -> 
                                    mainViewModel.updateFanMode(mode.name)
                                }
                            )
                        }
                    }
                    
                    item {
                        RecentActivitySection(
                            items = recentActivity,
                            onShowAll = { navController.navigate(Screen.History.route) }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(112.dp))
                    }
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = Screen.Dashboard.route,
                isVisible = isNavVisible,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun DashboardHeader(user: UserProfile?) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = user?.fullName ?: "Guest User",
                style = MaterialTheme.typography.headlineLarge, // Slightly larger
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = user?.role ?: "Staff",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = user?.restaurantName ?: "ScentGuard Station",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Surface(
            modifier = Modifier.size(72.dp), // Slightly larger
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                val mascot = MascotAvatars.getById(user?.avatarId)
                
                if (user?.avatarType == "mascot" && mascot != null) {
                    ScentGuardMascotAvatar(
                        mascot = mascot,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = user?.fullName?.take(1)?.uppercase() ?: "G",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun AirQualityHero(
    gasLevel: Int,
    airStatus: String,
    onViewAnalytics: () -> Unit,
    onCriticalAction: () -> Unit
) {
    val statusText = remember(gasLevel, airStatus) {
        if (gasLevel == 0) "Standby" else airStatus.uppercase()
    }

    val statusColor = remember(airStatus, gasLevel) {
        if (gasLevel == 0) return@remember Color.Gray
        when (airStatus.uppercase()) {
            "SAFE" -> Color(0xFF34C759)
            "WARN" -> Color(0xFFFF9500)
            "DANGER" -> Color(0xFFFF3B30)
            else -> Color(0xFF34C759)
        }
    }

    Surface(
        onClick = onViewAnalytics,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // Intentional rounding
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CURRENT AIR QUALITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(contentAlignment = Alignment.Center) {
                // Static State Indicator
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(statusColor.copy(alpha = 0.05f), CircleShape)
                )

                CircularProgressIndicator(
                    progress = { (gasLevel / 2000f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(180.dp),
                    color = statusColor,
                    strokeWidth = 8.dp,
                    trackColor = statusColor.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "$gasLevel ppm",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Surface(
                modifier = Modifier.clickable(enabled = airStatus.uppercase() == "DANGER") {
                    onCriticalAction()
                },
                color = statusColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (gasLevel == 0) Icons.Outlined.PowerSettingsNew else if (airStatus.uppercase() != "DANGER") Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                        null, 
                        tint = statusColor, 
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (gasLevel == 0) "Awaiting sensor data" else if (airStatus.uppercase() != "DANGER") "Optimized ventilation" else "Immediate action required",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsGrid(
    isWideScreen: Boolean,
    gasLevel: Int,
    temp: Float,
    pumpStatus: String = "OFF",
    onPumpClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "HARDWARE STATUS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = "Gas Level",
                value = gasLevel.toString(),
                unit = "ppm",
                icon = Icons.Outlined.Cloud,
                modifier = Modifier.weight(1.2f), // More weight to primary metric
                valueStyle = MaterialTheme.typography.headlineLarge
            )
            MetricCard(
                label = "System Temp",
                value = String.format(java.util.Locale.getDefault(), "%.1f", temp),
                unit = "°C",
                icon = Icons.Outlined.Thermostat,
                modifier = Modifier.weight(0.8f), // Less weight to secondary
                valueStyle = MaterialTheme.typography.headlineSmall,
                iconSize = 16.dp
            )
            if (isWideScreen) {
                MetricCard(
                    label = "Pump",
                    value = if (pumpStatus == "ON") "Spraying" else "Standby",
                    unit = "",
                    icon = Icons.Outlined.Opacity,
                    modifier = Modifier.weight(1f).clickable { onPumpClick() },
                    valueColor = if (pumpStatus == "ON") Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant,
                    valueStyle = MaterialTheme.typography.headlineSmall
                )
            }
        }
        
        if (!isWideScreen) {
            MetricCard(
                label = "Sanitation Pump",
                value = if (pumpStatus == "ON") "Spraying" else "Standby",
                unit = "",
                icon = Icons.Outlined.Opacity,
                modifier = Modifier.fillMaxWidth().clickable { onPumpClick() },
                valueStyle = MaterialTheme.typography.headlineMedium,
                valueColor = if (pumpStatus == "ON") Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricCard(
    label: String, 
    value: String, 
    unit: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    ScentGuardCard(
        modifier = modifier,
        cornerRadius = 16.dp, // Consistent rounding
        contentPadding = 16.dp
    ) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(iconSize))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label.uppercase(), 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value, 
                    style = valueStyle, 
                    fontWeight = FontWeight.Black, 
                    color = valueColor,
                    letterSpacing = (-0.5).sp
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit, 
                        style = MaterialTheme.typography.labelLarge, 
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp), 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StatisticsSection(fanStatus: String, signalStatus: String) {
    Column {
        Text(
            "System performance",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard(
                label = "Fan Status", 
                value = fanStatus.uppercase(), 
                icon = Icons.Outlined.Air, 
                Modifier.fillMaxWidth(),
                statusColor = if (fanStatus.uppercase() == "ON") Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            MiniStatCard(
                label = "Signal Status", 
                value = signalStatus, 
                icon = Icons.Outlined.Sensors, 
                Modifier.fillMaxWidth(),
                statusColor = when(signalStatus) {
                    "Active" -> Color(0xFF34C759)
                    "Weak" -> Color(0xFFFF9500)
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
fun MiniStatCard(
    label: String, 
    value: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier,
    statusColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = statusColor)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = statusColor)
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RecentActivitySection(
    items: List<com.example.scentguard.data.model.HistoryItem>,
    onShowAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onShowAll) {
                Text("Show all", fontWeight = FontWeight.Bold)
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No recent activity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                } else {
                    items.forEachIndexed { index, item ->
                        val icon = when (item.eventType) {
                            "FAN_ON" -> Icons.Outlined.AutoMode
                            "FAN_OFF" -> Icons.Outlined.Air
                            "AIR_DANGER" -> Icons.Outlined.WarningAmber
                            else -> Icons.Outlined.CheckCircleOutline
                        }
                        val time = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(item.timestamp.toDate())
                        ActivityItem(item.title, item.description, time, icon)
                        if (index < items.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityItem(title: String, subtitle: String, time: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
