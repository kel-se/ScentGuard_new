package com.example.scentguard.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scentguard.data.model.UserProfile
import com.example.scentguard.data.model.MascotAvatars
import com.example.scentguard.navigation.Screen

@Composable
fun ScentGuardNavigationDrawer(
    user: UserProfile?,
    currentRoute: String?,
    drawerState: DrawerState,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.width(310.dp)
            ) {
                // Header Padding
                Spacer(Modifier.height(48.dp))

                // User Profile Section - Premium & Polished
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            shadowElevation = 2.dp
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
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                text = user?.fullName ?: "Guest User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = user?.role?.uppercase() ?: "STAFF",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Navigation Category: MENU
                SectionLabel("MAIN MENU")
                
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

                Spacer(Modifier.height(24.dp))
                
                // Navigation Category: ACCOUNT
                SectionLabel("ACCOUNT & SETTINGS")

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
                
                Spacer(Modifier.weight(1f))

                // Footer section with Logout
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                DrawerItem(
                    label = "Logout Session",
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
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        letterSpacing = 1.sp
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        color
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

