package com.example.myfirstapp

import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myfirstapp.admin.AdminGreen

data class RecentSearchItem(
    val title: String,
    val address: String,
    val status: String = ""
)

// Javascript bridge for Leaflet location picker
class LeafletBridge(
    private val onPick: (String, String, Double, Double) -> Unit
) {
    @JavascriptInterface
    fun onLocationPicked(name: String, address: String, lat: Double, lng: Double) {
        onPick(name, address, lat, lng)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerSearchScreen(
    onBack: () -> Unit = {},
    onDestinationSelected: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    var origin by remember { mutableStateOf("Current Location") }
    var destination by remember { mutableStateOf("") }
    var selectedTransportMode by remember { mutableStateOf("Bus") }
    
    // Track which field is opening the Leaflet map location picker: "Origin" or "Destination" or null
    var pickerTarget by remember { mutableStateOf<String?>(null) }

    val recentSearches = remember {
        listOf(
            RecentSearchItem("Central Terminal - Manila", "Central Manila, Metro Manila", "Open 24 Hours"),
            RecentSearchItem("Hydromed Dialysis Clinic Inc - Tuy", "Provincial Medical Center, Brgy Putol, Tuy, Batangas", "Closed • Opens 8:00 AM"),
            RecentSearchItem("Cubao Bus Station - Quezon City", "EDSA Corner Aurora Blvd, Cubao, QC", "Open • Active Transit"),
            RecentSearchItem("PITX Terminal - Parañaque", "Kennedy Rd, Tambo, Parañaque, Metro Manila", "Open 24 Hours"),
            RecentSearchItem("Philhealth - Nasugbu", "Wawa, Nasugbu, Batangas", "Closed • Opens 7:00 AM"),
            RecentSearchItem("GMQ LEMERY BATANGAS", "Illustre Avenue, Palanas, Lemery, Batangas", "Open 24 Hours")
        )
    }

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

    // Leaflet Map Location Picker Fullscreen Overlay
    if (pickerTarget != null) {
        val target = pickerTarget!!
        val currentVal = if (target == "Origin") origin else destination
        LeafletLocationPickerModal(
            targetType = target,
            initialLocation = currentVal,
            onLocationConfirmed = { selectedName, _, _ ->
                if (target == "Origin") {
                    origin = selectedName
                    Toast.makeText(context, "Origin set to: $selectedName", Toast.LENGTH_SHORT).show()
                } else {
                    destination = selectedName
                    Toast.makeText(context, "Destination set to: $selectedName", Toast.LENGTH_SHORT).show()
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F6))
            .padding(16.dp)
    ) {
        // Top Header
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
            Text("Where to go?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Google Maps style Origin & Destination Input Card with Leaflet Map Picker integrations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location Dots / Pin Indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1976D2),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("┊", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Destination Pin",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Input Text Fields Column
                    Column(modifier = Modifier.weight(1f)) {
                        // Origin Input with Leaflet Map Picker button
                        OutlinedTextField(
                            value = origin,
                            onValueChange = { origin = it },
                            label = { Text("Your location (Origin)", color = Color.DarkGray, fontSize = 12.sp) },
                            singleLine = true,
                            colors = textFieldColors,
                            trailingIcon = {
                                IconButton(onClick = { pickerTarget = "Origin" }) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "Pick Origin on Leaflet Map",
                                        tint = AdminGreen
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Destination Input with Leaflet Map Picker button
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it },
                            label = { Text("Choose final destination", color = Color.DarkGray, fontSize = 12.sp) },
                            placeholder = { Text("Search city, station, or terminal", color = Color.Gray) },
                            singleLine = true,
                            colors = textFieldColors,
                            trailingIcon = {
                                IconButton(onClick = { pickerTarget = "Destination" }) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "Pick Destination on Leaflet Map",
                                        tint = AdminGreen
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Swap Origin ⇄ Destination Button
                    IconButton(onClick = {
                        val temp = origin
                        origin = destination.ifEmpty { "Current Location" }
                        destination = temp
                    }) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap Locations",
                            tint = AdminGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Buttons for Leaflet Map Picker (Choose Origin or Destination on Leaflet Map)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickerTarget = "Origin" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AdminGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminGreen)
                    ) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Origin on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { pickerTarget = "Destination" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AdminGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminGreen)
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Destination on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transport Mode Selector Pills (Bus / Car / Motorcycle / Walking)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                Pair("Bus", Icons.Default.DirectionsBus),
                Pair("Car", Icons.Default.DirectionsCar),
                Pair("Motorcycle", Icons.Default.TwoWheeler),
                Pair("Walking", Icons.AutoMirrored.Filled.DirectionsWalk)
            ).forEach { (mode, icon) ->
                val isSelected = selectedTransportMode == mode
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) AdminGreen else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) AdminGreen else Color.LightGray),
                    modifier = Modifier
                        .clickable { selectedTransportMode = mode }
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode,
                            tint = if (isSelected) Color.White else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Saved Places (Home / Work)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color.LightGray),
                modifier = Modifier.clickable { destination = "Home (Batangas)" }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = AdminGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Home", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color.LightGray),
                modifier = Modifier.clickable { destination = "Workplace" }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Work, contentDescription = "Work", tint = AdminGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Work", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Recent Searches List
        Text("Recent Searches & Terminals", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recentSearches) { searchItem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            destination = searchItem.title
                            onDestinationSelected(origin, searchItem.title)
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0F4F1),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Recent",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(searchItem.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                            Text(searchItem.address, fontSize = 12.sp, color = Color.Gray)
                            if (searchItem.status.isNotEmpty()) {
                                Text(searchItem.status, fontSize = 11.sp, color = AdminGreen, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Search Buses Button
        Button(
            onClick = {
                if (destination.isBlank()) {
                    Toast.makeText(context, "Please enter a destination or pick one on the Leaflet map", Toast.LENGTH_SHORT).show()
                } else {
                    onDestinationSelected(origin, destination)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search Buses & View Route Map", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/**
 * Interactive Leaflet Map Location Picker Component.
 * Allows users to choose an Origin or Final Destination on an OpenStreetMap Leaflet Map
 * by clicking on available bus stop/terminal markers or tapping anywhere on the map to drop a pin.
 */
@Composable
fun LeafletLocationPickerModal(
    targetType: String, // "Origin" or "Destination"
    initialLocation: String,
    onLocationConfirmed: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedName by remember { mutableStateOf(if (initialLocation.isNotBlank() && initialLocation != "Current Location") initialLocation else "Central Terminal - Manila") }
    var selectedAddress by remember { mutableStateOf("Selected Location on Leaflet Map") }
    var selectedLat by remember { mutableDoubleStateOf(14.5995) }
    var selectedLng by remember { mutableDoubleStateOf(120.9842) }
    var isMapLoaded by remember { mutableStateOf(false) }

    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    val leafletPickerHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
            <style>
                body, html, #map { height: 100vh; width: 100vw; margin: 0; padding: 0; background-color: #f5f8f6; }
                .leaflet-popup-content { font-size: 13px; font-weight: bold; color: #116530; }
                .avail-badge { background-color: #116530; color: #ffffff; padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: bold; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                document.addEventListener("DOMContentLoaded", function() {
                    try {
                        var map = L.map('map').setView([14.5995, 120.9842], 11);
                        
                        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            maxZoom: 19,
                            attribution: '© OpenStreetMap'
                        }).addTo(map);

                        var activeMarker = null;

                        var availableLocations = [
                            { name: "Central Terminal - Manila", address: "Central Manila, Metro Manila", lat: 14.5995, lng: 120.9842 },
                            { name: "PITX Terminal - Parañaque", address: "Kennedy Rd, Tambo, Parañaque", lat: 14.5092, lng: 120.9917 },
                            { name: "Cubao Bus Station - Quezon City", address: "EDSA Corner Aurora Blvd, Cubao", lat: 14.6195, lng: 121.0511 },
                            { name: "Airport Hub - NAIA", address: "NAIA Terminal, Pasay, Metro Manila", lat: 14.5204, lng: 121.0122 },
                            { name: "Philhealth - Nasugbu", address: "Wawa, Nasugbu, Batangas", lat: 14.0722, lng: 120.6311 },
                            { name: "Palico Grand Terminal - Nasugbu", address: "2MXX+8G6, Tagaytay - Nasugbu Hwy, Nasugbu, Batangas", lat: 14.0728, lng: 120.6983 },
                            { name: "GMQ LEMERY BATANGAS", address: "Illustre Avenue, Palanas, Lemery", lat: 13.8822, lng: 120.9111 },
                            { name: "Hydromed Dialysis Clinic Inc - Tuy", address: "Brgy Putol, Tuy, Batangas", lat: 14.0200, lng: 120.7300 }
                        ];

                        var greenIcon = L.icon({
                            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
                            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                            iconSize: [25, 41],
                            iconAnchor: [12, 41],
                            popupAnchor: [1, -34],
                            shadowSize: [41, 41]
                        });

                        var redIcon = L.icon({
                            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
                            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                            iconSize: [25, 41],
                            iconAnchor: [12, 41],
                            popupAnchor: [1, -34],
                            shadowSize: [41, 41]
                        });

                        // Render all Available Locations on Map
                        availableLocations.forEach(function(loc) {
                            var m = L.marker([loc.lat, loc.lng], { icon: greenIcon }).addTo(map);
                            m.bindPopup("<b>" + loc.name + "</b><br><span class='avail-badge'>Available Location</span><br>" + loc.address);
                            m.on('click', function() {
                                markSelected(loc.name, loc.address, loc.lat, loc.lng);
                            });
                        });

                        function markSelected(name, address, lat, lng) {
                            if (activeMarker) {
                                map.removeLayer(activeMarker);
                            }
                            activeMarker = L.marker([lat, lng], { icon: redIcon }).addTo(map);
                            activeMarker.bindPopup("<b>Selected $targetType</b><br>" + name).openPopup();
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onLocationPicked(name, address, lat, lng);
                            }
                        }

                        // Select default point
                        markSelected("$selectedName", "Selected on Leaflet OpenStreetMap", 14.5995, 120.9842);

                        // Tap anywhere on Map to pick custom location pin
                        map.on('click', function(e) {
                            var lat = e.latlng.lat.toFixed(4);
                            var lng = e.latlng.lng.toFixed(4);
                            var name = "Picked Location (" + lat + ", " + lng + ")";
                            var addr = "Custom Location on Leaflet Map";
                            markSelected(name, addr, parseFloat(lat), parseFloat(lng));
                        });

                    } catch(e) {
                        console.log(e);
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F6))
    ) {
        // Top Header
        Surface(
            color = AdminGreen,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Choose $targetType on Leaflet Map",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Tap any available terminal marker or tap anywhere on the map",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Leaflet Map Container
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isMapLoaded = true
                            }
                        }
                        addJavascriptInterface(
                            LeafletBridge { name, address, lat, lng ->
                                handler.post {
                                    selectedName = name
                                    selectedAddress = address
                                    selectedLat = lat
                                    selectedLng = lng
                                }
                            },
                            "AndroidBridge"
                        )
                        loadDataWithBaseURL("https://leafletjs.com", leafletPickerHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isMapLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AdminGreen)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading Leaflet OpenStreetMap Location Picker...", fontSize = 13.sp, color = AdminGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Selected Location Action Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (targetType == "Origin") Color(0xFFE3F2FD) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Selecting $targetType",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (targetType == "Origin") Color(0xFF1976D2) else Color(0xFFD32F2F),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Lat: %.4f, Lng: %.4f".format(selectedLat, selectedLng),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (targetType == "Origin") Icons.Default.MyLocation else Icons.Default.Place,
                        contentDescription = null,
                        tint = if (targetType == "Origin") Color(0xFF1976D2) else Color(0xFFD32F2F),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(selectedName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                        Text(selectedAddress, fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedName = "Current Location (GPS)"
                            selectedAddress = "Your Current GPS Location"
                            onLocationConfirmed("Current Location", selectedLat, selectedLng)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Use GPS", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onLocationConfirmed(selectedName, selectedLat, selectedLng)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as $targetType", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
