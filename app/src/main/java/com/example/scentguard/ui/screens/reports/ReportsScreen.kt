package com.example.scentguard.ui.screens.reports

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.ChartData
import com.example.scentguard.data.model.ReportSummary
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardChart
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.ui.theme.ErrorRed
import com.example.scentguard.ui.theme.PremiumGreen
import com.example.scentguard.ui.theme.WarningOrange
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.utils.shimmerEffect
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.ReportViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    viewModel: ReportViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val reportState by viewModel.reportState.collectAsState()
    val chartState by viewModel.chartState.collectAsState()
    val tempChartState by viewModel.tempChartState.collectAsState()
    val computedSummary by viewModel.computedSummary.collectAsState()
    val liveData by mainViewModel.liveRestaurantData.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val scrollState = rememberScrollState()
    val isNavVisible = scrollState.isScrollingUp() || scrollState.isAtBottom()

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = Screen.Reports.route,
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
                                "Analytics",
                                style = MaterialTheme.typography.headlineMedium,
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
                ) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            divider = {},
                            indicator = { tabPositions ->
                                if (selectedTab < tabPositions.size) {
                                    Box(
                                        Modifier
                                            .tabIndicatorOffset(tabPositions[selectedTab])
                                            .fillMaxHeight()
                                            .padding(4.dp)
                                            // Ensure the indicator is behind the text
                                            .zIndex(-1f)
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            .shadow(2.dp, CircleShape)
                                    )
                                }
                            }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { 
                                    selectedTab = 0 
                                    viewModel.fetchDailyReport()
                                },
                                text = { 
                                    Text(
                                        text = "Daily", 
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                                    ) 
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { 
                                    selectedTab = 1 
                                    viewModel.fetchWeeklyReport()
                                },
                                text = { 
                                    Text(
                                        text = "Weekly", 
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                                    ) 
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val state = reportState) {
                            is Resource.Loading -> {
                                ReportSkeleton()
                            }
                            is Resource.Success -> {
                                ReportContent(computedSummary, chartState, tempChartState, liveData, scrollState)
                            }
                            is Resource.Error -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = state.message ?: "Error", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = Screen.Reports.route,
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
fun ReportContent(
    report: ReportSummary, 
    chartState: Resource<ChartData>, 
    tempChartState: Resource<ChartData>,
    liveData: com.example.scentguard.data.model.Restaurant?,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .responsiveContainer(maxWidth = 600.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        ScoreCard(report.airQualityScore)
        
        Spacer(modifier = Modifier.height(32.dp))

        // Heads-Up Display: Current PPM and Status
        ScentGuardCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Real-time concentration", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${liveData?.currentGasPpm ?: 0} ppm",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = (-1).sp
                    )
                }
                
                val airStatus = liveData?.airStatus ?: "SAFE"
                val statusColor = when (airStatus.uppercase()) {
                    "SAFE" -> Color(0xFF34C759)
                    "WARN" -> Color(0xFFFF9500)
                    "DANGER" -> Color(0xFFFF3B30)
                    else -> Color(0xFF34C759)
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = airStatus.uppercase(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Odor Concentration Trend", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ScentGuardCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 16.dp
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                when (chartState) {
                    is Resource.Loading -> Box(modifier = Modifier.fillMaxWidth().height(220.dp).shimmerEffect())
                    is Resource.Success -> {
                        if (chartState.data?.points?.isEmpty() == true) {
                            Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.SsidChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    Text("Insufficient data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                                    Text("Ensure hardware is online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                }
                            }
                        } else {
                            ScentGuardChart(chartState.data!!)
                        }
                    }
                    is Resource.Error -> Text("Failed to load chart", color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "System Temperature Trend", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ScentGuardCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 16.dp
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                when (tempChartState) {
                    is Resource.Loading -> Box(modifier = Modifier.fillMaxWidth().height(220.dp).shimmerEffect())
                    is Resource.Success -> {
                        if (tempChartState.data?.points?.isEmpty() == true) {
                            Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Thermostat, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    Text("Insufficient data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            ScentGuardChart(tempChartState.data!!)
                        }
                    }
                    is Resource.Error -> Text("Failed to load temp chart", color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Explanation Text
        Text(
            text = "Monitoring shows changes in detected gas levels. SAFE indicates normal conditions, WARN indicates attention may be needed, and DANGER indicates immediate action. Optimized for ScentGuard MQ135 calibration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Insights summary", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportMetricItem("Average Gas", report.avgGasLevel, Icons.Outlined.Cloud, PremiumGreen)
            ReportMetricItem("Average Temp", report.avgTemp, Icons.Outlined.Thermostat, Color(0xFF007AFF))
            ReportMetricItem("Fan Activity", report.totalFanRuntime, Icons.Outlined.Timer, WarningOrange)
            ReportMetricItem("Critical Alerts", report.alertsCount.toString(), Icons.Outlined.Warning, ErrorRed)
        }
        
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun ReportSkeleton() {
    Column(
        modifier = Modifier.padding(24.dp).fillMaxSize().responsiveContainer(maxWidth = 600.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(32.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.width(180.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(32.dp)).shimmerEffect())
    }
}

@Composable
fun ScoreCard(score: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Performance Index", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = when {
                        score > 90 -> "Excellent"
                        score > 70 -> "Good"
                        else -> "Stabilizing"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.size(84.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(text = "$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ReportMetricItem(label: String, value: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = color.copy(alpha = 0.05f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}
