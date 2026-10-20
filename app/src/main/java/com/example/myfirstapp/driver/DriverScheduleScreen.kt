package com.example.myfirstapp.driver

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfirstapp.Bus
import com.example.myfirstapp.BusRoute
import com.example.myfirstapp.BusSchedule
import com.example.myfirstapp.DatabaseManager
import com.example.myfirstapp.admin.LeafletMapViewScreen
import com.example.myfirstapp.user

private val DriverGreen = Color(0xFF116530)
private val DriverBg = Color(0xFFF5F8F6)

/**
 * Flexible name matching helper to compare Driver Names in Firestore with Logged-in Driver Name
 */
fun isDriverMatch(assignedDriver: String, loggedInUser: String): Boolean {
    val cleanAssigned = assignedDriver.lowercase().replace("driver", "").replace(",", " ").replace(".", "").trim()
    val cleanUser = loggedInUser.lowercase().replace("driver", "").replace(",", " ").replace(".", "").trim()

    if (cleanAssigned.isBlank() || cleanAssigned == "unassigned") return true
    if (cleanUser.isBlank()) return true

    if (cleanAssigned.contains(cleanUser) || cleanUser.contains(cleanAssigned)) return true

    val userWords = cleanUser.split(" ").map { it.trim() }.filter { it.length >= 2 }
    val assignedWords = cleanAssigned.split(" ").map { it.trim() }.filter { it.length >= 2 }

    return userWords.any { uWord -> assignedWords.any { aWord -> uWord.contains(aWord) || aWord.contains(uWord) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScheduleScreen(
    user: user,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("All") } // Default to "All"
    var viewingMapSchedule by remember { mutableStateOf<BusSchedule?>(null) }

    val allSchedules = remember { mutableStateListOf<BusSchedule>() }
    val registeredBuses = remember { mutableStateListOf<Bus>() }

    // Live sync duty schedules and buses from Cloud Firestore
    LaunchedEffect(Unit) {
        DatabaseManager.listenToSchedules(
            onUpdate = { fetched ->
                allSchedules.clear()
                allSchedules.addAll(fetched)
            }
        )

        DatabaseManager.listenToBuses(
            onUpdate = { fetchedBuses ->
                registeredBuses.clear()
                registeredBuses.addAll(fetchedBuses)
            }
        )
    }

    // Combine explicit bus_schedules and bus assignments from Bus/Schedule Management
    val combinedSchedules = remember(allSchedules.toList(), registeredBuses.toList()) {
        val result = mutableListOf<BusSchedule>()
        result.addAll(allSchedules)

        // For any bus assigned to a driver that doesn't have an explicit schedule document yet, synthesize one!
        registeredBuses.forEach { bus ->
            val bName = if (bus.busNumber.isNotBlank()) bus.busNumber else "BUS-${bus.busId}"
            val exists = result.any { s -> s.bus.busNumber.contains(bName) || bName.contains(s.bus.busNumber) || s.bus.busId == bus.busId }
            if (!exists && bus.assignedDriver.isNotBlank() && bus.assignedDriver != "Unassigned") {
                val rName = if (bus.route.isNotBlank()) bus.route else "Route: Tuy Public Transport Terminal ➔ Pasay"
                val startP = if (rName.contains(" ➔ ")) rName.split(" ➔ ")[0].replace(Regex("""^Route\s+[A-Za-z0-9-]+:\s*""", RegexOption.IGNORE_CASE), "").trim() else "Tuy Public Transport Terminal"
                val destP = if (rName.contains(" ➔ ")) rName.split(" ➔ ")[1].trim() else "Pasay"

                val routeObj = BusRoute("R-" + bus.busId, rName, startP, destP)
                val depTime = if (bus.departureTime.isNotBlank()) bus.departureTime else "10:00 AM"
                val scheduleObj = BusSchedule("SCH-" + bus.busId, bus, routeObj, depTime, "01:30 PM")
                scheduleObj.status = if (bus.status.isNotBlank()) bus.status else "Scheduled"
                result.add(scheduleObj)
            }
        }
        result
    }

    // Filter schedules relevant to this driver
    val myDriverSchedules = remember(combinedSchedules, user.fullname) {
        val matched = combinedSchedules.filter { s ->
            isDriverMatch(s.bus.assignedDriver, user.fullname)
        }
        if (matched.isNotEmpty()) matched else combinedSchedules
    }

    // Interactive Leaflet Driving Map for active driving duty
    if (viewingMapSchedule != null) {
        val sch = viewingMapSchedule!!
        LeafletMapViewScreen(
            leafletId = sch.scheduleId,
            routeName = sch.route.routeName,
            startPoint = sch.route.startPoint,
            destination = sch.route.destination,
            startLat = if (sch.route.startLat != 0.0) sch.route.startLat else null,
            startLng = if (sch.route.startLng != 0.0) sch.route.startLng else null,
            destLat = if (sch.route.destLat != 0.0) sch.route.destLat else null,
            destLng = if (sch.route.destLng != 0.0) sch.route.destLng else null,
            onBack = { viewingMapSchedule = null }
        )
        return
    }

    val pendingList = remember(myDriverSchedules) {
        myDriverSchedules.filter { s ->
            s.status.equals("Scheduled", ignoreCase = true) ||
            s.status.equals("Pending", ignoreCase = true) ||
            s.status.equals("Active", ignoreCase = true) ||
            s.status.equals("Available", ignoreCase = true) ||
            (!s.status.equals("Accepted", ignoreCase = true) &&
             !s.status.equals("Rejected", ignoreCase = true) &&
             !s.status.equals("Completed", ignoreCase = true) &&
             !s.status.equals("In Transit", ignoreCase = true))
        }
    }

    val acceptedList = remember(myDriverSchedules) {
        myDriverSchedules.filter { s ->
            s.status.equals("Accepted", ignoreCase = true) ||
            s.status.equals("In Transit", ignoreCase = true) ||
            s.status.equals("Departed", ignoreCase = true)
        }
    }

    val rejectedList = remember(myDriverSchedules) {
        myDriverSchedules.filter { s -> s.status.equals("Rejected", ignoreCase = true) }
    }

    val pendingCount = pendingList.size
    val acceptedCount = acceptedList.size
    val rejectedCount = rejectedList.size

    val filteredList = when (selectedTab) {
        "Pending" -> pendingList
        "Accepted" -> acceptedList
        "Rejected" -> rejectedList
        else -> myDriverSchedules
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DriverBg)
                .padding(16.dp)
        ) {
            // Header Profile Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DriverGreen)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = null, tint = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Driver Duty Portal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Driver: ${user.fullname}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        TextButton(onClick = onLogout) {
                            Text("Logout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Duty Summary Counters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(pendingCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text("Pending", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(acceptedCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text("Accepted", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(rejectedCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Text("Rejected", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("All", "Pending", "Accepted", "Rejected").forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = when (tab) {
                                    "Pending" -> "Pending ($pendingCount)"
                                    "Accepted" -> "Accepted ($acceptedCount)"
                                    "Rejected" -> "Rejected ($rejectedCount)"
                                    else -> "All (${myDriverSchedules.size})"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DriverGreen,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Duty Schedules Found", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("No schedules in '$selectedTab' status at this moment", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredList) { schedule ->
                        val isAccepted = schedule.status.equals("Accepted", ignoreCase = true)
                        val isInTransit = schedule.status.equals("In Transit", ignoreCase = true)
                        val isRejected = schedule.status.equals("Rejected", ignoreCase = true)
                        val isPending = !isAccepted && !isInTransit && !isRejected

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Bus & Status Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = null, tint = DriverGreen)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(schedule.bus.busNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DriverGreen)
                                            Text("Schedule ID: ${schedule.scheduleId}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }

                                    Surface(
                                        color = when {
                                            isInTransit -> Color(0xFFE3F2FD)
                                            isAccepted -> Color(0xFFE8F5E9)
                                            isRejected -> Color(0xFFFFEBEE)
                                            else -> Color(0xFFFFF3E0)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isInTransit -> "In Transit"
                                                isAccepted -> "Accepted"
                                                isRejected -> "Rejected"
                                                else -> "Pending Accept"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isInTransit -> Color(0xFF1565C0)
                                                isAccepted -> DriverGreen
                                                isRejected -> Color(0xFFC62828)
                                                else -> Color(0xFFE65100)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Route Name
                                Text(schedule.route.routeName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Departure: ${schedule.departureTime}", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                    Text("Arrival: ${schedule.arrivalTime}", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = DriverGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Assigned Driver: ${if (schedule.bus.assignedDriver.isNotBlank()) schedule.bus.assignedDriver else user.fullname}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DriverGreen)
                                }

                                if (schedule.bus.operator.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Duty Date: ${schedule.bus.operator}", fontSize = 11.sp, color = Color.Gray)
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEEEEEE))

                                // ACCEPT / REJECT / START DRIVING ACTION BUTTONS
                                when {
                                    isPending -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // ACCEPT SCHEDULE BUTTON
                                            Button(
                                                onClick = {
                                                    DatabaseManager.updateScheduleStatus(
                                                        scheduleId = schedule.scheduleId,
                                                        newStatus = "Accepted",
                                                        driverName = user.fullname,
                                                        onSuccess = {
                                                            schedule.status = "Accepted"
                                                            schedule.bus.assignedDriver = user.fullname
                                                            val idx = allSchedules.indexOf(schedule)
                                                            if (idx != -1) allSchedules[idx] = schedule
                                                            Toast.makeText(context, "Schedule ${schedule.scheduleId} Accepted!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onFailure = { e ->
                                                            Toast.makeText(context, "Failed to accept: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = DriverGreen),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Accept Duty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }

                                            // REJECT SCHEDULE BUTTON
                                            OutlinedButton(
                                                onClick = {
                                                    DatabaseManager.updateScheduleStatus(
                                                        scheduleId = schedule.scheduleId,
                                                        newStatus = "Rejected",
                                                        driverName = user.fullname,
                                                        onSuccess = {
                                                            schedule.status = "Rejected"
                                                            val idx = allSchedules.indexOf(schedule)
                                                            if (idx != -1) allSchedules[idx] = schedule
                                                            Toast.makeText(context, "Schedule ${schedule.scheduleId} Rejected", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onFailure = { e ->
                                                            Toast.makeText(context, "Failed to reject: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFC62828)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Cancel, contentDescription = "Reject", tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Reject Duty", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    isAccepted -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // START DRIVING BUTTON
                                            Button(
                                                onClick = {
                                                    DatabaseManager.updateScheduleStatus(
                                                        scheduleId = schedule.scheduleId,
                                                        newStatus = "In Transit",
                                                        driverName = user.fullname,
                                                        onSuccess = {
                                                            schedule.status = "In Transit"
                                                            val idx = allSchedules.indexOf(schedule)
                                                            if (idx != -1) allSchedules[idx] = schedule
                                                            viewingMapSchedule = schedule
                                                            Toast.makeText(context, "Driving Transit Started for ${schedule.scheduleId}!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Navigation, contentDescription = "Start", tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Start Transit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }

                                            // DECLINE / REJECT BUTTON
                                            OutlinedButton(
                                                onClick = {
                                                    DatabaseManager.updateScheduleStatus(
                                                        scheduleId = schedule.scheduleId,
                                                        newStatus = "Rejected",
                                                        driverName = user.fullname,
                                                        onSuccess = {
                                                            schedule.status = "Rejected"
                                                            val idx = allSchedules.indexOf(schedule)
                                                            if (idx != -1) allSchedules[idx] = schedule
                                                            Toast.makeText(context, "Schedule Declined", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFC62828))
                                            ) {
                                                Text("Reject", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    isInTransit -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewingMapSchedule = schedule },
                                                colors = ButtonDefaults.buttonColors(containerColor = DriverGreen),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Map, contentDescription = "Driving Map", tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("View Driving Map", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    DatabaseManager.updateScheduleStatus(
                                                        scheduleId = schedule.scheduleId,
                                                        newStatus = "Completed",
                                                        driverName = user.fullname,
                                                        onSuccess = {
                                                            schedule.status = "Completed"
                                                            val idx = allSchedules.indexOf(schedule)
                                                            if (idx != -1) allSchedules[idx] = schedule
                                                            Toast.makeText(context, "Trip Completed!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Complete Trip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    isRejected -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Schedule Declined by Driver", fontSize = 12.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                            OutlinedButton(
                                                onClick = {
                                                    DatabaseManager.updateScheduleStatus(
                                                        scheduleId = schedule.scheduleId,
                                                        newStatus = "Accepted",
                                                        driverName = user.fullname,
                                                        onSuccess = {
                                                            schedule.status = "Accepted"
                                                            schedule.bus.assignedDriver = user.fullname
                                                            val idx = allSchedules.indexOf(schedule)
                                                            if (idx != -1) allSchedules[idx] = schedule
                                                            Toast.makeText(context, "Schedule Re-Accepted!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Re-Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
