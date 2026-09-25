package com.example.scentguard.ui.screens.staff

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.Restaurant
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.ui.components.ScentGuardMascotAvatar
import com.example.scentguard.data.model.MascotAvatars
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.utils.shimmerEffect
import com.example.scentguard.viewmodel.MainViewModel
import com.example.scentguard.viewmodel.StaffFilterOption
import com.example.scentguard.viewmodel.StaffViewModel
import com.example.scentguard.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    staffViewModel: StaffViewModel = viewModel(factory = ViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val staffState by staffViewModel.filteredStaffList.collectAsState()
    val rawStaffState by staffViewModel.staffList.collectAsState()
    val selectedFilter by staffViewModel.selectedFilter.collectAsState()
    var showFilterMenu by remember { mutableStateOf(false) }

    val restaurantState by staffViewModel.restaurantInfo.collectAsState()
    val isRefreshing by staffViewModel.isRefreshingCode.collectAsState()
    val removalState by staffViewModel.removalState.collectAsState()
    val timeRemaining by staffViewModel.timeRemaining.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val lazyListState = rememberLazyListState()
    val isNavVisible = lazyListState.isScrollingUp() || lazyListState.isAtBottom()

    var userToRemove by remember { mutableStateOf<UserProfile?>(null) }
    var selectedDuration by remember { mutableLongStateOf(24L) }

    LaunchedEffect(user?.restaurantId, user?.role) {
        user?.restaurantId?.let { 
            staffViewModel.fetchStaff(it)
            staffViewModel.fetchRestaurantInfo(it, user.role)
        }
    }

    LaunchedEffect(removalState) {
        if (removalState is Resource.Success) {
            snackbarHostState.showSnackbar("Staff member removed successfully")
            staffViewModel.resetRemovalState()
        } else if (removalState is Resource.Error) {
            snackbarHostState.showSnackbar(removalState.message ?: "Failed to remove staff")
            staffViewModel.resetRemovalState()
        }
    }

    if (userToRemove != null) {
        AlertDialog(
            onDismissRequest = { userToRemove = null },
            title = { Text("Remove Staff Member", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${userToRemove?.fullName}? They will immediately lose access to this restaurant's data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        user?.restaurantId?.let { rid -> 
                            staffViewModel.removeStaff(userToRemove!!.uid, rid)
                        }
                        userToRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToRemove = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = "staff",
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
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            val titleText = if (!user?.restaurantName.isNullOrBlank()) "${user?.restaurantName}" else "Staff Management"
                            Text(
                                titleText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .responsiveContainer(maxWidth = 600.dp)
                ) {
                    
                    val restaurant = (restaurantState as? Resource.Success)?.data
                    if (restaurant != null && user?.role?.uppercase() == "MANAGER") {
                        InviteCodeCard(
                            restaurant = restaurant,
                            isRefreshing = isRefreshing,
                            timeRemaining = timeRemaining,
                            selectedDuration = selectedDuration,
                            onDurationChange = { selectedDuration = it },
                            onRefresh = { staffViewModel.refreshInviteCode(restaurant.id, selectedDuration, user?.role) }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val staffHeading = if (!user?.restaurantName.isNullOrBlank()) "${user?.restaurantName} Staff" else "Current Staff"
                        Text(
                            text = staffHeading,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Box {
                            Surface(
                                onClick = { showFilterMenu = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedFilter.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowDropDown,
                                        contentDescription = "Filter Menu",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                StaffFilterOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (option == selectedFilter) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                } else {
                                                    Spacer(modifier = Modifier.width(26.dp))
                                                }
                                                Text(
                                                    text = option.label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (option == selectedFilter) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (option == selectedFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        },
                                        onClick = {
                                            staffViewModel.setFilter(option)
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val state = staffState) {
                            is Resource.Loading -> {
                                StaffSkeletonList()
                            }
                            is Resource.Success -> {
                                val filteredStaffList = state.data ?: emptyList()
                                val totalStaffList = (rawStaffState as? Resource.Success)?.data ?: emptyList()

                                if (totalStaffList.isEmpty()) {
                                    EmptyStaffState(user?.restaurantName)
                                } else if (filteredStaffList.isEmpty() && selectedFilter == StaffFilterOption.RECENTLY_JOINED) {
                                    EmptyRecentlyJoinedState(onResetFilter = { staffViewModel.setFilter(StaffFilterOption.LAST_JOINED) })
                                } else {
                                    StaffList(
                                        staff = filteredStaffList,
                                        lazyListState = lazyListState,
                                        onRemove = { member -> userToRemove = member },
                                        currentUserRole = user?.role ?: ""
                                    )
                                }
                            }
                            is Resource.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = state.message ?: "Permission Denied",
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { 
                                        user?.restaurantId?.let { 
                                            staffViewModel.fetchStaff(it) 
                                            staffViewModel.fetchRestaurantInfo(it)
                                        } 
                                    }) {
                                        Text("Retry")
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
                currentRoute = "staff",
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
fun InviteCodeCard(
    restaurant: Restaurant,
    isRefreshing: Boolean,
    timeRemaining: String,
    selectedDuration: Long,
    onDurationChange: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    val isExpired = timeRemaining == "Expired"

    Surface(
        modifier = Modifier.padding(24.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (!restaurant.name.isNullOrBlank()) "${restaurant.name} Invite Code" else "Staff Invitation Code",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = restaurant.inviteCode,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(12.dp))
            
            Surface(
                color = (if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
                shape = CircleShape
            ) {
                Text(
                    text = if (isExpired) "Code Expired" else "Expires in: $timeRemaining",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(24.dp))

            Text("Set Expiration Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationChip(label = "1h", value = 1L, selected = selectedDuration == 1L) { onDurationChange(it) }
                DurationChip(label = "12h", value = 12L, selected = selectedDuration == 12L) { onDurationChange(it) }
                DurationChip(label = "24h", value = 24L, selected = selectedDuration == 24L) { onDurationChange(it) }
                DurationChip(label = "7d", value = 168L, selected = selectedDuration == 168L) { onDurationChange(it) }
            }

            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh Code", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DurationChip(label: String, value: Long, selected: Boolean, onClick: (Long) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onClick(value) },
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun StaffList(staff: List<UserProfile>, lazyListState: LazyListState, onRemove: (UserProfile) -> Unit, currentUserRole: String) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize().responsiveContainer(maxWidth = 600.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(staff) { member ->
            StaffCard(member, onRemove, currentUserRole)
        }
        item { Spacer(modifier = Modifier.height(112.dp)) }
    }
}

@Composable
fun StaffSkeletonList() {
    Column(
        modifier = Modifier.padding(24.dp).fillMaxSize().responsiveContainer(maxWidth = 600.dp), 
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(24.dp)).shimmerEffect())
        }
    }
}

@Composable
fun StaffCard(member: UserProfile, onRemove: (UserProfile) -> Unit, currentUserRole: String) {
    val joinDate = remember(member.createdAt) {
        if (member.createdAt != null) {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(member.createdAt.toDate())
        } else {
            "Unknown"
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val mascot = MascotAvatars.getById(member.avatarId)
                    if (member.avatarType == "mascot" && mascot != null) {
                        ScentGuardMascotAvatar(
                            mascot = mascot,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            member.fullName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(member.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                Text("Joined: $joinDate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (currentUserRole.uppercase() == "MANAGER") {
                IconButton(onClick = { onRemove(member) }) {
                    Icon(Icons.Outlined.PersonRemove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun EmptyStaffState(restaurantName: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.People, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Text(
                if (!restaurantName.isNullOrBlank()) "No staff at $restaurantName yet" else "No staff members yet", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold
            )
            Text("Share your invite code to add staff", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyRecentlyJoinedState(onResetFilter: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No staff joined in the last 30 days.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Switch back to see all team members.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onResetFilter) {
                Text(
                    text = "Show Last Joined",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
