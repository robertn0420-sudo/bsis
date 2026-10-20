package com.example.myfirstapp.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfirstapp.Bus
import com.example.myfirstapp.BusRoute
import com.example.myfirstapp.DatabaseManager
import com.example.myfirstapp.DriverRecord
import com.example.myfirstapp.R
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Converts a picked Uri into a lightweight Base64 JPEG string (~25KB)
 * so it stores directly in Cloud Firestore and renders across all devices.
 */
fun convertUriToBase64(context: Context, uri: Uri): String {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        var inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        var sampleSize = 1
        val maxDim = 400
        while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        inputStream?.close()

        if (originalBitmap != null) {
            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } else ""
    } catch (_: Exception) {
        ""
    }
}

/**
 * Universal Bus Image component that decodes Base64 strings, local files, or URLs,
 * and falls back to R.drawable.logo if missing.
 */
@Composable
fun BusImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imageUrl) {
        if (imageUrl.startsWith("data:image") && imageUrl.contains("base64,")) {
            try {
                val base64Data = imageUrl.substringAfter("base64,")
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (_: Exception) {
                null
            }
        } else if (imageUrl.startsWith("/")) {
            try {
                val file = File(imageUrl)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (_: Exception) {
                null
            }
        } else null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        val context = LocalContext.current
        val imageRequest = remember(imageUrl) {
            val model: Any = if (imageUrl.isBlank()) R.drawable.logo else imageUrl
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .fallback(R.drawable.logo)
                .error(R.drawable.logo)
                .placeholder(R.drawable.logo)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    
    var viewingBus by remember { mutableStateOf<Bus?>(null) }
    var editingBus by remember { mutableStateOf<Bus?>(null) }
    var busToDelete by remember { mutableStateOf<Bus?>(null) }

    val registeredDriverRecords = remember { mutableStateListOf<DriverRecord>() }
    val busTypeOptions = listOf("Air-conditioned", "Ordinary")
    val busList = remember { mutableStateListOf<Bus>() }
    val availableRoutes = remember { mutableStateListOf<BusRoute>() }

    LaunchedEffect(Unit) {
        DatabaseManager.listenToDrivers(
            onUpdate = { fetchedDrivers ->
                registeredDriverRecords.clear()
                registeredDriverRecords.addAll(fetchedDrivers)
            }
        )

        DatabaseManager.listenToBuses(
            onUpdate = { fetchedBuses ->
                busList.clear()
                busList.addAll(fetchedBuses)
            }
        )

        DatabaseManager.listenToRoutes(
            onUpdate = { fetchedRoutes ->
                availableRoutes.clear()
                availableRoutes.addAll(fetchedRoutes)
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AdminGreen,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Bus")
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
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AdminGreen
                    )
                }
                Text("Bus Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (busList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Buses Found", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Tap + button below to add a bus", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(busList) { bus ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingBus = bus },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BusImage(
                                        imageUrl = bus.imageUrl,
                                        contentDescription = "Bus Image",
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Bus ID: ${bus.busId}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AdminGreen)
                                            Surface(
                                                color = when (bus.status) {
                                                    "Active" -> Color(0xFFE8F5E9)
                                                    "In Transit" -> Color(0xFFE3F2FD)
                                                    else -> Color(0xFFFFEBEE)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = bus.status,
                                                    fontSize = 11.sp,
                                                    color = when (bus.status) {
                                                        "Active" -> AdminGreen
                                                        "In Transit" -> Color(0xFF1565C0)
                                                        else -> Color(0xFFC62828)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Bus Company: ${if (bus.operator.isNotEmpty()) bus.operator else bus.busNumber}",
                                            fontSize = 14.sp,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (bus.plateNumber.isNotEmpty()) {
                                            Text("Plate Number: ${bus.plateNumber}", fontSize = 13.sp, color = Color.DarkGray)
                                        }
                                        Text("Bus Type: ${bus.busType}  •  ${bus.capacity} seats", fontSize = 12.sp, color = Color.DarkGray)
                                        Text("Driver: ${bus.assignedDriver}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AdminGreen)
                                        val matchedCardDriver = registeredDriverRecords.find { it.driverName.equals(bus.assignedDriver, ignoreCase = true) }
                                        if (matchedCardDriver != null && matchedCardDriver.contactNumber.isNotBlank()) {
                                            Text("Contact: ${matchedCardDriver.contactNumber}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEEEEEE))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bus.route.isNotEmpty()) {
                                        Text("Route: ${bus.route}", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewingBus = bus },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "View Details",
                                                tint = Color(0xFF1565C0),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { editingBus = bus },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Details",
                                                tint = AdminGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { busToDelete = bus },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Bus",
                                                tint = Color(0xFFC62828),
                                                modifier = Modifier.size(20.dp)
                                            )
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

    // DELETE BUS CONFIRMATION DIALOG
    if (busToDelete != null) {
        val targetBus = busToDelete!!
        AlertDialog(
            onDismissRequest = { busToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text("Delete Bus", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
            },
            text = {
                Text(
                    "Are you sure you want to delete ${if (targetBus.operator.isNotBlank()) targetBus.operator else targetBus.busNumber} (${targetBus.busId})? This action will remove the record permanently from Cloud Firestore.",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        DatabaseManager.deleteBus(
                            busId = targetBus.busId,
                            onSuccess = {
                                Toast.makeText(context, "Bus ${targetBus.busId} deleted!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        busList.remove(targetBus)
                        busToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { busToDelete = null }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // VIEW BUS DETAILS DIALOG
    if (viewingBus != null) {
        val bus = viewingBus!!
        AlertDialog(
            onDismissRequest = { viewingBus = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bus Details", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
                    Surface(
                        color = when (bus.status) {
                            "Active" -> Color(0xFFE8F5E9)
                            "In Transit" -> Color(0xFFE3F2FD)
                            else -> Color(0xFFFFEBEE)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = bus.status,
                            fontSize = 11.sp,
                            color = when (bus.status) {
                                "Active" -> AdminGreen
                                "In Transit" -> Color(0xFF1565C0)
                                else -> Color(0xFFC62828)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF5F8F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        BusImage(
                            imageUrl = bus.imageUrl,
                            contentDescription = "Bus Detail Image",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bus ID: ${bus.busId}",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AdminGreen
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBF9)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Bus Company / Operator:", fontSize = 11.sp, color = Color.Gray)
                            Text(if (bus.operator.isNotEmpty()) bus.operator else bus.busNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)

                            HorizontalDivider(color = Color.LightGray)

                            Text("Plate Number:", fontSize = 11.sp, color = Color.Gray)
                            Text(if (bus.plateNumber.isNotEmpty()) bus.plateNumber else "N/A", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)

                            HorizontalDivider(color = Color.LightGray)

                            Text("Bus Type & Capacity:", fontSize = 11.sp, color = Color.Gray)
                            Text("${bus.busType} • ${bus.capacity} Seats", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)

                            HorizontalDivider(color = Color.LightGray)

                            Text("Assigned Driver (Driver Management):", fontSize = 11.sp, color = Color.Gray)
                            Text(bus.assignedDriver, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AdminGreen)
                            val detailDriver = registeredDriverRecords.find { it.driverName.equals(bus.assignedDriver, ignoreCase = true) }
                            if (detailDriver != null) {
                                if (detailDriver.contactNumber.isNotBlank()) {
                                    Text("Contact: ${detailDriver.contactNumber}", fontSize = 11.sp, color = Color.DarkGray)
                                }
                                if (detailDriver.licenseNumber.isNotBlank()) {
                                    Text("License No: ${detailDriver.licenseNumber}", fontSize = 11.sp, color = Color.DarkGray)
                                }
                                Text("Status: ${detailDriver.employmentStatus}", fontSize = 11.sp, color = AdminGreen, fontWeight = FontWeight.Medium)
                            }

                            HorizontalDivider(color = Color.LightGray)

                            Text("Assigned Route:", fontSize = 11.sp, color = Color.Gray)
                            Text(if (bus.route.isNotEmpty()) bus.route else "Unassigned", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingBus = bus
                        viewingBus = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Bus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingBus = null }) {
                    Text("Close", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // ADD OR EDIT BUS DIALOG COMPONENT
    if (showAddDialog || editingBus != null) {
        val isEditing = editingBus != null
        val targetBus = editingBus

        val autoGeneratedId = remember { targetBus?.busId ?: ("B" + (1000..9999).random()) }
        var busCompany by remember { mutableStateOf(targetBus?.operator?.ifEmpty { targetBus.busNumber } ?: "") }
        var plateNumber by remember { mutableStateOf(targetBus?.plateNumber ?: "") }
        var busType by remember { mutableStateOf(targetBus?.busType ?: "Air-conditioned") }
        var busTypeDropdownExpanded by remember { mutableStateOf(false) }
        
        var capacityText by remember { mutableStateOf(targetBus?.capacity?.toString() ?: "50") }
        
        var assignedDriver by remember { mutableStateOf(targetBus?.assignedDriver ?: "Unassigned") }
        var driverDropdownExpanded by remember { mutableStateOf(false) }

        var routeDropdownExpanded by remember { mutableStateOf(false) }
        var assignedRoute by remember { mutableStateOf(targetBus?.route?.ifEmpty { availableRoutes.firstOrNull()?.routeName ?: "Unassigned" } ?: (availableRoutes.firstOrNull()?.routeName ?: "Unassigned")) }

        var selectedStatus by remember { mutableStateOf(targetBus?.status ?: "Active") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        val currentImageUrl = targetBus?.imageUrl ?: ""

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
            }
        }

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

        val statusOptions = listOf("Active", "In Transit", "Inactive")

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingBus = null
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text(if (isEditing) "Edit Bus Details" else "Add New Bus", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
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
                            text = "Bus ID: $autoGeneratedId",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AdminGreen
                        )
                    }

                    // Interactive Bus Image Picker
                    Text("Bus Image", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.DarkGray)
                    Surface(
                        color = Color(0xFFF5F8F6),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { imagePickerLauncher.launch("image/*") }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Picked Bus Image",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                BusImage(
                                    imageUrl = currentImageUrl,
                                    contentDescription = "Current Bus Image",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedImageUri != null) "New Photo Selected" else "Tap to Change Bus Photo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(text = "Opens Device Gallery", fontSize = 11.sp, color = Color.Gray)
                            }
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Pick Image", tint = AdminGreen)
                        }
                    }

                    // Bus Company Name
                    OutlinedTextField(
                        value = busCompany,
                        onValueChange = { busCompany = it },
                        label = { Text("Bus Company Name", color = Color.DarkGray) },
                        placeholder = { Text("e.g. TransitGo Express, Jam Liner, Victory Liner", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Plate Number
                    OutlinedTextField(
                        value = plateNumber,
                        onValueChange = { plateNumber = it },
                        label = { Text("Plate Number", color = Color.DarkGray) },
                        placeholder = { Text("e.g. ABC-1234", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bus Type Dropdown (Air-conditioned / Ordinary)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = busType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Bus Type", color = Color.DarkGray) },
                            trailingIcon = {
                                IconButton(onClick = { busTypeDropdownExpanded = !busTypeDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Bus Type")
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { busTypeDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = busTypeDropdownExpanded,
                            onDismissRequest = { busTypeDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(Color.White)
                        ) {
                            busTypeOptions.forEach { typeOption ->
                                DropdownMenuItem(
                                    text = { Text(typeOption, color = Color.Black, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        busType = typeOption
                                        busTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Capacity
                    OutlinedTextField(
                        value = capacityText,
                        onValueChange = { capacityText = it },
                        label = { Text("Capacity (Seats)", color = Color.DarkGray) },
                        singleLine = true,
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Assigned Driver Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = assignedDriver,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned Driver", color = Color.DarkGray) },
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
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unassigned", color = Color.Black, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    assignedDriver = "Unassigned"
                                    driverDropdownExpanded = false
                                }
                            )

                            if (registeredDriverRecords.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No drivers registered in Driver Management", color = Color.Gray, fontSize = 12.sp) },
                                    onClick = { driverDropdownExpanded = false }
                                )
                            } else {
                                registeredDriverRecords.forEach { driverRecord ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(driverRecord.driverName, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                                                Text("License: ${driverRecord.licenseNumber} • ${driverRecord.employmentStatus}", fontSize = 11.sp, color = AdminGreen)
                                            }
                                        },
                                        onClick = {
                                            assignedDriver = driverRecord.driverName
                                            driverDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Assigned Route Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = assignedRoute,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned Route (From Route Management)", color = Color.DarkGray) },
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
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(Color.White)
                        ) {
                            if (availableRoutes.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No routes found in Route Management", color = Color.Gray, fontSize = 12.sp) },
                                    onClick = { routeDropdownExpanded = false }
                                )
                            } else {
                                availableRoutes.forEach { route ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(route.routeName, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                                                Text("Points: ${route.startPoint} ➔ ${route.destination}", fontSize = 11.sp, color = AdminGreen)
                                            }
                                        },
                                        onClick = {
                                            assignedRoute = route.routeName
                                            routeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Bus Status Selection
                    Text("Bus Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)
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
                        if (busCompany.isBlank()) {
                            dialogError = "Please enter bus company name"
                            return@Button
                        }
                        val cap = capacityText.toIntOrNull() ?: 50

                        // Convert picked photo to lightweight Base64 JPEG string (~25KB)
                        // so it is stored directly in Cloud Firestore and syncs across all devices!
                        val finalImg = if (selectedImageUri != null) {
                            val b64 = convertUriToBase64(context, selectedImageUri!!)
                            if (b64.isNotEmpty()) {
                                b64
                            } else {
                                currentImageUrl
                            }
                        } else {
                            currentImageUrl
                        }

                        val updatedBus = Bus(
                            busId = autoGeneratedId,
                            busNumber = busCompany.trim(),
                            capacity = cap,
                            status = selectedStatus,
                            plateNumber = plateNumber.trim(),
                            busType = busType.trim(),
                            assignedDriver = assignedDriver,
                            operator = busCompany.trim(),
                            route = assignedRoute.trim(),
                            imageUrl = finalImg
                        )

                        // Save or update bus directly in Cloud Firestore
                        val existingIndex = busList.indexOfFirst { it.busId == autoGeneratedId }
                        if (existingIndex != -1) {
                            busList[existingIndex] = updatedBus
                        } else {
                            busList.add(0, updatedBus)
                        }

                        DatabaseManager.saveBus(updatedBus)

                        // Sync driver record in Driver Management if a driver was assigned
                        if (assignedDriver != "Unassigned") {
                            val matchedDriver = registeredDriverRecords.find { it.driverName.equals(assignedDriver, ignoreCase = true) }
                            if (matchedDriver != null) {
                                val busLabel = if (updatedBus.busNumber.isNotBlank()) updatedBus.busNumber else "BUS-${updatedBus.busId}"
                                val busPlateLabel = if (updatedBus.plateNumber.isNotBlank()) " (${updatedBus.plateNumber})" else ""
                                DatabaseManager.saveDriverDetails(
                                    driverId = matchedDriver.driverId,
                                    driverName = matchedDriver.driverName,
                                    contactNumber = matchedDriver.contactNumber,
                                    licenseNumber = matchedDriver.licenseNumber,
                                    licenseExpiry = matchedDriver.licenseExpiry,
                                    assignedBus = "$busLabel$busPlateLabel",
                                    employmentStatus = matchedDriver.employmentStatus
                                )
                            }
                        }

                        Toast.makeText(context, "Bus $autoGeneratedId saved to Cloud Database!", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        editingBus = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) {
                    Text("Save Bus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingBus = null
                }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}
