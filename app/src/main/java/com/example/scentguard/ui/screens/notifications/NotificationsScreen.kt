package com.example.scentguard.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.NotificationItem
import com.example.scentguard.data.model.NotificationType
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.ui.theme.WarningOrange
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.utils.shimmerEffect
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.NotificationViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    viewModel: NotificationViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val notificationsState by viewModel.notificationsState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val lazyListState = rememberLazyListState()
    val isNavVisible = lazyListState.isScrollingUp() || lazyListState.isAtBottom()

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = Screen.Notifications.route,
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
                                "Alerts",
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
                            IconButton(onClick = { viewModel.fetchNotifications() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (val state = notificationsState) {
                        is Resource.Loading -> {
                            NotificationSkeletonList()
                        }
                        is Resource.Success -> {
                            NotificationList(state.data ?: emptyList(), lazyListState)
                        }
                        is Resource.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.message ?: "Unknown Error", color = MaterialTheme.colorScheme.error)
                                    Button(onClick = { viewModel.fetchNotifications() }, modifier = Modifier.padding(top = 16.dp)) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = Screen.Notifications.route,
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
fun NotificationList(items: List<NotificationItem>, lazyListState: LazyListState = rememberLazyListState()) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new alerts", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().responsiveContainer(maxWidth = 600.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                NotificationCard(item)
            }
            item {
                Spacer(modifier = Modifier.height(112.dp))
            }
        }
    }
}

@Composable
fun NotificationSkeletonList() {
    Column(
        modifier = Modifier.padding(24.dp).fillMaxSize().responsiveContainer(maxWidth = 600.dp), 
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(24.dp)).shimmerEffect())
        }
    }
}

@Composable
fun NotificationCard(item: NotificationItem) {
    val color = when (item.type) {
        NotificationType.ALERT -> MaterialTheme.colorScheme.error
        NotificationType.WARNING -> WarningOrange
        NotificationType.SYSTEM -> MaterialTheme.colorScheme.primary
    }

    val icon = when (item.type) {
        NotificationType.ALERT -> Icons.Outlined.ErrorOutline
        NotificationType.WARNING -> Icons.Outlined.Warning
        NotificationType.SYSTEM -> Icons.Outlined.SettingsSuggest
    }

    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp // Row handles padding
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = color.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        color = if (item.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    if (!item.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
                Text(
                    text = item.message, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.isRead) 0.5f else 1f)
                )
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(item.timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
