package com.example.myfirstapp.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
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
import com.example.myfirstapp.DriverRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScheduleRecord(
    val scheduleId: String,
    var busNumber: String,
    var routeName: String,
    var driverName: String = "Unassigned",
    var departureTime: String,
    var arrivalTime: String,
    var status: String,
    var dateDay: String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimePickerDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AdminGreen)
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        },
        containerColor = Color.White
    )
}

@OptIn(ExperimentalMaterial3Api::class)
fun formatTimePickerState(state: TimePickerState): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, state.hour)
        set(Calendar.MINUTE, state.minute)
    }
    return SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)
}

fun parseTimeToHourMinute(timeStr: String): Pair<Int, Int> {
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val date = sdf.parse(timeStr)
        if (date != null) {
            val cal = Calendar.getInstance().apply { time = date }
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else Pair(9, 30)
    } catch (_: Exception) {
        Pair(9, 30)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ScheduleRecord?>(null) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleRecord?>(null) }

    // Google Calendar Real Date State (Default = Today's System Date)
    val todaySdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val todayFullDateStr = remember { todaySdf.format(Date()) }
    
    var selectedFullDate by remember { mutableStateOf(todayFullDateStr) }
    var showDatePickerModal by remember { mutableStateOf(false) }

    val registeredBuses = remember { mutableStateListOf<Bus>() }
    val registeredRoutes = remember { mutableStateListOf<BusRoute>() }
    val registeredDrivers = remember { mutableStateListOf<DriverRecord>() }

    val scheduleList = remember { mutableStateListOf<ScheduleRecord>() }

    // Live sync directly with Cloud Firestore database for Schedules, Buses, Routes, and Drivers
    LaunchedEffect(Unit) {
        DatabaseManager.listenToSchedules(
            onUpdate = { fetchedSchedules ->
                scheduleList.clear()
                fetchedSchedules.forEach { s ->
                    scheduleList.add(
                        ScheduleRecord(
                            scheduleId = s.scheduleId,
                            busNumber = s.bus.busNumber,
                            routeName = s.route.routeName,
                            driverName = if (s.bus.assignedDriver.isNotBlank()) s.bus.assignedDriver else "Unassigned",
                            departureTime = s.departureTime,
                            arrivalTime = s.arrivalTime,
                            status = s.status,
                            dateDay = if (s.bus.operator.isNotBlank()) s.bus.operator else todayFullDateStr
                        )
                    )
                }
            }
        )

        DatabaseManager.listenToBuses(
            onUpdate = { buses ->
                registeredBuses.clear()
                registeredBuses.addAll(buses)
            }
        )

        DatabaseManager.listenToRoutes(
            onUpdate = { routes ->
                registeredRoutes.clear()
                registeredRoutes.addAll(routes)
            }
        )

        DatabaseManager.listenToDrivers(
            onUpdate = { drivers ->
                registeredDrivers.clear()
                registeredDrivers.addAll(drivers)
            }
        )
    }

    // Dynamic Real Calendar Ribbon Generator (Calculates real day names & dates relative to selected date)
    val dynamicCalendarDays = remember(selectedFullDate) {
        val sdfDayName = SimpleDateFormat("EEE", Locale.ENGLISH)
        val sdfDayNum = SimpleDateFormat("dd", Locale.ENGLISH)
        val sdfFull = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        val cal = Calendar.getInstance()
        try {
            cal.time = sdfFull.parse(selectedFullDate) ?: Date()
        } catch (_: Exception) {}

        cal.add(Calendar.DAY_OF_YEAR, -3)
        val list = mutableListOf<Triple<String, String, String>>()
        for (i in 0 until 7) {
            val dName = sdfDayName.format(cal.time)
            val dNum = sdfDayNum.format(cal.time)
            val fDate = sdfFull.format(cal.time)
            list.add(Triple(dName, dNum, fDate))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    // Friendly date header e.g. "Tuesday, March 31, 2026"
    val formattedDisplayDate = remember(selectedFullDate) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(selectedFullDate)
            if (parsed != null) SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.ENGLISH).format(parsed) else selectedFullDate
        } catch (_: Exception) {
            selectedFullDate
        }
    }

    // Filter Saved Schedules for Selected Date from Cloud Firestore
    val displayedSchedulesForSelectedDate = remember(scheduleList.toList(), selectedFullDate) {
        scheduleList.filter { it.dateDay == selectedFullDate || it.dateDay.isBlank() }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AdminGreen,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Schedule")
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AdminGreen
                        )
                    }
                    Text("Schedules Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                IconButton(onClick = { showDatePickerModal = true }) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Open Google Calendar Picker", tint = AdminGreen)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Calendar Ribbon Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Selected Date:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Text(formattedDisplayDate, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AdminGreen)
                }

                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, AdminGreen),
                    modifier = Modifier.clickable { showDatePickerModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Event, contentDescription = "Pick Date", tint = AdminGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Date", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AdminGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Real Calendar Ribbon Selector
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(dynamicCalendarDays) { triple ->
                    val dayName = triple.first
                    val dayNum = triple.second
                    val fullDateStr = triple.third
                    val isSelected = selectedFullDate == fullDateStr

                    Card(
                        modifier = Modifier
                            .width(52.dp)
                            .height(68.dp)
                            .clickable { selectedFullDate = fullDateStr },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AdminGreen else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dayName.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Gray
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dayNum,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.White else Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buses & Schedule Cards List fetched directly from Cloud Firestore
            if (displayedSchedulesForSelectedDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Schedules Found for $selectedFullDate", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Tap + button below to add a schedule", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(displayedSchedulesForSelectedDate) { schedule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingSchedule = schedule },
                            shape = RoundedCornerShape(14.dp),
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
                                        Icon(
                                            imageVector = Icons.Default.DirectionsBus,
                                            contentDescription = "Bus Icon",
                                            tint = AdminGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = schedule.busNumber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                    }

                                    Surface(
                                        color = when (schedule.status) {
                                            "Departed" -> Color(0xFFE8F5E9)
                                            "Scheduled", "Active", "Available" -> Color(0xFFE3F2FD)
                                            else -> Color(0xFFFFF3E0)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = schedule.status,
                                            fontSize = 11.sp,
                                            color = when (schedule.status) {
                                                "Departed" -> AdminGreen
                                                "Scheduled", "Active", "Available" -> Color(0xFF1565C0)
                                                else -> Color(0xFFE65100)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = "Driver", tint = AdminGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Driver: ${schedule.driverName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Route: ${schedule.routeName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AdminGreen)

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Date: ${schedule.dateDay}", fontSize = 11.sp, color = AdminGreen, fontWeight = FontWeight.Bold)
                                        Text("Departure: ${schedule.departureTime}", fontSize = 12.sp, color = Color.DarkGray)
                                        Text("Arrival: ${schedule.arrivalTime}", fontSize = 12.sp, color = Color.DarkGray)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { editingSchedule = schedule },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Schedule", tint = AdminGreen, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { scheduleToDelete = schedule },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Schedule", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
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

    // GOOGLE CALENDAR STYLE MATERIAL 3 DATE PICKER DIALOG MODAL
    if (showDatePickerModal) {
        val initialMillis = remember(selectedFullDate) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(selectedFullDate)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePickerModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedMillis
                            }
                            selectedFullDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
                        }
                        showDatePickerModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) {
                    Text("Select Date", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerModal = false }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ADD OR EDIT SCHEDULE MODAL DIALOG WITH MATERIAL 3 TIME PICKER DIALOGS
    if (showAddDialog || editingSchedule != null) {
        val isEditing = editingSchedule != null
        val targetSchedule = editingSchedule

        val autoScheduleId = remember { targetSchedule?.scheduleId ?: ("SCH-" + (104..999).random()) }

        val busOptions = if (registeredBuses.isNotEmpty()) {
            registeredBuses.map { b -> "${if (b.busNumber.isNotBlank()) b.busNumber else "BUS-" + b.busId} (${if (b.plateNumber.isNotBlank()) b.plateNumber else b.busId})" }
        } else {
            listOf("BUS-101")
        }

        val routeOptions = if (registeredRoutes.isNotEmpty()) {
            registeredRoutes.map { r -> r.routeName }
        } else {
            listOf("Route A01: City Terminal ➔ Airport Hub")
        }

        val driverOptions = if (registeredDrivers.isNotEmpty()) {
            registeredDrivers.map { d -> d.driverName }
        } else {
            listOf("Unassigned")
        }

        var selectedBus by remember { mutableStateOf(targetSchedule?.busNumber ?: busOptions.first()) }
        var busDropdownExpanded by remember { mutableStateOf(false) }

        var selectedRoute by remember { mutableStateOf(targetSchedule?.routeName ?: routeOptions.first()) }
        var routeDropdownExpanded by remember { mutableStateOf(false) }

        var selectedDriver by remember { mutableStateOf(targetSchedule?.driverName ?: "Unassigned") }
        var driverDropdownExpanded by remember { mutableStateOf(false) }

        var scheduleDateInput by remember { mutableStateOf(targetSchedule?.dateDay ?: selectedFullDate) }
        var departureTime by remember { mutableStateOf(targetSchedule?.departureTime ?: "10:00 AM") }
        var arrivalTime by remember { mutableStateOf(targetSchedule?.arrivalTime ?: "01:30 PM") }
        var selectedStatus by remember { mutableStateOf(targetSchedule?.status ?: "Scheduled") }

        var showDepartureTimePicker by remember { mutableStateOf(false) }
        var showArrivalTimePicker by remember { mutableStateOf(false) }

        val (depHour, depMin) = remember(departureTime) { parseTimeToHourMinute(departureTime) }
        val departureTimeState = rememberTimePickerState(initialHour = depHour, initialMinute = depMin, is24Hour = false)

        val (arrHour, arrMin) = remember(arrivalTime) { parseTimeToHourMinute(arrivalTime) }
        val arrivalTimeState = rememberTimePickerState(initialHour = arrHour, initialMinute = arrMin, is24Hour = false)

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

        val statusOptions = listOf("Scheduled", "Departed", "Delayed")

        // DEPARTURE TIME PICKER DIALOG MODAL
        if (showDepartureTimePicker) {
            ScheduleTimePickerDialog(
                title = "Select Departure Time",
                onDismissRequest = { showDepartureTimePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            departureTime = formatTimePickerState(departureTimeState)
                            showDepartureTimePicker = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                    ) {
                        Text("Set Departure Time", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDepartureTimePicker = false }) {
                        Text("Cancel", color = Color.DarkGray)
                    }
                }
            ) {
                TimePicker(state = departureTimeState)
            }
        }

        // ARRIVAL TIME PICKER DIALOG MODAL
        if (showArrivalTimePicker) {
            ScheduleTimePickerDialog(
                title = "Select Arrival Time",
                onDismissRequest = { showArrivalTimePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            arrivalTime = formatTimePickerState(arrivalTimeState)
                            showArrivalTimePicker = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                    ) {
                        Text("Set Arrival Time", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showArrivalTimePicker = false }) {
                        Text("Cancel", color = Color.DarkGray)
                    }
                }
            ) {
                TimePicker(state = arrivalTimeState)
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingSchedule = null
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text(if (isEditing) "Edit Bus Schedule" else "Add Bus Schedule", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
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
                            text = "Schedule ID: $autoScheduleId",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AdminGreen
                        )
                    }

                    // Schedule Date Field
                    OutlinedTextField(
                        value = scheduleDateInput,
                        onValueChange = { scheduleDateInput = it },
                        label = { Text("Schedule Date (YYYY-MM-DD)", color = Color.DarkGray) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePickerModal = true }) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = AdminGreen)
                            }
                        },
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 1. SELECT BUS (Connected to Bus Management)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedBus,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Bus (from Bus Management)", color = Color.DarkGray) },
                            trailingIcon = {
                                IconButton(onClick = { busDropdownExpanded = !busDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Bus")
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { busDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = busDropdownExpanded,
                            onDismissRequest = { busDropdownExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            busOptions.forEach { busStr ->
                                DropdownMenuItem(
                                    text = { Text(busStr, color = Color.Black) },
                                    onClick = {
                                        selectedBus = busStr
                                        busDropdownExpanded = false

                                        val matchingBusObj = registeredBuses.find { b -> busStr.contains(b.busNumber) || busStr.contains(b.busId) }
                                        if (matchingBusObj != null) {
                                            if (matchingBusObj.assignedDriver.isNotBlank() && matchingBusObj.assignedDriver != "Unassigned") {
                                                selectedDriver = matchingBusObj.assignedDriver
                                            }
                                            if (matchingBusObj.route.isNotBlank()) {
                                                selectedRoute = matchingBusObj.route
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 2. SELECT DRIVER (Connected to Driver Management)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedDriver,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned Driver (from Driver Management)", color = Color.DarkGray) },
                            trailingIcon = {
                                IconButton(onClick = { driverDropdownExpanded = !driverDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Driver")
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { driverDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = driverDropdownExpanded,
                            onDismissRequest = { driverDropdownExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            driverOptions.forEach { drv ->
                                DropdownMenuItem(
                                    text = { Text(drv, color = Color.Black) },
                                    onClick = {
                                        selectedDriver = drv
                                        driverDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 3. SELECT ROUTE (Connected to Route Management)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedRoute,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Route (from Route Management)", color = Color.DarkGray) },
                            trailingIcon = {
                                IconButton(onClick = { routeDropdownExpanded = !routeDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Route")
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { routeDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = routeDropdownExpanded,
                            onDismissRequest = { routeDropdownExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            routeOptions.forEach { route ->
                                DropdownMenuItem(
                                    text = { Text(route, color = Color.Black) },
                                    onClick = {
                                        selectedRoute = route
                                        routeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Interactive Departure Time Picker Field
                    OutlinedTextField(
                        value = departureTime,
                        onValueChange = { departureTime = it },
                        readOnly = true,
                        label = { Text("Departure Time", color = Color.DarkGray) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showDepartureTimePicker = true }) {
                                Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Pick Departure Time", tint = AdminGreen)
                            }
                        },
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDepartureTimePicker = true }
                    )

                    // Interactive Arrival Time Picker Field
                    OutlinedTextField(
                        value = arrivalTime,
                        onValueChange = { arrivalTime = it },
                        readOnly = true,
                        label = { Text("Arrival Time", color = Color.DarkGray) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showArrivalTimePicker = true }) {
                                Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Pick Arrival Time", tint = AdminGreen)
                            }
                        },
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showArrivalTimePicker = true }
                    )

                    // Schedule Status Selection
                    Text("Schedule Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)
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
                        Text(dialogError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newScheduleRecord = ScheduleRecord(
                            scheduleId = autoScheduleId,
                            busNumber = selectedBus,
                            routeName = selectedRoute,
                            driverName = selectedDriver,
                            departureTime = departureTime.trim(),
                            arrivalTime = arrivalTime.trim(),
                            status = selectedStatus,
                            dateDay = scheduleDateInput.trim()
                        )

                        val existingIndex = scheduleList.indexOfFirst { it.scheduleId == autoScheduleId }
                        if (existingIndex != -1) {
                            scheduleList[existingIndex] = newScheduleRecord
                        } else {
                            scheduleList.add(0, newScheduleRecord)
                        }

                        val sampleBus = Bus(autoScheduleId, selectedBus, 50, selectedStatus)
                        sampleBus.assignedDriver = selectedDriver
                        val sampleRoute = BusRoute("R01", selectedRoute, "Origin", "Destination")
                        val busScheduleObj = BusSchedule(
                            scheduleId = autoScheduleId,
                            bus = sampleBus,
                            route = sampleRoute,
                            departureTime = departureTime.trim(),
                            arrivalTime = arrivalTime.trim()
                        )
                        busScheduleObj.status = selectedStatus
                        DatabaseManager.saveBusSchedule(busScheduleObj, driverName = selectedDriver, scheduleDate = scheduleDateInput.trim())

                        Toast.makeText(context, "Schedule $autoScheduleId saved to Cloud Database!", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        editingSchedule = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) {
                    Text("Save Schedule", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingSchedule = null
                }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // DELETE SCHEDULE CONFIRMATION DIALOG
    if (scheduleToDelete != null) {
        val targetToDelete = scheduleToDelete!!
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Bus Schedule", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
                }
            },
            text = {
                Text("Are you sure you want to delete Schedule ${targetToDelete.scheduleId} (${targetToDelete.busNumber})? This action will remove the record permanently from Cloud Firestore.", fontSize = 14.sp, color = Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        DatabaseManager.deleteBusSchedule(
                            scheduleId = targetToDelete.scheduleId,
                            onSuccess = {
                                Toast.makeText(context, "Schedule ${targetToDelete.scheduleId} deleted!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        scheduleList.removeIf { it.scheduleId == targetToDelete.scheduleId }
                        scheduleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Schedule", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}
