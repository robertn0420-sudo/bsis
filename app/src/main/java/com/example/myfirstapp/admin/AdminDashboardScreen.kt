package com.example.myfirstapp.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfirstapp.DatabaseManager
import com.example.myfirstapp.user

val AdminGreen = Color(0xFF116530)
val AdminBg = Color(0xFFF5F8F6)

@Composable
fun AdminDashboardScreen(
    user: user,
    onLogout: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("Dashboard") }
    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    var dbStatus by remember { mutableStateOf("") }

    if (selectedTab == "Schedules") {
        ScheduleManagementScreen(onBack = { selectedTab = "Dashboard" })
        return
    }

    if (selectedTab == "Map") {
        LeafletMapViewScreen(leafletId = "L001", routeName = "City Terminal ➔ Airport Hub", onBack = { selectedTab = "Dashboard" })
        return
    }

    if (activeSubScreen != null) {
        when (activeSubScreen) {
            "Bus" -> BusManagementScreen(onBack = { activeSubScreen = null })
            "Driver" -> DriverManagementScreen(onBack = { activeSubScreen = null })
            "Schedule" -> ScheduleManagementScreen(onBack = { activeSubScreen = null })
            "Route" -> RouteManagementScreen(onBack = { activeSubScreen = null })
            "Tracker" -> LeafletMapViewScreen(leafletId = "L001", routeName = "Route A01: City Terminal ➔ Airport Hub", onBack = { activeSubScreen = null })
            "Terminal" -> TerminalManagementScreen(onBack = { activeSubScreen = null })
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$activeSubScreen Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { activeSubScreen = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                        ) {
                            Text("Back to Admin Portal")
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                listOf("Dashboard", "Schedules", "Map", "Settings").forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    "Dashboard" -> Icons.Default.Dashboard
                                    "Schedules" -> Icons.Default.CalendarMonth
                                    "Map" -> Icons.Default.Map
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = tab,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = { Text(tab, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AdminGreen,
                            selectedTextColor = AdminGreen,
                            indicatorColor = Color(0xFFE8F5E9)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AdminBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Admin Top Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AdminGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Welcome back,",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "${user.fullname} (Admin)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stat Cards Row - Fully responsive to small mobile screens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminStatCard(title = "Buses Active", value = "24/30", modifier = Modifier.weight(1f))
                AdminStatCard(title = "Today's Routes", value = "42", modifier = Modifier.weight(1f))
                AdminStatCard(title = "Terminal Bays", value = "5/8", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Quick Action",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Grid (2-column responsive layout)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        icon = Icons.Default.DirectionsBus,
                        title = "Bus\nManagement",
                        modifier = Modifier.weight(1f),
                        onClick = { activeSubScreen = "Bus" }
                    )
                    QuickActionTile(
                        icon = Icons.AutoMirrored.Filled.AltRoute,
                        title = "Route\nManagement",
                        modifier = Modifier.weight(1f),
                        onClick = { activeSubScreen = "Route" }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        icon = Icons.Default.Schedule,
                        title = "Schedule\nManagement",
                        modifier = Modifier.weight(1f),
                        onClick = { activeSubScreen = "Schedule" }
                    )
                    QuickActionTile(
                        icon = Icons.Default.Person,
                        title = "Driver\nManagement",
                        modifier = Modifier.weight(1f),
                        onClick = { activeSubScreen = "Driver" }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        icon = Icons.Default.Store,
                        title = "Terminal\nHubs",
                        modifier = Modifier.weight(1f),
                        onClick = { activeSubScreen = "Terminal" }
                    )
                    QuickActionTile(
                        icon = Icons.Default.LocationOn,
                        title = "Real-Time\nTracker",
                        modifier = Modifier.weight(1f),
                        onClick = { activeSubScreen = "Tracker" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Database Sync Status Button
            Button(
                onClick = {
                    dbStatus = "Saving Admin & Transit records to Firestore..."
                    DatabaseManager.saveAllSampleData(
                        onSuccess = { dbStatus = "Admin Records Synced with Cloud Database" },
                        onFailure = { e -> dbStatus = "Sync Error: ${e.localizedMessage}" }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Sync Admin Data to Cloud Firestore", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (dbStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dbStatus,
                    color = AdminGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Logout Admin Session", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AdminGreen)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun AdminStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AdminGreen, maxLines = 1)
        }
    }
}

@Composable
fun QuickActionTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(88.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AdminGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                lineHeight = 13.sp
            )
        }
    }
}
