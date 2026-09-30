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
    val isNavVisible = lazyListState.isScrollingUp()

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

                    // Compact Filter Toolbar
                    Row(
                        modifier = Modifier
                            .responsiveContainer(maxWidth = 480.dp)
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Dropdown
                        var categoryExpanded by remember { mutableStateOf(false) }
                        val categories = listOf("All", "Alerts", "Devices", "Fan", "Users", "System")
                        
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { categoryExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Category: $selectedCategory", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Icon(Icons.Outlined.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            viewModel.setCategory(cat)
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Date Range Dropdown
                        var dateExpanded by remember { mutableStateOf(false) }
                        val dateRanges = listOf("All", "Today", "Last 7 Days", "Last 30 Days")
                        
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { dateExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Time: $selectedDateRange", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Icon(Icons.Outlined.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = dateExpanded,
                                onDismissRequest = { dateExpanded = false }
                            ) {
                                dateRanges.forEach { range ->
                                    DropdownMenuItem(
                                        text = { Text(range) },
                                        onClick = {
                                            viewModel.setDateRange(range)
                                            dateExpanded = false
                                        }
                                    )
                                }
                            }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(6) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).shimmerEffect())
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.width(200.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
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

    val containerBg = if (isStaffResponse) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerBg, RoundedCornerShape(12.dp))
            .padding(horizontal = if (isStaffResponse) 8.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStaffResponse) {
                    val mascot = MascotAvatars.getById("robot")
                    if (mascot != null) {
                        ScentGuardMascotAvatar(
                            mascot = mascot,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        color = color.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (isStaffResponse) "Staff Response" else item.title
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayTitle, 
                            style = MaterialTheme.typography.bodyMedium, 
                            fontWeight = FontWeight.Bold,
                            color = if (isStaffResponse) color else MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(item.timestamp.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    
                    if (isStaffResponse) {
                        val parts = item.description.split(": ", limit = 2)
                        val descText = if (parts.size == 2) "${parts[0]} — ${parts[1]}" else item.description
                        Text(
                            text = descText, 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = item.description, 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                
                if (item.value != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                thickness = 1.dp
            )
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
