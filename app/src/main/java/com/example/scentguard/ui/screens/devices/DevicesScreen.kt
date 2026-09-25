package com.example.scentguard.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardChart
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.components.ScentGuardFanControl
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.ReportViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    reportViewModel: ReportViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val chartState by reportViewModel.chartState.collectAsState()
    val signalStatus by mainViewModel.signalStatus.collectAsState()
    val liveData by mainViewModel.liveRestaurantData.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val scrollState = rememberScrollState()
    val isNavVisible = scrollState.isScrollingUp() || scrollState.isAtBottom()

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = "devices",
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
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "Device Details",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // ESP32 Device Status Card
                    DeviceStatusCard(signalStatus)

                    if (user?.role?.uppercase() == "MANAGER") {
                        Spacer(modifier = Modifier.height(24.dp))
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
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // The Graph (UI-Ready for ESP32 Data)
                    Text(
                        text = "Real-time Gas Monitoring",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            when (val state = chartState) {
                                is Resource.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                is Resource.Success -> ScentGuardChart(state.data!!)
                                is Resource.Error -> Text("Sensor offline", color = MaterialTheme.colorScheme.error)
                                else -> {}
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Device Info Section
                    DeviceInfoList(liveData)

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = "devices",
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
fun DeviceStatusCard(signalStatus: String) {
    val isOnline = signalStatus == "Active" || signalStatus == "Weak"
    val statusColor = when(signalStatus) {
        "Active" -> Color(0xFF34C759)
        "Weak" -> Color(0xFFFF9500)
        else -> MaterialTheme.colorScheme.error
    }

    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Router, 
                        contentDescription = null, 
                        tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), 
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "ScentGuard Base", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) "Hardware Live" else "Hardware Offline", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { /* Info */ }) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun DeviceInfoList(restaurant: com.example.scentguard.data.model.Restaurant?) {
    val lastSync = restaurant?.lastSeen?.toDate()?.let {
        java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(it)
    } ?: "Never"

    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 8.dp
    ) {
        Column {
            DeviceInfoItem("Connection Type", "Wi-Fi (2.4GHz)", Icons.Outlined.Wifi)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            DeviceInfoItem("Network Status", "Secured", Icons.Outlined.VerifiedUser)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            DeviceInfoItem("Firmware", "v1.2.0-stable", Icons.Outlined.Build)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            DeviceInfoItem("Last Sync", lastSync, Icons.Outlined.Update)
        }
    }
}

@Composable
fun DeviceInfoItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().padding(20.dp)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyMedium, 
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyMedium, 
            fontWeight = FontWeight.Black, 
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
