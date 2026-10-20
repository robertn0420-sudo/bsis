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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myfirstapp.BusStop
import com.example.myfirstapp.DatabaseManager
import com.example.myfirstapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Universal Terminal Image component that renders Base64 strings, local files, or fallback logo.
 */
@Composable
fun TerminalImage(
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

data class TerminalLocationSuggestion(
    val title: String,
    val subtitle: String,
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * Real-time Live OpenStreetMap Geocoding Search Engine for Terminal Hubs
 */
suspend fun fetchLiveTerminalSuggestions(query: String): List<TerminalLocationSuggestion> {
    if (query.trim().length < 2) return emptyList()
    return withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=8&countrycodes=ph"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "TransitGoApp/1.0 (Android Native App)")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)

                val results = mutableListOf<TerminalLocationSuggestion>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val displayName = obj.optString("display_name", "")
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)

                    val parts = displayName.split(",")
                    val shortTitle = parts.firstOrNull()?.trim() ?: displayName
                    val remainingSubtitle = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else "OpenStreetMap Real Location"

                    results.add(
                        TerminalLocationSuggestion(
                            title = shortTitle,
                            subtitle = remainingSubtitle,
                            lat = if (lat != 0.0) lat else null,
                            lng = if (lon != 0.0) lon else null
                        )
                    )
                }
                results
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

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

    // Real-time live listener for Terminal Hubs / Bus Stops
    LaunchedEffect(Unit) {
        DatabaseManager.listenToBusStops(
            onUpdate = { fetchedStops ->
                terminalList.clear()
                terminalList.addAll(fetchedStops)
            }
        )
    }

    // Open Interactive Map for a specific terminal
    if (viewingMapTerminal != null) {
        val term = viewingMapTerminal!!
        LeafletMapViewScreen(
            leafletId = term.stopId,
            routeName = term.stopName,
            startPoint = term.stopName,
            destination = term.location,
            startLat = term.latitude,
            startLng = term.longitude,
            isSingleTerminal = true,
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
                placeholder = { Text("Search terminal name, location, or ID...", color = Color.Gray) },
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
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Google Maps Style Terminal Photo Thumbnail
                                    TerminalImage(
                                        imageUrl = terminal.imageUrl,
                                        contentDescription = "Terminal Photo",
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(terminal.stopName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
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

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Terminal ID: ${terminal.stopId}", fontSize = 11.sp, color = Color.Gray)
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Location / Destination Line
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = AdminGreen, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = terminal.location,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

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
                                        Text("View Route Map", color = AdminGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    // ADD OR EDIT TERMINAL MODAL DIALOG WITH PHOTO PICKER & LIVE OPENSTREETMAP AUTOCOMPLETE
    if (showAddDialog || editingTerminal != null) {
        val isEditing = editingTerminal != null
        val target = editingTerminal

        val autoStopId = remember { target?.stopId ?: ("TERM-" + (100..999).random()) }
        var stopName by remember { mutableStateOf(target?.stopName ?: "") }
        var location by remember { mutableStateOf(target?.location ?: "") }

        var detectedLat by remember { mutableStateOf(target?.latitude ?: 14.5995) }
        var detectedLng by remember { mutableStateOf(target?.longitude ?: 120.9842) }

        var baysCountText by remember { mutableStateOf(target?.baysCount?.toString() ?: "8") }
        var activeBaysText by remember { mutableStateOf(target?.activeBays?.toString() ?: "5") }
        var operatingHours by remember { mutableStateOf(target?.operatingHours ?: "04:00 AM - 11:00 PM") }
        var selectedStatus by remember { mutableStateOf(target?.status ?: "Active") }

        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        val currentImageUrl = target?.imageUrl ?: ""

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
            }
        }

        var locationFocused by remember { mutableStateOf(false) }
        var liveLocationSuggestions by remember { mutableStateOf<List<TerminalLocationSuggestion>>(emptyList()) }

        // Live OpenStreetMap Geocoding Search for Terminal Location
        LaunchedEffect(location) {
            if (location.trim().length >= 2) {
                delay(300)
                val fetched = fetchLiveTerminalSuggestions(location)
                liveLocationSuggestions = fetched.distinctBy { it.title }
            } else {
                liveLocationSuggestions = emptyList()
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

                    // Interactive Terminal Image / Photo Picker (Google Maps Style Photo)
                    Text("Terminal Photo", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.DarkGray)
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
                                    contentDescription = "Picked Terminal Photo",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                TerminalImage(
                                    imageUrl = currentImageUrl,
                                    contentDescription = "Current Terminal Photo",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedImageUri != null) "New Terminal Photo Selected" else "Tap to Add/Change Terminal Photo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(text = "Upload Google Maps Style Photo", fontSize = 11.sp, color = Color.Gray)
                            }
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Pick Photo", tint = AdminGreen)
                        }
                    }

                    // Terminal Name Input
                    OutlinedTextField(
                        value = stopName,
                        onValueChange = { stopName = it },
                        label = { Text("Terminal Name", color = Color.DarkGray) },
                        placeholder = { Text("e.g. Central Terminal - Manila", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Address / City Location Input Field with Live OpenStreetMap Autocomplete Search
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OutlinedTextField(
                                value = location,
                                onValueChange = {
                                    location = it
                                    locationFocused = true
                                },
                                label = { Text("Destination / Location Address", color = Color.DarkGray) },
                                placeholder = { Text("Search location e.g. Nasugbu, Palico, Manila", color = Color.Gray) },
                                leadingIcon = { Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = AdminGreen) },
                                trailingIcon = {
                                    if (location.isNotEmpty()) {
                                        IconButton(onClick = { location = ""; locationFocused = false }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Live OpenStreetMap Autocomplete Suggestion Card
                            if (locationFocused && liveLocationSuggestions.isNotEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        liveLocationSuggestions.forEach { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        location = if (suggestion.subtitle.isNotBlank()) "${suggestion.title}, ${suggestion.subtitle}" else suggestion.title
                                                        if (suggestion.lat != null) detectedLat = suggestion.lat
                                                        if (suggestion.lng != null) detectedLng = suggestion.lng
                                                        if (stopName.isBlank()) stopName = suggestion.title
                                                        locationFocused = false
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = Color(0xFFE8F5E9),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = AdminGreen,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(suggestion.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                                    Text(suggestion.subtitle, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                            HorizontalDivider(color = Color(0xFFF0F0F0))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Auto-Detected Coordinates Info Badge (Manual Lat/Lng Inputs Removed)
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = AdminGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GPS Auto-Detected: ${String.format("%.4f", detectedLat)}, ${String.format("%.4f", detectedLng)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdminGreen
                            )
                        }
                    }

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

                        val bCount = baysCountText.toIntOrNull() ?: 8
                        val aBays = activeBaysText.toIntOrNull() ?: 5

                        // Convert picked photo to lightweight Base64 JPEG string (~25KB)
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

                        val newTerminal = BusStop(
                            stopId = autoStopId,
                            stopName = stopName.trim(),
                            location = location.trim(),
                            latitude = detectedLat,
                            longitude = detectedLng,
                            baysCount = bCount,
                            activeBays = aBays,
                            operatingHours = operatingHours.trim(),
                            status = selectedStatus,
                            imageUrl = finalImg
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
