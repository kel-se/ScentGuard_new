package com.example.scentguard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scentguard.R
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.data.model.MascotAvatars
import com.example.scentguard.navigation.Screen

@Composable
fun ScentGuardNavigationDrawer(
    user: UserProfile?,
    currentRoute: String?,
    drawerState: DrawerState,
    onNavigate: (String) -> Unit, // Changed from Screen to String for flexibility
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.width(320.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                
                // App Logo & Brand
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.scentguard_icon),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "ScentGuard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(Modifier.height(32.dp))

                // User Profile Section
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = user?.fullName ?: "Guest User",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = user?.role ?: "STAFF",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Navigation Items
                DrawerItem(
                    label = "Dashboard",
                    icon = Icons.Outlined.Dashboard,
                    selected = currentRoute == Screen.Dashboard.route,
                    onClick = { onNavigate(Screen.Dashboard.route) }
                )
                DrawerItem(
                    label = "Devices",
                    icon = Icons.Outlined.Devices,
                    selected = currentRoute == "devices",
                    onClick = { onNavigate("devices") }
                )
                DrawerItem(
                    label = "History",
                    icon = Icons.Outlined.History,
                    selected = currentRoute == Screen.History.route,
                    onClick = { onNavigate(Screen.History.route) }
                )
                
                DrawerItem(
                    label = "Staff",
                    icon = Icons.Outlined.People,
                    selected = currentRoute == "staff",
                    onClick = { onNavigate("staff") }
                )
                DrawerItem(
                    label = "Reports",
                    icon = Icons.Outlined.Assessment,
                    selected = currentRoute == Screen.Reports.route,
                    onClick = { onNavigate(Screen.Reports.route) }
                )

                Spacer(Modifier.weight(1f))
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 28.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                DrawerItem(
                    label = "Profile",
                    icon = Icons.Outlined.Person,
                    selected = currentRoute == Screen.Profile.route,
                    onClick = { onNavigate(Screen.Profile.route) }
                )

                DrawerItem(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    selected = currentRoute == Screen.Settings.route,
                    onClick = { onNavigate(Screen.Settings.route) }
                )
                
                DrawerItem(
                    label = "Logout",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    selected = false,
                    color = MaterialTheme.colorScheme.error,
                    onClick = onLogout
                )
                
                Spacer(Modifier.height(24.dp))
            }
        }
    ) {
        content()
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { 
            Text(
                text = label, 
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ) 
        },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else color) },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = color
        )
    )
}
