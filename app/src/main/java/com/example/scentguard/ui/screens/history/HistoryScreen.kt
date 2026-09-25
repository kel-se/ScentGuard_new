package com.example.scentguard.ui.screens.history

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.HistoryItem
import com.example.scentguard.data.model.HistoryType
import com.example.scentguard.data.model.MascotAvatars
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardMascotAvatar
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.utils.shimmerEffect
import com.example.scentguard.viewmodel.HistoryViewModel
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    viewModel: HistoryViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val historyState by viewModel.historyState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedDateRange by viewModel.selectedDateRange.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val lazyListState = rememberLazyListState()
    val isNavVisible = lazyListState.isScrollingUp() || lazyListState.isAtBottom()

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = Screen.History.route,
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
                                "System logs",
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
                            if (user?.role?.uppercase() == "MANAGER") {
                                var showDeleteAllDialog by remember { mutableStateOf(false) }
                                
                                if (showDeleteAllDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteAllDialog = false },
                                        title = { Text("Delete All Logs?") },
                                        text = { Text("This will permanently remove all system logs for this restaurant. This action cannot be undone.") },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    viewModel.deleteAllLogs()
                                                    showDeleteAllDialog = false
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Delete All", fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteAllDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }
                                
                                IconButton(onClick = { showDeleteAllDialog = true }) {
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            IconButton(onClick = { viewModel.fetchHistory() }) {
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
                var searchQuery by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .responsiveContainer(maxWidth = 480.dp)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text("Search logs...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    )

                    // Category Filters
                    ScrollableTabRow(
                        selectedTabIndex = listOf("All", "Alerts", "Devices", "Fan", "Users", "System").indexOf(selectedCategory),
                        containerColor = Color.Transparent,
                        edgePadding = 24.dp,
                        divider = {},
                        indicator = {}
                    ) {
                        listOf("All", "Alerts", "Devices", "Fan", "Users", "System").forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { viewModel.setCategory(category) },
                                label = { Text(category) },
                                modifier = Modifier.padding(end = 8.dp),
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // Date Filters
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Today", "Last 7 Days", "Last 30 Days").forEach { range ->
                            FilterChip(
                                selected = selectedDateRange == range,
                                onClick = { viewModel.setDateRange(range) },
                                label = { Text(range, fontSize = 12.sp) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val state = historyState) {
                            is Resource.Loading -> {
                                HistorySkeletonList()
                            }
                            is Resource.Success -> {
                                val filteredItems = state.data?.filter { 
                                    it.title.contains(searchQuery, ignoreCase = true) || 
                                    it.description.contains(searchQuery, ignoreCase = true) 
                                } ?: emptyList()
                                HistoryList(
                                    items = filteredItems, 
                                    isLoadingMore = isLoadingMore,
                                    isManager = user?.role?.uppercase() == "MANAGER",
                                    lazyListState = lazyListState,
                                    onLoadMore = { viewModel.loadNextPage() },
                                    onDelete = { viewModel.deleteLog(it) }
                                )
                            }
                            is Resource.Error -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.message ?: "Unknown Error", color = MaterialTheme.colorScheme.error)
                                        Button(onClick = { viewModel.fetchHistory() }, modifier = Modifier.padding(top = 16.dp)) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = Screen.History.route,
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
fun HistoryList(
    items: List<HistoryItem>,
    isLoadingMore: Boolean,
    isManager: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    onLoadMore: () -> Unit,
    onDelete: (HistoryItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.History, 
                    null, 
                    modifier = Modifier.size(64.dp), 
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No logs found", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Your system events will appear here once monitoring begins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        }
    } else {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().responsiveContainer(maxWidth = 600.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id }) { item ->
                SwipeToDeleteContainer(
                    item = item,
                    onDelete = onDelete,
                    enabled = isManager
                ) {
                    HistoryCard(item)
                }
            }
            
            item {
                if (isLoadingMore) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Load More", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun HistorySkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().responsiveContainer(maxWidth = 600.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        items(5) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.05f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).shimmerEffect())
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.width(120.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.width(200.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItem) {
    val isStaffResponse = item.eventType == "STAFF_UPDATE"
    
    val color = when {
        isStaffResponse -> MaterialTheme.colorScheme.primary
        item.type == HistoryType.INFO -> MaterialTheme.colorScheme.primary
        item.type == HistoryType.WARNING -> Color(0xFFFF9500)
        item.type == HistoryType.ALERT -> MaterialTheme.colorScheme.error
        item.type == HistoryType.SUCCESS -> Color(0xFF34C759)
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when {
        isStaffResponse -> Icons.Outlined.Person
        item.type == HistoryType.INFO -> Icons.Outlined.Info
        item.type == HistoryType.WARNING -> Icons.Outlined.Warning
        item.type == HistoryType.ALERT -> Icons.Outlined.ErrorOutline
        item.type == HistoryType.SUCCESS -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Info
    }

    val backgroundColor = if (isStaffResponse) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        shadowElevation = if (isStaffResponse) 0.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            if (isStaffResponse) 1.dp else 0.5.dp, 
            if (isStaffResponse) color.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isStaffResponse) {
                val mascot = MascotAvatars.getById("robot")
                if (mascot != null) {
                    ScentGuardMascotAvatar(
                        mascot = mascot,
                        modifier = Modifier.size(44.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = color.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val displayTitle = if (isStaffResponse) "Staff Response" else item.title
                
                Text(
                    text = displayTitle, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = if (isStaffResponse) color else MaterialTheme.colorScheme.onSurface
                )
                
                if (isStaffResponse) {
                    val parts = item.description.split(": ", limit = 2)
                    if (parts.size == 2) {
                        Text(
                            text = parts[0], 
                            style = MaterialTheme.typography.bodyMedium, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = parts[1], 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = item.description, 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = item.description, 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(item.timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (item.value != null) {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = color
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    item: HistoryItem,
    onDelete: (HistoryItem) -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            content()
        }
    )
}
