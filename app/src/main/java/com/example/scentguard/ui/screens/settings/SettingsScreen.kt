package com.example.scentguard.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.AlertSound
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.SettingsViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    viewModel: SettingsViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    val liveData by mainViewModel.liveRestaurantData.collectAsState()
    val signalStatus by mainViewModel.signalStatus.collectAsState()
    
    val isOnline = signalStatus == "Active" || signalStatus == "Weak"
    
    val themeMode by viewModel.themeMode.collectAsState()
    val gasAlerts by viewModel.gasAlertsEnabled.collectAsState()
    val fanAlerts by viewModel.fanAlertsEnabled.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var showThemeDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val isNavVisible = scrollState.isScrollingUp() || scrollState.isAtBottom()

    val lastSyncText = remember(liveData?.lastSeen) {
        val lastSeen = liveData?.lastSeen?.toDate()
        if (lastSeen == null) {
            "Not synced yet"
        } else {
            val now = System.currentTimeMillis()
            val diff = now - lastSeen.time
            if (diff < 60000) { // Less than a minute
                "Just now"
            } else {
                java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(lastSeen)
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ThemeOption("System Default", themeMode == "system") { viewModel.setThemeMode("system"); showThemeDialog = false }
                    ThemeOption("Light", themeMode == "light") { viewModel.setThemeMode("light"); showThemeDialog = false }
                    ThemeOption("Dark", themeMode == "dark") { viewModel.setThemeMode("dark"); showThemeDialog = false }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = Screen.Settings.route,
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
                                "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
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
                        .responsiveContainer(maxWidth = 480.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SectionTitle("Appearance")
                    SettingsCard {
                        ActionItem(
                            label = "App Theme",
                            description = themeMode.replaceFirstChar { it.uppercase() },
                            icon = Icons.Outlined.Palette,
                            onClick = { showThemeDialog = true }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    SectionTitle("Notifications")
                    SettingsCard {
                        ToggleItem(
                            label = "Air Quality Alerts",
                            description = "Notify when levels are high",
                            icon = Icons.Outlined.Warning,
                            checked = gasAlerts,
                            onCheckedChange = { viewModel.toggleGasAlerts(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        ToggleItem(
                            label = "Fan Activity",
                            description = "Notify when fan is active",
                            icon = Icons.Outlined.Air,
                            checked = fanAlerts,
                            onCheckedChange = { viewModel.toggleFanAlerts(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        AlertSoundSection(viewModel)
                    }

                    // Move Hardware Configuration to be more prominent
                    if (user != null) {
                        Spacer(modifier = Modifier.height(32.dp))
                        SectionTitle("Hardware Configuration")
                        ThresholdConfigCard(
                            restaurantId = user.restaurantId,
                            isManager = user.role.uppercase() == "MANAGER",
                            isOnline = isOnline,
                            liveData = liveData,
                            viewModel = viewModel
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    SectionTitle("System")
                    SettingsCard {
                        ActionItem(
                            label = "Calibration",
                            description = "Last sync: $lastSyncText",
                            icon = Icons.Outlined.SettingsSuggest,
                            onClick = { }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        ActionItem(
                            label = "Connect New Device",
                            description = "Set up Wi-Fi for your ESP32",
                            icon = Icons.Outlined.Bluetooth,
                            onClick = { navController.navigate("provisioning") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        ActionItem(
                            label = "About",
                            description = "ScentGuard v1.1.0",
                            icon = Icons.Outlined.Info,
                            onClick = { }
                        )
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = Screen.Settings.route,
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
fun AlertSoundSection(viewModel: SettingsViewModel) {
    val selectedSoundId by viewModel.selectedAlarmSoundId.collectAsState()
    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsState()
    val selectedSound = AlertSound.getById(selectedSoundId)
    
    var showSoundDialog by remember { mutableStateOf(false) }

    if (showSoundDialog) {
        AlertDialog(
            onDismissRequest = { showSoundDialog = false },
            title = { Text("Critical Alert Sound", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    AlertSound.ALL_SOUNDS.forEach { sound ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setAlarmSound(sound.id)
                                    showSoundDialog = false 
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sound.id == selectedSoundId, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = sound.displayName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSoundDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(32.dp)
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Critical Alert Sound", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(selectedSound.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            
            Row {
                IconButton(onClick = { viewModel.togglePreview(selectedSoundId) }) {
                    Icon(
                        imageVector = if (isPreviewPlaying) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircleOutline,
                        contentDescription = "Preview",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showSoundDialog = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Change Sound", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 8.dp, // Individual items handle padding
        content = content
    )
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = 16.dp, top = 8.dp),
        letterSpacing = (-0.5).sp
    )
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ThresholdConfigCard(
    restaurantId: String,
    isManager: Boolean,
    isOnline: Boolean,
    liveData: com.example.scentguard.data.model.Restaurant?,
    viewModel: SettingsViewModel
) {
    // Key by restaurantId to preserve state across heartbeats
    var warnVal by remember(restaurantId) { 
        mutableStateOf((liveData?.thresholdWarn ?: com.example.scentguard.data.model.Restaurant.DEFAULT_THRESHOLD_WARN).toString()) 
    }
    var dangerVal by remember(restaurantId) { 
        mutableStateOf((liveData?.thresholdDanger ?: com.example.scentguard.data.model.Restaurant.DEFAULT_THRESHOLD_DANGER).toString()) 
    }
    
    val updateState by viewModel.thresholdUpdateState.collectAsState()
    
    // Sync only when actual thresholds change in Firestore (e.g. successful save or external change)
    // and NOT when we are currently loading/submitting to avoid jumping text
    LaunchedEffect(liveData?.thresholdWarn, liveData?.thresholdDanger) {
        if (updateState !is Resource.Loading) {
            liveData?.thresholdWarn?.let { warnVal = it.toString() }
            liveData?.thresholdDanger?.let { dangerVal = it.toString() }
        }
    }
    
    SettingsCard {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gas Sensitivity (PPM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!isOnline) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hardware Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = warnVal,
                    onValueChange = { if (it.all { char -> char.isDigit() }) warnVal = it },
                    label = { Text("WARN") },
                    modifier = Modifier.weight(1f),
                    enabled = isManager && isOnline && updateState !is Resource.Loading,
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9500),
                        unfocusedBorderColor = Color(0xFFFF9500).copy(alpha = 0.2f)
                    )
                )
                OutlinedTextField(
                    value = dangerVal,
                    onValueChange = { if (it.all { char -> char.isDigit() }) dangerVal = it },
                    label = { Text("DANGER") },
                    modifier = Modifier.weight(1f),
                    enabled = isManager && isOnline && updateState !is Resource.Loading,
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF3B30),
                        unfocusedBorderColor = Color(0xFFFF3B30).copy(alpha = 0.2f)
                    )
                )
            }
            
            if (isManager) {
                Spacer(modifier = Modifier.height(24.dp))
                
                val isValid = (warnVal.toIntOrNull() ?: 0) < (dangerVal.toIntOrNull() ?: 0)
                
                Button(
                    onClick = { 
                        viewModel.updateThresholds(restaurantId, warnVal.toInt(), dangerVal.toInt()) 
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isValid && isOnline && updateState !is Resource.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (updateState is Resource.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (isOnline) "Apply Configuration" else "Hardware Offline", fontWeight = FontWeight.Bold)
                    }
                }
                
                if (!isOnline) {
                    Text(
                        "Configuration is unavailable while the hardware is offline.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                    )
                } else if (!isValid) {
                    Text(
                        "Warning: WARN threshold must be less than DANGER.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                if (updateState is Resource.Error) {
                    Text(
                        text = updateState.message ?: "Update failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                if (updateState is Resource.Success) {
                    LaunchedEffect(Unit) {
                        delay(3000)
                        viewModel.resetUpdateState()
                    }
                    Text(
                        "Thresholds updated successfully!",
                        color = Color(0xFF34C759),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                Text(
                    "Note: Only Managers can modify these thresholds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun ToggleItem(
    label: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label, 
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ActionItem(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}
