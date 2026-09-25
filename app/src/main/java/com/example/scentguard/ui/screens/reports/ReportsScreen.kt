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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.ChartData
import com.example.scentguard.data.model.ReportSummary
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.components.ScentGuardChart
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
import java.util.Locale
import kotlin.math.roundToInt

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
                                ReportContent(
                                    report = computedSummary, 
                                    chartState = chartState, 
                                    tempChartState = tempChartState, 
                                    liveData = liveData, 
                                    isWeekly = selectedTab == 1,
                                    scrollState = scrollState
                                )
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
    isWeekly: Boolean,
    scrollState: ScrollState = rememberScrollState()
) {
    val warnThresh = liveData?.thresholdWarn ?: 1000
    val dangerThresh = liveData?.thresholdDanger ?: 1500

    val gasPoints = (chartState as? Resource.Success)?.data?.points ?: emptyList()
    val peakGas = if (gasPoints.isNotEmpty()) gasPoints.maxOf { it.y }.roundToInt() else 0

    val tempPoints = (tempChartState as? Resource.Success)?.data?.points ?: emptyList()
    val peakTemp = if (tempPoints.isNotEmpty()) tempPoints.maxOf { it.y } else 0f

    val periodSubtitle = if (isWeekly) "Last 7 Days — Daily Aggregated Averages" else "Last 24 Hours — 15-Minute Telemetry Snapshots"
    val xAxisLabel = if (isWeekly) "Day of Week" else "Time of Day"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .responsiveContainer(maxWidth = 600.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        ScoreCard(score = report.airQualityScore, dangerThreshold = dangerThresh)
        
        Spacer(modifier = Modifier.height(28.dp))

        // Gas Concentration Context Section
        ChartContextCard(
            title = "Gas Concentration",
            timeframeText = periodSubtitle,
            description = "Tracks airborne gas concentration (MQ-135 sensor) in parts per million (PPM) in the waste storage station.",
            yAxisDescription = "Gas Concentration (ppm)",
            xAxisDescription = xAxisLabel,
            currentValText = "${liveData?.currentGasPpm ?: 0} ppm",
            avgValText = report.avgGasLevel,
            peakValText = "$peakGas ppm",
            howToReadPoints = listOf(
                "Higher points indicate higher measured gas concentration in the station.",
                "Peaks near or above $dangerThresh ppm indicate elevated gas buildup.",
                "Horizontal threshold bands mark system warning (≥ $warnThresh ppm) and danger (≥ $dangerThresh ppm) levels."
            ),
            whyItMatters = "Monitoring concentration changes helps station management verify ventilation activity and review conditions during peak waste accumulation hours.",
            thresholdLegend = {
                ThresholdLegendCard(thresholdWarn = warnThresh, thresholdDanger = dangerThresh)
            }
        ) {
            when (chartState) {
                is Resource.Loading -> Box(modifier = Modifier.fillMaxWidth().height(200.dp).shimmerEffect())
                is Resource.Success -> {
                    if (chartState.data?.points?.isEmpty() == true) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SsidChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Text("No sensor telemetry available for this period.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                                Text("Ensure ScentGuard station is online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            }
                        }
                    } else {
                        ScentGuardChart(
                            data = chartState.data!!,
                            minY = 0f,
                            maxY = 2000f,
                            unit = "ppm",
                            metricTitle = "Gas Concentration",
                            showThresholds = true,
                            thresholdWarn = warnThresh.toFloat(),
                            thresholdDanger = dangerThresh.toFloat(),
                            isWeekly = isWeekly
                        )
                    }
                }
                is Resource.Error -> Text("Failed to load gas analytics", color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Temperature Context Section
        ChartContextCard(
            title = "System Temperature",
            timeframeText = periodSubtitle,
            description = "Tracks station environment ambient temperature in degrees Celsius (°C).",
            yAxisDescription = "Temperature (°C)",
            xAxisDescription = xAxisLabel,
            currentValText = String.format(Locale.getDefault(), "%.1f °C", liveData?.temperature ?: 0f),
            avgValText = report.avgTemp,
            peakValText = String.format(Locale.getDefault(), "%.1f °C", peakTemp),
            howToReadPoints = listOf(
                "Higher points indicate higher measured ambient temperature at the station.",
                "The curve shows temperature variations across the selected period."
            ),
            whyItMatters = "Provides environmental context when reviewing gas concentration trends and equipment operating conditions.",
            thresholdLegend = null
        ) {
            when (tempChartState) {
                is Resource.Loading -> Box(modifier = Modifier.fillMaxWidth().height(200.dp).shimmerEffect())
                is Resource.Success -> {
                    if (tempChartState.data?.points?.isEmpty() == true) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Thermostat, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Text("No temperature telemetry available for this period.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        ScentGuardChart(
                            data = tempChartState.data!!,
                            minY = 0f,
                            maxY = 60f,
                            unit = "°C",
                            metricTitle = "System Temperature",
                            showThresholds = false,
                            isWeekly = isWeekly
                        )
                    }
                }
                is Resource.Error -> Text("Failed to load temperature analytics", color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        
        Text(
            text = "Operating Insights", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportMetricItem("Average Gas Concentration", report.avgGasLevel, Icons.Outlined.Cloud, PremiumGreen, "Arithmetic mean of recorded readings during period")
            ReportMetricItem("Average System Temperature", report.avgTemp, Icons.Outlined.Thermostat, Color(0xFF007AFF), "Arithmetic mean of recorded temperature readings")
            ReportMetricItem("Total Fan Operating Time", report.totalFanRuntime, Icons.Outlined.Timer, WarningOrange, "Calculated runtime based on telemetry snapshots with fan ON")
            ReportMetricItem("Danger Threshold Alerts", report.alertsCount.toString(), Icons.Outlined.Warning, ErrorRed, "Count of snapshots reaching or exceeding $dangerThresh ppm")
        }
        
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun ChartContextCard(
    title: String,
    timeframeText: String,
    description: String,
    yAxisDescription: String,
    xAxisDescription: String,
    currentValText: String,
    avgValText: String,
    peakValText: String,
    howToReadPoints: List<String>,
    whyItMatters: String,
    thresholdLegend: (@Composable () -> Unit)? = null,
    chartContent: @Composable () -> Unit
) {
    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 20.dp,
        cornerRadius = 24.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeframeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Canvas Container
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Y-axis: $yAxisDescription",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    chartContent()

                    Text(
                        text = "X-axis: $xAxisDescription",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }

            if (thresholdLegend != null) {
                Spacer(modifier = Modifier.height(14.dp))
                thresholdLegend()
            }

            // Summary Metric Strip
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text(currentValText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Average", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text(avgValText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    }
                    VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Peak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text(peakValText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // How to Read This
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "How to read this",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            howToReadPoints.forEach { point ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Why it Matters
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Why it matters",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = whyItMatters,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ThresholdLegendCard(thresholdWarn: Int, thresholdDanger: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "System Threshold Reference (Configured)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF34C759), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SAFE: < $thresholdWarn ppm", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9500), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WARN: ≥ $thresholdWarn ppm", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF3B30), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DANGER: ≥ $thresholdDanger ppm", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
            }
        }
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
fun ScoreCard(score: Int, dangerThreshold: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.size(80.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Text(text = "$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Percentage of recorded telemetry readings that remained below the configured Danger threshold ($dangerThreshold ppm) during the selected period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ReportMetricItem(label: String, value: String, icon: ImageVector, color: Color, note: String = "") {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (note.isNotEmpty()) {
                    Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}
