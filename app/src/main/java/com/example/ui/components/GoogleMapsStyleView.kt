package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.location.SearchResult
import com.example.util.DistanceUnit
import com.example.util.GeoUtils
import com.example.util.RouteResult
import com.example.util.RouteStep
import com.example.util.TravelMode
import java.util.Locale

enum class MapTileType(val label: String, val tileUrl: String, val attribution: String) {
    ROADMAP(
        "सड़क (Road)",
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        "© OpenStreetMap"
    ),
    SATELLITE(
        "सैटेलाइट (Satellite)",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "© Esri & Maxar"
    ),
    DARK(
        "डार्क (Dark)",
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
        "© CARTO"
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleMapsStyleView(
    origin: SearchResult?,
    destination: SearchResult?,
    aerialDistanceMeters: Double,
    landDistanceMeters: Double,
    selectedUnit: DistanceUnit,
    travelMode: TravelMode = TravelMode.DRIVING,
    realRouteResult: RouteResult? = null,
    isFetchingRoute: Boolean = false,
    onSelectTravelMode: (TravelMode) -> Unit = {},
    onRefreshRoute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var showStepsList by remember { mutableStateOf(false) }
    var selectedTileType by remember { mutableStateOf(MapTileType.ROADMAP) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val originLat = origin?.latitude ?: 28.6139
    val originLng = origin?.longitude ?: 77.2090
    val originName = origin?.name ?: "Point A (आरंभ)"

    val destLat = destination?.latitude ?: 27.1751
    val destLng = destination?.longitude ?: 78.0421
    val destName = destination?.name ?: "Point B (मंज़िल)"

    val formattedAerial = "${GeoUtils.formatDistanceValue(aerialDistanceMeters, selectedUnit)} ${selectedUnit.shortLabel}"
    val formattedLand = "${GeoUtils.formatDistanceValue(landDistanceMeters, selectedUnit)} ${selectedUnit.shortLabel}"

    val geoJsonString = realRouteResult?.geometryGeoJson ?: "null"

    // Generate HTML for Leaflet Map with real road routing
    fun generateMapHtml(tileType: MapTileType): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body, html, #map {
                        margin: 0; padding: 0; width: 100%; height: 100%;
                        background: #0B1626; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    }
                    .custom-badge {
                        background: #0F172A;
                        color: #00E5FF;
                        border: 1px solid #00E5FF;
                        border-radius: 12px;
                        padding: 3px 8px;
                        font-size: 11px;
                        font-weight: bold;
                        white-space: nowrap;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.6);
                    }
                    .road-badge {
                        background: #1E3A8A;
                        color: #60A5FA;
                        border: 1px solid #3B82F6;
                        border-radius: 12px;
                        padding: 3px 8px;
                        font-size: 11px;
                        font-weight: bold;
                        white-space: nowrap;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.6);
                    }
                    .leaflet-popup-content-wrapper {
                        background: #1E293B;
                        color: #FFFFFF;
                        border-radius: 8px;
                    }
                    .leaflet-popup-tip {
                        background: #1E293B;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', {
                        zoomControl: false,
                        attributionControl: false
                    });

                    L.tileLayer('${tileType.tileUrl}', {
                        maxZoom: 19
                    }).addTo(map);

                    var originIcon = L.divIcon({
                        className: 'origin-marker',
                        html: '<div style="background:#00E676; width:18px; height:18px; border-radius:50%; border:3px solid #FFFFFF; box-shadow:0 0 10px #00E676; display:flex; align-items:center; justify-content:center; color:#000; font-size:9px; font-weight:bold;">A</div>',
                        iconSize: [24, 24],
                        iconAnchor: [12, 12]
                    });

                    var destIcon = L.divIcon({
                        className: 'dest-marker',
                        html: '<div style="background:#FF3D00; width:18px; height:18px; border-radius:50%; border:3px solid #FFFFFF; box-shadow:0 0 10px #FF3D00; display:flex; align-items:center; justify-content:center; color:#FFF; font-size:9px; font-weight:bold;">B</div>',
                        iconSize: [24, 24],
                        iconAnchor: [12, 12]
                    });

                    var markerA = L.marker([$originLat, $originLng], {icon: originIcon}).addTo(map)
                        .bindPopup("<b>Point A: $originName</b><br>Lat: $originLat, Lng: $originLng");

                    var markerB = L.marker([$destLat, $destLng], {icon: destIcon}).addTo(map)
                        .bindPopup("<b>Point B: $destName</b><br>Lat: $destLat, Lng: $destLng");

                    // Aerial straight line (Cyan dashed)
                    var aerialLine = L.polyline([
                        [$originLat, $originLng],
                        [$destLat, $destLng]
                    ], {
                        color: '#00E5FF',
                        weight: 3,
                        dashArray: '6, 6',
                        opacity: 0.75
                    }).addTo(map);

                    var roadLayerGroup = null;

                    function renderGeoJson(geoJsonData) {
                        if (roadLayerGroup) {
                            map.removeLayer(roadLayerGroup);
                        }
                        // Casing outer border (Dark Blue)
                        var casing = L.geoJSON(geoJsonData, {
                            style: {
                                color: '#1E3A8A',
                                weight: 7,
                                opacity: 0.95,
                                lineCap: 'round',
                                lineJoin: 'round'
                            }
                        });
                        // Core road line (Google Maps vibrant blue)
                        var coreLine = L.geoJSON(geoJsonData, {
                            style: {
                                color: '#3B82F6',
                                weight: 4.5,
                                opacity: 1.0,
                                lineCap: 'round',
                                lineJoin: 'round'
                            }
                        });

                        roadLayerGroup = L.featureGroup([casing, coreLine]).addTo(map);
                        map.fitBounds(roadLayerGroup.getBounds().pad(0.18));
                    }

                    var routeData = $geoJsonString;
                    if (routeData) {
                        renderGeoJson(routeData);
                    } else {
                        // Client-side OSRM routing fetch fallback
                        var travelModeStr = '${travelMode.apiValue}';
                        var osrmUrl = 'https://router.project-osrm.org/route/v1/' + travelModeStr + '/$originLng,$originLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true';
                        fetch(osrmUrl)
                            .then(function(res){ return res.json(); })
                            .then(function(data){
                                if (data && data.routes && data.routes.length > 0) {
                                    renderGeoJson(data.routes[0].geometry);
                                } else {
                                    drawEstimatedArc();
                                }
                            })
                            .catch(function(e){
                                drawEstimatedArc();
                            });
                    }

                    function drawEstimatedArc() {
                        var midLat = ($originLat + $destLat) / 2 + ($destLng - $originLng) * 0.05;
                        var midLng = ($originLng + $destLng) / 2 - ($destLat - $originLat) * 0.05;
                        var fallbackRoad = L.polyline([
                            [$originLat, $originLng],
                            [midLat, midLng],
                            [$destLat, $destLng]
                        ], {
                            color: '#3B82F6',
                            weight: 4,
                            opacity: 0.8
                        }).addTo(map);
                        var bounds = new L.featureGroup([markerA, markerB, fallbackRoad]);
                        map.fitBounds(bounds.getBounds().pad(0.2));
                    }

                    // Midpoint badges
                    var centerLat = ($originLat + $destLat) / 2;
                    var centerLng = ($originLng + $destLng) / 2;
                    var badgeIcon = L.divIcon({
                        className: 'custom-badge-container',
                        html: '<div class="road-badge">🛣️ $formattedLand</div>',
                        iconSize: [90, 24],
                        iconAnchor: [45, 12]
                    });
                    L.marker([centerLat, centerLng], {icon: badgeIcon}).addTo(map);

                    function fitRoute() {
                        if (roadLayerGroup) {
                            map.fitBounds(roadLayerGroup.getBounds().pad(0.18));
                        } else {
                            var g = new L.featureGroup([markerA, markerB]);
                            map.fitBounds(g.getBounds().pad(0.2));
                        }
                    }
                    function zoomIn() { map.zoomIn(); }
                    function zoomOut() { map.zoomOut(); }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    // Launch Google Maps App intent for turn-by-turn navigation
    fun openInGoogleMapsApp() {
        val gmapsMode = when (travelMode) {
            TravelMode.DRIVING -> "driving"
            TravelMode.WALKING -> "walking"
            TravelMode.BICYCLE -> "bicycling"
        }
        try {
            val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=$gmapsMode")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    // Refresh map when route GeoJSON or tiles change
    LaunchedEffect(realRouteResult, selectedTileType) {
        webViewRef?.loadDataWithBaseURL(null, generateMapHtml(selectedTileType), "text/html", "UTF-8", null)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF00E5FF).copy(alpha = 0.5f), Color(0xFF3B82F6).copy(alpha = 0.6f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🗺️ सड़क व गली मैप (Street Route Map)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "वास्तविक मोड़, गलियों और सड़कों का सटीक मार्ग",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Open in Google Maps button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { openInGoogleMapsApp() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Google Maps में नेविगेट",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Maps नेविगेट",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.CropFree,
                            contentDescription = "Expand Map",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Travel Mode Selection Tabs (Driving, Walking/Gali, Bike)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF162235))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TravelMode.values().forEach { mode ->
                    val isSelected = travelMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent)
                            .clickable {
                                onSelectTravelMode(mode)
                            }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = mode.icon,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (mode) {
                                    TravelMode.DRIVING -> "कार मार्ग"
                                    TravelMode.WALKING -> "गली / पैदल"
                                    TravelMode.BICYCLE -> "साइकिल"
                                },
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Map Layer Selector (Road / Satellite / Dark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MapTileType.values().forEach { tileType ->
                    val isSelected = selectedTileType == tileType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                selectedTileType = tileType
                                webViewRef?.loadDataWithBaseURL(null, generateMapHtml(tileType), "text/html", "UTF-8", null)
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tileType.label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF64748B),
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Map Viewport Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 380.dp else 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL(null, generateMapHtml(selectedTileType), "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        // Leaflet handles redraw via LaunchedEffect
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isFetchingRoute) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF00E5FF),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "सड़क व गलियों का मार्ग खोजा जा रहा है...",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Map Legend Overlay at bottom-left
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Real Road Route line indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(3.dp)
                                .background(Color(0xFF3B82F6), RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("सड़क रास्ता", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Aerial straight line indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(2.dp)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("हवाई", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Recenter Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFF334155), CircleShape)
                        .clickable {
                            webViewRef?.loadUrl("javascript:fitRoute();")
                            onRefreshRoute()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Fit Route",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Street Route Details Card (कौन से रास्ते/गलियों से होकर जा रहा है)
            val summaryText = realRouteResult?.summaryRoad?.ifBlank { null } ?: "मुख्य सड़क मार्ग"
            val totalSteps = realRouteResult?.steps?.size ?: 0
            val durationMin = realRouteResult?.let { (it.durationSeconds / 60.0).toInt().coerceAtLeast(1) } ?: 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF162235))
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "चुना गया मार्ग (Selected Route):",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = summaryText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Duration Badge
                        if (durationMin > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E3A8A))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "~$durationMin मिनट",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛣️ सड़क दूरी: $formattedLand | ✈️ सीधी: $formattedAerial",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Toggle button for turn-by-turn street step details
                        if (totalSteps > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E293B))
                                    .clickable { showStepsList = !showStepsList }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showStepsList) "मोड़ छुपाएं" else "$totalSteps मोड़/गलियां देखें",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    if (showStepsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Turn-by-Turn Street and Alleys Step List
                    AnimatedVisibility(
                        visible = showStepsList && realRouteResult?.steps?.isNotEmpty() == true,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "📍 गली-दर-गली रास्ते का विवरण (Step-by-Step Turns):",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            realRouteResult?.steps?.take(15)?.forEachIndexed { index, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(Color(0xFF1E293B), CircleShape)
                                            .border(1.dp, Color(0xFF3B82F6), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color(0xFF93C5FD),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.instruction,
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                    if (step.distanceMeters > 0) {
                                        Text(
                                            text = if (step.distanceMeters >= 1000) String.format(Locale.US, "%.1f km", step.distanceMeters / 1000) else "${step.distanceMeters.toInt()} m",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            if ((realRouteResult?.steps?.size ?: 0) > 15) {
                                Text(
                                    text = "... और ${(realRouteResult?.steps?.size ?: 0) - 15} आगे के मोड़ (पूर्ण रास्ते के लिए Google Maps खोलें)",
                                    color = Color(0xFF64748B),
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
