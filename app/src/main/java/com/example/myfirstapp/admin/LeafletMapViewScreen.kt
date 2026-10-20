package com.example.myfirstapp.admin

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Store
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
import java.io.File

@Composable
fun LeafletMapViewScreen(
    leafletId: String = "L001",
    routeName: String = "",
    startPoint: String = "",
    destination: String = "",
    startLat: Double? = null,
    startLng: Double? = null,
    destLat: Double? = null,
    destLng: Double? = null,
    isSingleTerminal: Boolean = false,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isMapLoaded by remember { mutableStateOf(false) }

    // Clean Start Point
    val cleanStart = remember(startPoint, routeName) {
        if (startPoint.isNotBlank()) {
            startPoint.trim()
        } else if (routeName.contains(" ➔ ")) {
            val raw = routeName.split(" ➔ ")[0].trim()
            raw.replace(Regex("""^Route\s+[A-Za-z0-9-]+:\s*""", RegexOption.IGNORE_CASE), "").trim()
        } else if (routeName.contains(" - ")) {
            val raw = routeName.split(" - ")[0].trim()
            raw.replace(Regex("""^Route\s+[A-Za-z0-9-]+:\s*""", RegexOption.IGNORE_CASE), "").trim()
        } else if (routeName.isNotBlank()) {
            routeName.trim()
        } else {
            "Central Terminal"
        }
    }

    // Clean Destination Point
    val cleanDest = remember(destination, routeName) {
        if (destination.isNotBlank()) {
            destination.trim()
        } else if (routeName.contains(" ➔ ")) {
            routeName.split(" ➔ ")[1].trim()
        } else if (routeName.contains(" - ")) {
            routeName.split(" - ")[1].trim()
        } else {
            "Airport Hub"
        }
    }

    val cleanStartJS = remember(cleanStart) { cleanStart.replace("'", "\\'") }
    val cleanDestJS = remember(cleanDest) { cleanDest.replace("'", "\\'") }

    val startLatJS = if (startLat != null) startLat.toString() else "null"
    val startLngJS = if (startLng != null) startLng.toString() else "null"
    val destLatJS = if (destLat != null) destLat.toString() else "null"
    val destLngJS = if (destLng != null) destLng.toString() else "null"

    val leafletHtmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body {
                    height: 100%; width: 100%;
                    overflow: hidden; background-color: #f0f7f2;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                }
                #map-wrapper {
                    position: relative;
                    width: 100%;
                    height: 100vh;
                }
                #canvas-layer {
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 100%;
                    z-index: 1;
                }
                #map-layer {
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 100%;
                    z-index: 2;
                }
                .map-badge {
                    position: absolute;
                    top: 12px; left: 12px;
                    z-index: 1000;
                    background: rgba(255, 255, 255, 0.95);
                    padding: 8px 14px;
                    border-radius: 12px;
                    box-shadow: 0 3px 10px rgba(0,0,0,0.18);
                    font-size: 13px;
                    font-weight: bold;
                    color: #116530;
                    border: 1.5px solid #116530;
                }
                .leaflet-popup-content {
                    font-size: 13px;
                    font-weight: bold;
                    color: #116530;
                }
            </style>
        </head>
        <body>
            <div id="map-wrapper">
                <div class="map-badge">${if (isSingleTerminal) "🏢 Terminal Hub: $cleanStartJS" else "🛣️ Driving Route: $cleanStartJS ➔ $cleanDestJS"}</div>
                <canvas id="canvas-layer"></canvas>
                <div id="map-layer"></div>
            </div>
            <script>
                var cleanStartName = "$cleanStartJS";
                var cleanDestName = "$cleanDestJS";
                var isSingleMode = $isSingleTerminal;

                var paramStartLat = $startLatJS;
                var paramStartLng = $startLngJS;
                var paramDestLat = $destLatJS;
                var paramDestLng = $destLngJS;

                var knownLocations = {
                    "city terminal": [14.5995, 120.9842],
                    "central terminal": [14.5995, 120.9842],
                    "central manila": [14.5995, 120.9842],
                    "manila central": [14.5995, 120.9842],
                    "manila": [14.5995, 120.9842],
                    "airport hub": [14.5204, 121.0122],
                    "naia": [14.5204, 121.0122],
                    "pasay": [14.5378, 120.9992],
                    "cubao station": [14.6195, 121.0511],
                    "cubao": [14.6195, 121.0511],
                    "quezon city": [14.6760, 121.0437],
                    "pitx express": [14.5092, 120.9917],
                    "pitx": [14.5092, 120.9917],
                    "parañaque": [14.5092, 120.9917],
                    "regional routes": [14.5204, 121.0122],
                    "regional": [14.5204, 121.0122],
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
                    "city center": [14.5800, 121.0000],
                    "makati": [14.5547, 121.0244],
                    "taguig": [14.5176, 121.0509],
                    "alabang": [14.4172, 121.0400],
                    "muntinlupa": [14.4081, 121.0415],
                    "cavite": [14.4791, 120.8970],
                    "bacoor": [14.4602, 120.9631],
                    "dasmarinas": [14.3294, 120.9367]
                };

                function getCoordsForText(text, isDest) {
                    if (!text || text.trim().length === 0) {
                        return isDest ? [14.5204, 121.0122] : [14.5995, 120.9842];
                    }
                    var lower = text.trim().toLowerCase();
                    for (var key in knownLocations) {
                        if (lower.includes(key)) {
                            return knownLocations[key];
                        }
                    }
                    var hash = 0;
                    for (var i = 0; i < text.length; i++) {
                        hash = text.charCodeAt(i) + ((hash << 5) - hash);
                    }
                    var latOffset = ((Math.abs(hash) % 150) / 1000.0) * (isDest ? -1 : 1);
                    var lngOffset = (((Math.abs(hash) / 150) % 150) / 1000.0) * (isDest ? 1 : -1);
                    var baseLat = isDest ? 14.5092 : 14.5995;
                    var baseLng = isDest ? 120.9917 : 120.9842;
                    return [baseLat + latOffset, baseLng + lngOffset];
                }

                var startCoords = (paramStartLat !== null && paramStartLng !== null) ? [paramStartLat, paramStartLng] : getCoordsForText(cleanStartName, false);
                var destCoords = (paramDestLat !== null && paramDestLng !== null) ? [paramDestLat, paramDestLng] : getCoordsForText(cleanDestName, true);

                // --- LAYER 1: Immediate Self-Contained Vector Canvas Map Engine ---
                function drawVectorMap() {
                    var cvs = document.getElementById('canvas-layer');
                    if (!cvs) return;
                    var ctx = cvs.getContext('2d');
                    var w = cvs.width = window.innerWidth || document.documentElement.clientWidth || 360;
                    var h = cvs.height = window.innerHeight || document.documentElement.clientHeight || 600;

                    ctx.clearRect(0, 0, w, h);

                    // Background tint
                    ctx.fillStyle = '#f0f7f2';
                    ctx.fillRect(0, 0, w, h);

                    // Draw Road Grid / Map Lines
                    ctx.strokeStyle = '#d3e4d7';
                    ctx.lineWidth = 1.5;
                    for (var x = 0; x < w; x += 40) {
                        ctx.beginPath();
                        ctx.moveTo(x, 0);
                        ctx.lineTo(x, h);
                        ctx.stroke();
                    }
                    for (var y = 0; y < h; y += 40) {
                        ctx.beginPath();
                        ctx.moveTo(0, y);
                        ctx.lineTo(w, y);
                        ctx.stroke();
                    }

                    if (isSingleMode) {
                        // Single Terminal Pin Centered
                        var px = w * 0.5;
                        var py = h * 0.5;

                        ctx.fillStyle = '#116530';
                        ctx.beginPath();
                        ctx.arc(px, py, 16, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.strokeStyle = '#FFFFFF';
                        ctx.lineWidth = 3.5;
                        ctx.stroke();

                        ctx.fillStyle = '#116530';
                        ctx.font = 'bold 12px sans-serif';
                        ctx.textAlign = 'center';
                        ctx.fillRect(px - 90, py - 42, 180, 24);
                        ctx.fillStyle = '#FFFFFF';
                        ctx.fillText('🏢 ' + cleanStartName, px, py - 25);
                    } else {
                        // 2-Point Route
                        var p1 = { x: w * 0.28, y: h * 0.35 };
                        var p2 = { x: w * 0.72, y: h * 0.65 };
                        var pMid = { x: (p1.x + p2.x) / 2 + 35, y: (p1.y + p2.y) / 2 - 45 };

                        ctx.strokeStyle = '#116530';
                        ctx.lineWidth = 6;
                        ctx.setLineDash([10, 6]);
                        ctx.beginPath();
                        ctx.moveTo(p1.x, p1.y);
                        ctx.quadraticCurveTo(pMid.x, pMid.y, p2.x, p2.y);
                        ctx.stroke();
                        ctx.setLineDash([]);

                        ctx.fillStyle = '#1976D2';
                        ctx.beginPath();
                        ctx.arc(p1.x, p1.y, 14, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.strokeStyle = '#FFFFFF';
                        ctx.lineWidth = 3;
                        ctx.stroke();

                        ctx.fillStyle = '#1976D2';
                        ctx.font = 'bold 12px sans-serif';
                        ctx.textAlign = 'center';
                        ctx.fillRect(p1.x - 65, p1.y - 38, 130, 22);
                        ctx.fillStyle = '#FFFFFF';
                        ctx.fillText('📍 ' + cleanStartName, p1.x, p1.y - 23);

                        ctx.fillStyle = '#D32F2F';
                        ctx.beginPath();
                        ctx.arc(p2.x, p2.y, 14, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.strokeStyle = '#FFFFFF';
                        ctx.lineWidth = 3;
                        ctx.stroke();

                        ctx.fillStyle = '#D32F2F';
                        ctx.fillRect(p2.x - 65, p2.y + 16, 130, 22);
                        ctx.fillStyle = '#FFFFFF';
                        ctx.fillText('🏁 ' + cleanDestName, p2.x, p2.y + 31);
                    }

                    // Compass Rose Overlay
                    ctx.fillStyle = '#116530';
                    ctx.font = 'bold 11px sans-serif';
                    ctx.fillText('🧭 N', w - 30, 30);
                    ctx.fillText('📍 GPS Map Active', w - 60, h - 25);
                }

                drawVectorMap();
                window.addEventListener('resize', drawVectorMap);

                // --- LAYER 2: Leaflet Tile Engine ---
                function initLeafletEngine() {
                    if (typeof L === 'undefined') {
                        setTimeout(initLeafletEngine, 150);
                        return;
                    }

                    try {
                        var mapElem = document.getElementById('map-layer');
                        if (!mapElem) return;

                        mapElem.style.zIndex = "10";

                        if (isSingleMode) {
                            // Single Terminal Hub Mode (1 Pin Only)
                            var map = L.map('map-layer', { zoomControl: true }).setView(startCoords, 15);

                            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                maxZoom: 19,
                                attribution: '© OpenStreetMap'
                            }).addTo(map);

                            var terminalIcon = L.divIcon({
                                className: 'custom-term-icon',
                                html: "<div style='background:#116530;color:white;padding:8px 14px;border-radius:18px;font-weight:bold;font-size:13px;border:2.5px solid white;box-shadow:0 4px 10px rgba(0,0,0,0.4);white-space:nowrap;'>🏢 " + cleanStartName + "</div>",
                                iconSize: [160, 36],
                                iconAnchor: [80, 18]
                            });

                            L.marker(startCoords, { icon: terminalIcon }).addTo(map)
                                .bindPopup("<b>" + cleanStartName + "</b><br><span style='color:#116530;'>Terminal Hub • " + cleanDestName + "</span>").openPopup();
                        } else {
                            // Two-Point Driving Route Mode
                            var map = L.map('map-layer', { zoomControl: true }).setView(startCoords, 12);

                            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                maxZoom: 19,
                                attribution: '© OpenStreetMap'
                            }).addTo(map);

                            var blueIcon = L.divIcon({
                                className: 'custom-orig-icon',
                                html: "<div style='background:#1976D2;color:white;padding:6px 12px;border-radius:16px;font-weight:bold;font-size:12px;border:2px solid white;box-shadow:0 3px 8px rgba(0,0,0,0.35);white-space:nowrap;'>📍 " + cleanStartName + "</div>",
                                iconSize: [140, 32],
                                iconAnchor: [70, 16]
                            });

                            var redIcon = L.divIcon({
                                className: 'custom-dest-icon',
                                html: "<div style='background:#D32F2F;color:white;padding:6px 12px;border-radius:16px;font-weight:bold;font-size:12px;border:2px solid white;box-shadow:0 3px 8px rgba(0,0,0,0.35);white-space:nowrap;'>🏁 " + cleanDestName + "</div>",
                                iconSize: [140, 32],
                                iconAnchor: [70, 16]
                            });

                            L.marker(startCoords, { icon: blueIcon }).addTo(map)
                                .bindPopup("<b>" + cleanStartName + "</b><br><span style='color:#1976D2'>Origin Terminal Point</span>");

                            L.marker(destCoords, { icon: redIcon }).addTo(map)
                                .bindPopup("<b>" + cleanDestName + "</b><br><span style='color:#D32F2F'>Final Destination Point</span>");

                            // OSRM Driving Engine Route
                            var osrmUrl = "https://router.project-osrm.org/route/v1/driving/" +
                                startCoords[1] + "," + startCoords[0] + ";" +
                                destCoords[1] + "," + destCoords[0] +
                                "?overview=full&geometries=geojson";

                            fetch(osrmUrl)
                                .then(function(res) { return res.json(); })
                                .then(function(data) {
                                    if (data && data.routes && data.routes.length > 0) {
                                        var routeGeometry = data.routes[0].geometry;
                                        var drivingPolyline = L.geoJSON(routeGeometry, {
                                            style: { color: '#116530', weight: 6, opacity: 0.9, lineCap: 'round', lineJoin: 'round' }
                                        }).addTo(map);

                                        map.fitBounds(drivingPolyline.getBounds(), { padding: [50, 50] });

                                        var distKm = (data.routes[0].distance / 1000.0).toFixed(1);
                                        var durationMin = Math.round(data.routes[0].duration / 60.0);
                                        var durationText = durationMin >= 60 ? Math.floor(durationMin / 60) + "h " + (durationMin % 60) + "m" : durationMin + " mins";

                                        var badgeElem = document.querySelector('.map-badge');
                                        if (badgeElem) {
                                            badgeElem.innerHTML = "🛣️ Real Driving Route: " + cleanStartName + " ➔ " + cleanDestName + "<br><span style='font-size:11px;color:#1976D2;'>Distance: " + distKm + " km • Est. Drive Time: " + durationText + "</span>";
                                        }
                                    } else {
                                        drawFallbackPolyline(map, startCoords, destCoords);
                                    }
                                })
                                .catch(function(e) {
                                    drawFallbackPolyline(map, startCoords, destCoords);
                                });
                        }
                    } catch(e) {
                        console.log(e);
                    }
                }

                function drawFallbackPolyline(map, start, dest) {
                    var midLat = (start[0] + dest[0]) / 2.0 + 0.015;
                    var midLng = (start[1] + dest[1]) / 2.0 - 0.010;
                    var routePath = [start, [midLat, midLng], dest];
                    var polyline = L.polyline(routePath, { color: '#116530', weight: 6, opacity: 0.85 }).addTo(map);
                    map.fitBounds(polyline.getBounds(), { padding: [50, 50] });
                }

                setTimeout(initLeafletEngine, 200);
            </script>
        </body>
        </html>
    """.trimIndent()

    // Write HTML directly to local cache file for native WebView URL loading
    val mapHtmlFile = remember(leafletHtmlContent, leafletId) {
        try {
            val file = File(context.cacheDir, "leaflet_map_$leafletId.html")
            file.writeText(leafletHtmlContent, Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminBg)
    ) {
        // Top Header Bar
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isSingleTerminal) "Terminal Hub Map View" else "Leaflet Driving Route Map",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isSingleTerminal) "Terminal: $cleanStart" else "ID: $leafletId  •  $cleanStart ➔ $cleanDest",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (!isMapLoaded) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AdminGreen,
                trackColor = Color(0xFFE0E0E0)
            )
        }

        // Leaflet Map Webview Container
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
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

                        if (mapHtmlFile != null && mapHtmlFile.exists()) {
                            loadUrl("file://${mapHtmlFile.absolutePath}")
                        } else {
                            loadDataWithBaseURL("https://leafletjs.com", leafletHtmlContent, "text/html", "utf-8", null)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Info Card Overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSingleTerminal) "Terminal ID: $leafletId" else "Route ID: $leafletId",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AdminGreen
                    )
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (isSingleTerminal) "Active Terminal Hub" else "Active & Road Synced",
                            fontSize = 11.sp,
                            color = AdminGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (isSingleTerminal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Store, contentDescription = null, tint = AdminGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terminal: $cleanStart", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Location: $cleanDest", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Origin: $cleanStart", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Destination: $cleanDest", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Map Engine: OpenStreetMap + Leaflet.js Interactive Engine", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
