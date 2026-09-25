package com.example.scentguard.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.navigation.Screen
import com.example.scentguard.ui.components.AvatarPickerSheet
import com.example.scentguard.ui.components.ScentGuardCard
import com.example.scentguard.ui.components.ScentGuardFloatingNav
import com.example.scentguard.ui.components.ScentGuardNavigationDrawer
import com.example.scentguard.ui.components.ScentGuardMascotAvatar
import com.example.scentguard.data.model.MascotAvatars
import com.example.scentguard.utils.Resource
import com.example.scentguard.utils.isAtBottom
import com.example.scentguard.utils.isScrollingUp
import com.example.scentguard.utils.responsiveContainer
import com.example.scentguard.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    val userProfileResource by mainViewModel.userProfile.collectAsState()
    val user = (userProfileResource as? Resource.Success)?.data
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showAvatarPicker by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val isNavVisible = scrollState.isScrollingUp() || scrollState.isAtBottom()

    if (showAvatarPicker) {
        AvatarPickerSheet(
            onDismiss = { showAvatarPicker = false },
            selectedAvatarId = user?.avatarId,
            onAvatarSelected = { avatarId ->
                mainViewModel.selectMascotAvatar(avatarId)
                val mascotName = MascotAvatars.getById(avatarId)?.name ?: "Guardian"
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "✓ Guardian changed to $mascotName",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    ScentGuardNavigationDrawer(
        user = user,
        currentRoute = Screen.Profile.route,
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
                            Text(
                                "Profile",
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
                        .responsiveContainer(maxWidth = 480.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    ProfileHeader(
                        user = user,
                        onEditAvatar = {
                            showAvatarPicker = true
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    SectionTitle("Account Details")
                    
                    val infoItems = mutableListOf<Any>()
                    infoItems.add(ProfileInfoItem("Full Name", user?.fullName ?: "N/A", Icons.Outlined.Person))
                    infoItems.add(ProfileInfoContent(user?.email ?: "N/A", Icons.Outlined.Email))
                    infoItems.add(ProfileInfoItem("Role", user?.role ?: "Staff", Icons.Outlined.Badge))
                    infoItems.add(ProfileInfoItem("Restaurant", user?.restaurantName ?: "N/A", Icons.Outlined.Restaurant))
                    
                    if (user?.role?.uppercase() == "MANAGER") {
                        val teamLabel = if (!user.restaurantName.isNullOrBlank()) "Check ${user.restaurantName} Team" else "Check Staff Section"
                        infoItems.add(ProfileInfoItem("Invite Code", teamLabel, Icons.Outlined.VpnKey, isClickable = true))
                    }
                    
                    ProfileInfoCard(items = infoItems)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    SectionTitle("Security & Alerts")
                    ProfileInfoCard(
                        items = listOf(
                            ProfileInfoItem("Security", "Password & Auth", Icons.Outlined.Security, isClickable = true),
                            ProfileInfoItem("Notifications", "Alert preferences", Icons.Outlined.NotificationsActive, isClickable = true)
                        )
                    )

                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = {
                            mainViewModel.logout()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = CircleShape,
                        elevation = null
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }

            ScentGuardFloatingNav(
                user = user,
                currentRoute = Screen.Profile.route,
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
fun ProfileHeader(
    user: UserProfile?,
    onEditAvatar: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            SmallFloatingActionButton(
                onClick = onEditAvatar,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(Icons.Outlined.Face, contentDescription = "Change Avatar", modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = user?.fullName ?: "Guest User",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = user?.role ?: "Staff Member",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
fun ProfileInfoCard(items: List<Any>) {
    ScentGuardCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 8.dp // Inner padding handled by rows
    ) {
        Column {
            items.forEachIndexed { index, item ->
                when (item) {
                    is ProfileInfoItem -> ProfileInfoRow(item.label, item.value, item.icon, item.isClickable)
                    is ProfileInfoContent -> ProfileInfoRow("Email", item.value, item.icon, false)
                }
                
                if (index < items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String, icon: ImageVector, isClickable: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isClickable) it.clickable { } else it }
            .padding(20.dp),
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
                style = MaterialTheme.typography.labelMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value, 
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (isClickable) {
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

data class ProfileInfoItem(val label: String, val value: String, val icon: ImageVector, val isClickable: Boolean = false)
data class ProfileInfoContent(val value: String, val icon: ImageVector)
