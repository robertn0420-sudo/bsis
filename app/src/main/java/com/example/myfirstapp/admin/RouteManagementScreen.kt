package com.example.myfirstapp.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapVert
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
import com.example.myfirstapp.BusRoute
import com.example.myfirstapp.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LeafletPickerInterface(private val onPicked: (String, String) -> Unit) {
    @JavascriptInterface
    fun onLocationsPicked(origin: String, destination: String) {
        onPicked(origin, destination)
    }
}

data class LocationSuggestion(
    val title: String,
    val subtitle: String,
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * Real-time Live OpenStreetMap Nominatim Geocoding Search Engine
 */
suspend fun fetchLiveOpenStreetMapSuggestions(query: String): List<LocationSuggestion> {
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

                val results = mutableListOf<LocationSuggestion>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val displayName = obj.optString("display_name", "")
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)

                    val parts = displayName.split(",")
                    val shortTitle = parts.firstOrNull()?.trim() ?: displayName
                    val remainingSubtitle = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else "OpenStreetMap Real Location"

                    results.add(
                        LocationSuggestion(
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
fun RouteManagementScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<BusRoute?>(null) }
    var trackingRoute by remember { mutableStateOf<BusRoute?>(null) }
    var routeToDelete by remember { mutableStateOf<BusRoute?>(null) }

    val initialDefaultRoutes = remember {
        listOf(
            BusRoute("R-101", "Route A01: City Terminal ➔ Airport Hub", "Central Terminal - Manila", "Airport Hub - NAIA Pasay", "Active", 14.5995, 120.9842, 14.5204, 121.0122),
            BusRoute("R-102", "Route B02: Cubao Station ➔ PITX Express", "Cubao Bus Station", "PITX Terminal", "Active", 14.6195, 121.0511, 14.5092, 120.9917),
            BusRoute("R-103", "Route C03: Palico Terminal ➔ GMQ Lemery", "Palico Grand Terminal", "GMQ LEMERY BATANGAS", "Active", 14.0728, 120.6983, 13.8822, 120.9111)
        )
    }

    val routeList = remember { mutableStateListOf<BusRoute>() }

    // Live sync directly with Cloud Firestore database
    LaunchedEffect(Unit) {
        DatabaseManager.listenToRoutes(
            onUpdate = { fetchedRoutes ->
                routeList.clear()
                routeList.addAll(fetchedRoutes)
            }
        )
    }

    if (trackingRoute != null) {
        val targetRoute = trackingRoute!!
        LeafletMapViewScreen(
            leafletId = targetRoute.routeId,
            routeName = targetRoute.routeName,
            startPoint = targetRoute.startPoint,
            destination = targetRoute.destination,
            startLat = if (targetRoute.startLat != 0.0) targetRoute.startLat else null,
            startLng = if (targetRoute.startLng != 0.0) targetRoute.startLng else null,
            destLat = if (targetRoute.destLat != 0.0) targetRoute.destLat else null,
            destLng = if (targetRoute.destLng != 0.0) targetRoute.destLng else null,
            onBack = { trackingRoute = null }
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AdminGreen,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Route")
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
                Text("Route Management", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (routeList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Routes Found", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Tap + button below to create a route", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(routeList) { route ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRoute = route },
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
                                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                        contentDescription = "Route Icon",
                                        tint = AdminGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = route.routeId,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = AdminGreen
                                    )
                                }

                                Surface(
                                    color = if (route.status == "Active") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = route.status,
                                        fontSize = 11.sp,
                                        color = if (route.status == "Active") AdminGreen else Color(0xFFC62828),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = route.routeName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Origin: ${route.startPoint}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Destination: ${route.destination}", fontSize = 13.sp, color = Color.DarkGray)

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { trackingRoute = route },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Map, contentDescription = "View Map", tint = AdminGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View Route Map", fontSize = 12.sp, color = AdminGreen, fontWeight = FontWeight.Bold)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingRoute = route },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Route", tint = AdminGreen, modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

                                    IconButton(
                                        onClick = { routeToDelete = route },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Route", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Switch(
                                        checked = route.status == "Active",
                                        onCheckedChange = { isChecked ->
                                            val updatedStatus = if (isChecked) "Active" else "Inactive"
                                            val index = routeList.indexOf(route)
                                            if (index != -1) {
                                                val updatedRoute = BusRoute(
                                                    routeId = route.routeId,
                                                    routeName = route.routeName,
                                                    startPoint = route.startPoint,
                                                    destination = route.destination,
                                                    status = updatedStatus,
                                                    startLat = route.startLat,
                                                    startLng = route.startLng,
                                                    destLat = route.destLat,
                                                    destLng = route.destLng
                                                )
                                                routeList[index] = updatedRoute
                                                DatabaseManager.saveBusRoute(updatedRoute)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = AdminGreen, checkedTrackColor = Color(0xFFE8F5E9))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DELETE ROUTE CONFIRMATION DIALOG
    if (routeToDelete != null) {
        val target = routeToDelete!!
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text("Delete Route", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
            },
            text = {
                Text("Are you sure you want to delete ${target.routeName} (${target.routeId})? This action will permanently remove the route from Cloud Firestore.", fontSize = 14.sp, color = Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        DatabaseManager.deleteBusRoute(
                            routeId = target.routeId,
                            onSuccess = {
                                Toast.makeText(context, "Route ${target.routeId} deleted!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        routeList.remove(target)
                        routeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // CREATE OR EDIT ROUTE MODAL DIALOG WITH EMBEDDED LEAFLET MAP PICKER
    if (showAddDialog || editingRoute != null) {
        val isEditing = editingRoute != null
        val autoRouteId = remember { editingRoute?.routeId ?: ("R-" + (104..999).random()) }
        var startPoint by remember { mutableStateOf(editingRoute?.startPoint ?: "City Terminal") }
        var destination by remember { mutableStateOf(editingRoute?.destination ?: "Airport Hub") }
        var routeName by remember { mutableStateOf(editingRoute?.routeName ?: "Route A01: $startPoint ➔ $destination") }
        var isActive by remember { mutableStateOf(editingRoute?.status != "Inactive") }

        var startPointLat by remember { mutableStateOf(editingRoute?.startLat ?: 0.0) }
        var startPointLng by remember { mutableStateOf(editingRoute?.startLng ?: 0.0) }
        var destPointLat by remember { mutableStateOf(editingRoute?.destLat ?: 0.0) }
        var destPointLng by remember { mutableStateOf(editingRoute?.destLng ?: 0.0) }

        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var dialogError by remember { mutableStateOf("") }

        var startPointFocused by remember { mutableStateOf(false) }
        var destinationFocused by remember { mutableStateOf(false) }

        var liveOriginSuggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
        var liveDestSuggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }

        // Live OpenStreetMap Real Geocoding Search for Origin
        LaunchedEffect(startPoint) {
            if (startPoint.trim().length >= 2) {
                delay(300)
                val fetched = fetchLiveOpenStreetMapSuggestions(startPoint)
                liveOriginSuggestions = fetched.distinctBy { it.title }
            } else {
                liveOriginSuggestions = emptyList()
            }
        }

        // Live OpenStreetMap Real Geocoding Search for Destination
        LaunchedEffect(destination) {
            if (destination.trim().length >= 2) {
                delay(300)
                val fetched = fetchLiveOpenStreetMapSuggestions(destination)
                liveDestSuggestions = fetched.distinctBy { it.title }
            } else {
                liveDestSuggestions = emptyList()
            }
        }

        // Live update Leaflet map markers on screen as user types origin or destination
        LaunchedEffect(startPoint, destination, webViewRef) {
            val cleanStart = startPoint.replace("'", "\\'")
            val cleanDest = destination.replace("'", "\\'")
            webViewRef?.evaluateJavascript(
                "if (window.updateDynamicLocation) { window.updateDynamicLocation('$cleanStart', '$cleanDest'); }",
                null
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

        // HTML Leaflet Map Picker Code supporting dynamic real-time location updating
        val leafletPickerHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
                <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
                <style>
                    body, html, #map { height: 190px; width: 100%; margin: 0; padding: 0; border-radius: 8px; }
                    .orig-popup { font-size: 11px; font-weight: bold; color: #1976D2; }
                    .dest-popup { font-size: 11px; font-weight: bold; color: #D32F2F; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = null;
                    var origMarker = null;
                    var destMarker = null;
                    var line = null;

                    var knownLocations = {
                        "city terminal": [14.5995, 120.9842],
                        "central manila": [14.5995, 120.9842],
                        "manila central": [14.5995, 120.9842],
                        "manila": [14.5995, 120.9842],
                        "airport hub": [14.5204, 121.0122],
                        "naia": [14.5204, 121.0122],
                        "cubao station": [14.6195, 121.0511],
                        "cubao": [14.6195, 121.0511],
                        "pitx express": [14.5092, 120.9917],
                        "pitx": [14.5092, 120.9917],
                        "parañaque": [14.5092, 120.9917],
                        "nasugbu": [14.0722, 120.6311],
                        "philhealth nasugbu": [14.0722, 120.6311],
                        "palico grand terminal": [14.0728, 120.6983],
                        "palico terminal": [14.0728, 120.6983],
                        "palico nasugbu": [14.0728, 120.6983],
                        "palico": [14.0728, 120.6983],
                        "lemery": [13.8822, 120.9111],
                        "gmq lemery": [13.8822, 120.9111],
                        "tuy": [14.0200, 120.7300],
                        "hydromed tuy": [14.0200, 120.7300],
                        "batangas": [13.7565, 121.0583],
                        "quezon city": [14.6760, 121.0437]
                    };

                    var blueIcon = L.icon({
                        iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
                        shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                        iconSize: [22, 36],
                        iconAnchor: [11, 36],
                        popupAnchor: [1, -30],
                        shadowSize: [36, 36]
                    });

                    var redIcon = L.icon({
                        iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
                        shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                        iconSize: [22, 36],
                        iconAnchor: [11, 36],
                        popupAnchor: [1, -30],
                        shadowSize: [36, 36]
                    });

                    document.addEventListener("DOMContentLoaded", function() {
                        try {
                            map = L.map('map').setView([14.5995, 120.9842], 11);
                            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18 }).addTo(map);

                            map.on('click', function(e) {
                                var lat = e.latlng.lat.toFixed(4);
                                var lng = e.latlng.lng.toFixed(4);

                                if (!origMarker) {
                                    origMarker = L.marker(e.latlng, { icon: blueIcon }).addTo(map)
                                        .bindPopup("<div class='orig-popup'><b>Origin Point</b><br>(" + lat + ", " + lng + ")</div>").openPopup();
                                    var origName = "Terminal (" + lat + ", " + lng + ")";
                                    if (window.Android) window.Android.onLocationsPicked(origName, "");
                                } else if (!destMarker) {
                                    destMarker = L.marker(e.latlng, { icon: redIcon }).addTo(map)
                                        .bindPopup("<div class='dest-popup'><b>Destination Point</b><br>(" + lat + ", " + lng + ")</div>").openPopup();
                                    line = L.polyline([origMarker.getLatLng(), destMarker.getLatLng()], {color: '#116530', weight: 5}).addTo(map);
                                    map.fitBounds(line.getBounds(), { padding: [20, 20] });

                                    var origName = "Terminal (" + origMarker.getLatLng().lat.toFixed(4) + ", " + origMarker.getLatLng().lng.toFixed(4) + ")";
                                    var destName = "Hub (" + destMarker.getLatLng().lat.toFixed(4) + ", " + destMarker.getLatLng().lng.toFixed(4) + ")";
                                    if (window.Android) window.Android.onLocationsPicked(origName, destName);
                                } else {
                                    map.removeLayer(origMarker);
                                    map.removeLayer(destMarker);
                                    if (line) map.removeLayer(line);
                                    origMarker = L.marker(e.latlng, { icon: blueIcon }).addTo(map)
                                        .bindPopup("<div class='orig-popup'><b>Origin Point</b><br>(" + lat + ", " + lng + ")</div>").openPopup();
                                    destMarker = null;
                                    line = null;
                                    var origName = "Terminal (" + lat + ", " + lng + ")";
                                    if (window.Android) window.Android.onLocationsPicked(origName, "");
                                }
                            });
                        } catch(e) { console.log(e); }
                    });

                    function getCoordsForText(text, isDest) {
                        if (!text || text.trim().length === 0) return null;
                        var lower = text.trim().toLowerCase();
                        for (var key in knownLocations) {
                            if (lower.includes(key)) {
                                return knownLocations[key];
                            }
                        }
                        var hash = 0;
                        for (var i = 0; i < text.length; i++) hash = text.charCodeAt(i) + ((hash << 5) - hash);
                        var latOffset = ((hash % 100) / 1000.0);
                        var lngOffset = (((hash / 100) % 100) / 1000.0);
                        var baseLat = isDest ? 14.5092 : 14.5995;
                        var baseLng = isDest ? 120.9917 : 120.9842;
                        return [baseLat + latOffset, baseLng + lngOffset];
                    }

                    window.updateDynamicLocation = function(origText, destText) {
                        if (!map) return;

                        var origCoords = getCoordsForText(origText, false);
                        var destCoords = getCoordsForText(destText, true);

                        if (origCoords) {
                            if (origMarker) map.removeLayer(origMarker);
                            origMarker = L.marker(origCoords, { icon: blueIcon }).addTo(map)
                                .bindPopup("<div class='orig-popup'><b>Origin:</b> " + origText + "</div>");
                        }

                        if (destCoords) {
                            if (destMarker) map.removeLayer(destMarker);
                            destMarker = L.marker(destCoords, { icon: redIcon }).addTo(map)
                                .bindPopup("<div class='dest-popup'><b>Destination:</b> " + destText + "</div>");
                        }

                        if (line) map.removeLayer(line);

                        if (origMarker && destMarker) {
                            line = L.polyline([origMarker.getLatLng(), destMarker.getLatLng()], { color: '#116530', weight: 5, opacity: 0.85 }).addTo(map);
                            map.fitBounds(line.getBounds(), { padding: [25, 25] });
                        } else if (origMarker) {
                            map.setView(origMarker.getLatLng(), 13);
                        } else if (destMarker) {
                            map.setView(destMarker.getLatLng(), 13);
                        }
                    };
                </script>
            </body>
            </html>
        """.trimIndent()

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingRoute = null
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text(if (isEditing) "Edit Route" else "Create New Route", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Route ID Badge
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Route ID: $autoRouteId",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AdminGreen
                        )
                    }

                    // Interactive Leaflet Map Picker Component with Dynamic Real-time Location Sync
                    Text("Dynamic Leaflet Map (Live Updates as You Type)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AdminGreen)
                    Surface(
                        color = Color(0xFFF5F8F6),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    webChromeClient = WebChromeClient()
                                    webViewClient = WebViewClient()
                                    addJavascriptInterface(
                                        LeafletPickerInterface { pickedOrigin, pickedDest ->
                                            if (pickedOrigin.isNotEmpty()) startPoint = pickedOrigin
                                            if (pickedDest.isNotEmpty()) {
                                                destination = pickedDest
                                                routeName = "Route: $pickedOrigin ➔ $pickedDest"
                                            }
                                        },
                                        "Android"
                                    )
                                    loadDataWithBaseURL("https://leafletjs.com", leafletPickerHtml, "text/html", "UTF-8", null)
                                    webViewRef = this
                                }
                            },
                            update = { webView ->
                                webViewRef = webView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text("Type origin & destination or tap 2 points directly on the map", fontSize = 11.sp, color = Color.Gray)

                    // Origin / Start Point Input Field with Live OpenStreetMap Autocomplete
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OutlinedTextField(
                                value = startPoint,
                                onValueChange = {
                                    startPoint = it
                                    startPointFocused = true
                                    destinationFocused = false
                                    if (routeName.startsWith("Route:")) {
                                        routeName = "Route: $startPoint ➔ $destination"
                                    }
                                },
                                label = { Text("Origin / Start Point", color = Color.DarkGray) },
                                placeholder = { Text("Search location e.g. Nasugbu, Palico, Manila", color = Color.Gray) },
                                leadingIcon = { Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1976D2)) },
                                trailingIcon = {
                                    if (startPoint.isNotEmpty()) {
                                        IconButton(onClick = { startPoint = ""; startPointFocused = false }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Live OpenStreetMap / OpenRouteService Search Autocomplete Suggestion Card
                            if (startPointFocused && liveOriginSuggestions.isNotEmpty()) {
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
                                        liveOriginSuggestions.forEach { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        startPoint = suggestion.title
                                                        if (suggestion.lat != null) startPointLat = suggestion.lat
                                                        if (suggestion.lng != null) startPointLng = suggestion.lng
                                                        startPointFocused = false
                                                        routeName = "Route: ${suggestion.title} ➔ $destination"
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = Color(0xFFE3F2FD),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = Color(0xFF1976D2),
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

                    // Swap / Reverse Locations Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                val tempText = startPoint
                                startPoint = destination
                                destination = tempText

                                val tempLat = startPointLat
                                val tempLng = startPointLng
                                startPointLat = destPointLat
                                startPointLng = destPointLng
                                destPointLat = tempLat
                                destPointLng = tempLng

                                startPointFocused = false
                                destinationFocused = false
                                routeName = "Route: $startPoint ➔ $destination"
                            },
                            modifier = Modifier
                                .background(Color(0xFFE8F5E9), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Swap Locations",
                                tint = AdminGreen
                            )
                        }
                    }

                    // Destination Point Input Field with Live OpenStreetMap Autocomplete
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OutlinedTextField(
                                value = destination,
                                onValueChange = {
                                    destination = it
                                    destinationFocused = true
                                    startPointFocused = false
                                    if (routeName.startsWith("Route:")) {
                                        routeName = "Route: $startPoint ➔ $destination"
                                    }
                                },
                                label = { Text("Destination Point", color = Color.DarkGray) },
                                placeholder = { Text("Search location e.g. Palico, Lemery, PITX", color = Color.Gray) },
                                leadingIcon = { Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = null, tint = Color(0xFFD32F2F)) },
                                trailingIcon = {
                                    if (destination.isNotEmpty()) {
                                        IconButton(onClick = { destination = ""; destinationFocused = false }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Live OpenStreetMap / OpenRouteService Search Autocomplete Suggestion Card
                            if (destinationFocused && liveDestSuggestions.isNotEmpty()) {
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
                                        liveDestSuggestions.forEach { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        destination = suggestion.title
                                                        if (suggestion.lat != null) destPointLat = suggestion.lat
                                                        if (suggestion.lng != null) destPointLng = suggestion.lng
                                                        destinationFocused = false
                                                        routeName = "Route: $startPoint ➔ ${suggestion.title}"
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = Color(0xFFFFEBEE),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.DirectionsBus,
                                                            contentDescription = null,
                                                            tint = Color(0xFFD32F2F),
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

                    // Route Name Input Field
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text("Route Name", color = Color.DarkGray) },
                        placeholder = { Text("e.g. Route A01: City Terminal ➔ Airport Hub", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Route Active Toggle Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isActive) "Route Status: Active" else "Route Status: Inactive",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isActive) AdminGreen else Color.DarkGray
                        )
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AdminGreen, checkedTrackColor = Color(0xFFE8F5E9))
                        )
                    }

                    if (dialogError.isNotEmpty()) {
                        Text(dialogError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (routeName.isBlank()) {
                            dialogError = "Please enter route name"
                            return@Button
                        }

                        val updatedRoute = BusRoute(
                            routeId = autoRouteId,
                            routeName = routeName.trim(),
                            startPoint = startPoint.trim(),
                            destination = destination.trim(),
                            status = if (isActive) "Active" else "Inactive",
                            startLat = startPointLat,
                            startLng = startPointLng,
                            destLat = destPointLat,
                            destLng = destPointLng
                        )

                        // Save Route directly to Cloud Firestore
                        DatabaseManager.saveBusRoute(updatedRoute)

                        Toast.makeText(context, "Route $autoRouteId saved to Cloud Database!", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        editingRoute = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                ) {
                    Text("Save Route", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingRoute = null
                }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}
}
