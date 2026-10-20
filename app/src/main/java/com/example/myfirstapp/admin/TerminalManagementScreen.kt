package com.example.myfirstapp.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfirstapp.BusStop
import com.example.myfirstapp.DatabaseManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTerminal by remember { mutableStateOf<BusStop?>(null) }
    var terminalToDelete by remember { mutableStateOf<BusStop?>(null) }
    var viewingMapTerminal by remember { mutableStateOf<BusStop?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    val terminalList = remember { mutableStateListOf<BusStop>() }

    // Pre-seeded Default Terminals if Firestore is initially empty
    val initialDefaultTerminals = listOf(
        BusStop("TERM-001", "Central Terminal - Manila", "Central Manila, Metro Manila", 14.5995, 120.9842, baysCount = 10, activeBays = 7, operatingHours = "24 Hours Open", status = "Active"),
        BusStop("TERM-002", "PITX Terminal - Parañaque", "Kennedy Rd, Tambo, Parañaque, Metro Manila", 14.5092, 120.9917, baysCount = 12, activeBays = 8, operatingHours = "04:00 AM - 11:30 PM", status = "Active"),
        BusStop("TERM-003", "Airport Hub - NAIA Pasay", "NAIA Terminal 3, Pasay, Metro Manila", 14.5204, 121.0122, baysCount = 8, activeBays = 4, operatingHours = "24 Hours Open", status = "Active"),
        BusStop("TERM-004", "Cubao Terminal - Quezon City", "EDSA Cubao, Quezon City, Metro Manila", 14.6195, 121.0512, baysCount = 10, activeBays = 6, operatingHours = "05:00 AM - 10:00 PM", status = "Active"),
        BusStop("TERM-005", "Palico Grand Terminal - Nasugbu", "2MXX+8G6, Tagaytay - Nasugbu Hwy, Nasugbu, Batangas", 14.0728, 120.6983, baysCount = 10, activeBays = 6, operatingHours = "04:00 AM - 10:00 PM", status = "Active")
    )

    // Real-time live listener for Terminal Hubs / Bus Stops
    LaunchedEffect(Unit) {
        DatabaseManager.listenToBusStops(
            onUpdate = { fetchedStops ->
                if (fetchedStops.isEmpty()) {
                    // Seed initial terminals to Firestore if empty
                    initialDefaultTerminals.forEach { DatabaseManager.saveBusStop(it) }
                } else {
                    terminalList.clear()
                    terminalList.addAll(fetchedStops)
                }
            }
        )
    }

    // Open Interactive Map for a specific terminal
    if (viewingMapTerminal != null) {
        val term = viewingMapTerminal!!
        LeafletMapViewScreen(
            leafletId = "L-${term.stopId}",
            routeName = "${term.stopName} ➔ Regional Routes",
            onBack = { viewingMapTerminal = null }
        )
        return
    }

    val filteredTerminals = terminalList.filter {
        it.stopName.contains(searchQuery, ignoreCase = true) ||
        it.location.contains(searchQuery, ignoreCase = true) ||
        it.stopId.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AdminGreen,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Terminal Hub")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AdminBg)
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AdminGreen
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Terminal Hubs & Bays", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("${terminalList.size} Active Hubs Registered", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search terminal name, city, or ID...", color = Color.Gray) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = AdminGreen) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AdminGreen,
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredTerminals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Store, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Terminal Hubs Found", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Tap + button below to add a terminal hub", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredTerminals) { terminal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(imageVector = Icons.Default.Store, contentDescription = null, tint = AdminGreen)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(terminal.stopName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                            Text("ID: ${terminal.stopId}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }

                                    Surface(
                                        color = when (terminal.status) {
                                            "Active" -> Color(0xFFE8F5E9)
                                            "Maintenance" -> Color(0xFFFFF3E0)
                                            else -> Color(0xFFFFEBEE)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = terminal.status,
                                            fontSize = 11.sp,
                                            color = when (terminal.status) {
                                                "Active" -> AdminGreen
                                                "Maintenance" -> Color(0xFFE65100)
                                                else -> Color(0xFFC62828)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = AdminGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(terminal.location, fontSize = 13.sp, color = Color.DarkGray)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hours: ${terminal.operatingHours}", fontSize = 12.sp, color = Color.DarkGray)
                                    Text("GPS: ${String.format("%.4f", terminal.latitude)}, ${String.format("%.4f", terminal.longitude)}", fontSize = 11.sp, color = Color.Gray)
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEEEEEE))

                                // Terminal Bay Capacity Indicator
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Terminal Bays Occupied:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                        Text("${terminal.activeBays} / ${terminal.baysCount} Bays", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AdminGreen)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val progressFraction = if (terminal.baysCount > 0) (terminal.activeBays.toFloat() / terminal.baysCount.toFloat()).coerceIn(0f, 1f) else 0f
                                    LinearProgressIndicator(
                                        progress = { progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (progressFraction > 0.85f) Color(0xFFC62828) else AdminGreen,
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewingMapTerminal = terminal },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, AdminGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Map, contentDescription = "Map", tint = AdminGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View Map", color = AdminGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { editingTerminal = terminal },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Terminal", tint = AdminGreen, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { terminalToDelete = terminal },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Terminal", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
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

    // DELETE TERMINAL CONFIRMATION DIALOG
    if (terminalToDelete != null) {
        val target = terminalToDelete!!
        AlertDialog(
            onDismissRequest = { terminalToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text("Delete Terminal Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
            },
            text = {
                Text("Are you sure you want to delete ${target.stopName} (${target.stopId})? This action will permanently remove the terminal hub record from Cloud Firestore.", fontSize = 14.sp, color = Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        DatabaseManager.deleteBusStop(
                            stopId = target.stopId,
                            onSuccess = {
                                Toast.makeText(context, "Terminal ${target.stopName} deleted!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        terminalList.remove(target)
                        terminalToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { terminalToDelete = null }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // ADD OR EDIT TERMINAL MODAL DIALOG
    if (showAddDialog || editingTerminal != null) {
        val isEditing = editingTerminal != null
        val target = editingTerminal

        val autoStopId = remember { target?.stopId ?: ("TERM-" + (100..999).random()) }
        var stopName by remember { mutableStateOf(target?.stopName ?: "") }
        var location by remember { mutableStateOf(target?.location ?: "") }
        var latText by remember { mutableStateOf(target?.latitude?.toString() ?: "14.5995") }
        var lngText by remember { mutableStateOf(target?.longitude?.toString() ?: "120.9842") }
        var baysCountText by remember { mutableStateOf(target?.baysCount?.toString() ?: "8") }
        var activeBaysText by remember { mutableStateOf(target?.activeBays?.toString() ?: "5") }
        var operatingHours by remember { mutableStateOf(target?.operatingHours ?: "04:00 AM - 11:00 PM") }
        var selectedStatus by remember { mutableStateOf(target?.status ?: "Active") }

        var dialogError by remember { mutableStateOf("") }

        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedLabelColor = AdminGreen,
            unfocusedLabelColor = Color.DarkGray,
            focusedBorderColor = AdminGreen,
            unfocusedBorderColor = Color.LightGray
        )

        val statusOptions = listOf("Active", "Maintenance", "Closed")

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingTerminal = null
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text(if (isEditing) "Edit Terminal Details" else "Add New Terminal Hub", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Terminal ID: $autoStopId",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AdminGreen
                        )
                    }

                    // Terminal Name
                    OutlinedTextField(
                        value = stopName,
                        onValueChange = { stopName = it },
                        label = { Text("Terminal Name", color = Color.DarkGray) },
                        placeholder = { Text("e.g. Central Terminal - Manila", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Address / Location
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Address / City Location", color = Color.DarkGray) },
                        placeholder = { Text("e.g. Ermita, Manila, Metro Manila", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Operating Hours
                    OutlinedTextField(
                        value = operatingHours,
                        onValueChange = { operatingHours = it },
                        label = { Text("Operating Hours", color = Color.DarkGray) },
                        placeholder = { Text("e.g. 04:00 AM - 11:00 PM or 24/7", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Coordinates (Latitude & Longitude)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitude", color = Color.DarkGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = lngText,
                            onValueChange = { lngText = it },
                            label = { Text("Longitude", color = Color.DarkGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Terminal Bays (Total & Active Occupied)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = baysCountText,
                            onValueChange = { baysCountText = it },
                            label = { Text("Total Bays", color = Color.DarkGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = activeBaysText,
                            onValueChange = { activeBaysText = it },
                            label = { Text("Occupied Bays", color = Color.DarkGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Status Selection
                    Text("Terminal Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        statusOptions.forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AdminGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF0F4F1),
                                    labelColor = Color.Black
                                )
                            )
                        }
                    }

                    if (dialogError.isNotEmpty()) {
                        Text(dialogError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (stopName.isBlank()) {
                            dialogError = "Please enter terminal name"
                            return@Button
                        }
                        if (location.isBlank()) {
                            dialogError = "Please enter city location / address"
                            return@Button
                        }

                        val lat = latText.toDoubleOrNull() ?: 14.5995
                        val lng = lngText.toDoubleOrNull() ?: 120.9842
                        val bCount = baysCountText.toIntOrNull() ?: 8
                        val aBays = activeBaysText.toIntOrNull() ?: 5

                        val newTerminal = BusStop(
                            stopId = autoStopId,
                            stopName = stopName.trim(),
                            location = location.trim(),
                            latitude = lat,
                            longitude = lng,
                            baysCount = bCount,
                            activeBays = aBays,
                            operatingHours = operatingHours.trim(),
                            status = selectedStatus
                        )

                        // Save or update in Firestore
                        val existingIndex = terminalList.indexOfFirst { it.stopId == autoStopId }
                        if (existingIndex != -1) {
                            terminalList[existingIndex] = newTerminal
                        } else {
                            terminalList.add(0, newTerminal)
                        }

                        DatabaseManager.saveBusStop(newTerminal)
                        Toast.makeText(context, "Terminal ${newTerminal.stopName} saved to Cloud Database!", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        editingTerminal = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) {
                    Text("Save Terminal", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingTerminal = null
                }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}
