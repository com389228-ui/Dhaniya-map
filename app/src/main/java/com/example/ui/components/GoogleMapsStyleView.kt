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

    // Calculate Estimated Time of Arrival (ETA)
    val etaString = remember(realRouteResult, landDistanceMeters, travelMode) {
        val seconds = realRouteResult?.durationSeconds ?: run {
            val speedKmh = when (travelMode) {
                TravelMode.DRIVING -> 42.0
                TravelMode.WALKING -> 4.8
                TravelMode.BICYCLE -> 16.0
            }
            (landDistanceMeters / 1000.0) / speedKmh * 3600.0
        }
        val totalMin = (seconds / 60.0).toInt().coerceAtLeast(1)
        if (totalMin >= 60) {
            val hrs = totalMin / 60
            val mins = totalMin % 60
            if (mins > 0) "$hrs hr $mins min" else "$hrs hr"
        } else {
            "$totalMin min"
        }
    }

    val safeOriginName = originName.replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ")
    val safeDestName = destName.replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ")

    // Generate HTML for Leaflet Map with real road routing and Google Maps styling
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
                    /* Google Maps style Destination Callout */
                    .gmaps-dest-tooltip {
                        background: #FFFFFF !important;
                        color: #202124 !important;
                        border: none !important;
                        border-radius: 8px !important;
                        padding: 5px 9px !important;
                        box-shadow: 0 4px 14px rgba(0,0,0,0.45) !important;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                    }
                    .gmaps-dest-tooltip::before {
                        border-top-color: #FFFFFF !important;
                    }
                    .gmaps-dest-title {
                        color: #EA4335;
                        font-size: 10px;
                        font-weight: 800;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        display: flex;
                        align-items: center;
                        gap: 3px;
                    }
                    .gmaps-dest-name {
                        color: #1A1A1A;
                        font-size: 12px;
                        font-weight: 700;
                        max-width: 170px;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        line-height: 1.25;
                    }
                    .gmaps-dest-meta {
                        display: flex;
                        align-items: center;
                        gap: 5px;
                        margin-top: 2px;
                        font-size: 10.5px;
                    }
                    .gmaps-eta-text {
                        color: #137333;
                        font-weight: 800;
                    }
                    .gmaps-dist-text {
                        color: #5F6368;
                        font-weight: 600;
                    }
                    /* Google Maps Origin Puck */
                    .gmaps-origin-tooltip {
                        background: #1A73E8 !important;
                        color: #FFFFFF !important;
                        border: none !important;
                        border-radius: 6px !important;
                        padding: 3px 8px !important;
                        font-size: 10px !important;
                        font-weight: 700 !important;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.3) !important;
                    }
                    .gmaps-origin-tooltip::before {
                        border-top-color: #1A73E8 !important;
                    }
                    @keyframes gmaps-puck-pulse {
                        0% { transform: scale(0.5); opacity: 0.9; }
                        70% { transform: scale(1.8); opacity: 0.0; }
                        100% { transform: scale(1.8); opacity: 0.0; }
                    }
                    .puck-pulse {
                        position: absolute;
                        width: 28px;
                        height: 28px;
                        border-radius: 50%;
                        background: rgba(66, 133, 244, 0.45);
                        animation: gmaps-puck-pulse 2s infinite ease-out;
                    }
                    .puck-center {
                        position: absolute;
                        width: 16px;
                        height: 16px;
                        border-radius: 50%;
                        background: #1A73E8;
                        border: 3px solid #FFFFFF;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.4);
                    }
                    /* Road Midpoint Badge */
                    .gmaps-route-bubble {
                        background: #1E293B;
                        color: #FFFFFF;
                        border: 1.5px solid #3B82F6;
                        border-radius: 14px;
                        padding: 3px 8px;
                        font-size: 10px;
                        font-weight: bold;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.5);
                        white-space: nowrap;
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

                    // 1. Google Maps Origin Marker (Blue pulsing puck)
                    var originIcon = L.divIcon({
                        className: 'gmaps-origin-container',
                        html: '<div style="position:relative; width:28px; height:28px; display:flex; align-items:center; justify-content:center;">' +
                              '<div class="puck-pulse"></div>' +
                              '<div class="puck-center"></div>' +
                              '</div>',
                        iconSize: [28, 28],
                        iconAnchor: [14, 14]
                    });

                    // 2. Google Maps Destination Marker (Iconic Red Pin)
                    var destIcon = L.divIcon({
                        className: 'gmaps-dest-container',
                        html: '<div style="position:relative; width:34px; height:44px; filter:drop-shadow(0 4px 6px rgba(0,0,0,0.55)); cursor:pointer;">' +
                              '<svg viewBox="0 0 24 36" width="34" height="44">' +
                              '<path fill="#EA4335" stroke="#B31412" stroke-width="0.8" d="M12 0C5.37 0 0 5.37 0 12c0 8.8 12 24 12 24s12-15.2 12-24c0-6.63-5.37-12-12-12z"/>' +
                              '<circle fill="#FFFFFF" cx="12" cy="12" r="5"/>' +
                              '<circle fill="#B31412" cx="12" cy="12" r="2.5"/>' +
                              '</svg>' +
                              '</div>',
                        iconSize: [34, 44],
                        iconAnchor: [17, 44],
                        popupAnchor: [0, -44]
                    });

                    var markerA = L.marker([$originLat, $originLng], {icon: originIcon}).addTo(map);
                    markerA.bindTooltip('<div class="gmaps-origin-tooltip">🚩 $safeOriginName</div>', {
                        permanent: true,
                        direction: 'top',
                        offset: [0, -16],
                        className: 'gmaps-origin-tooltip'
                    });

                    var markerB = L.marker([$destLat, $destLng], {icon: destIcon}).addTo(map);
                    // Google Maps style floating callout bubble directly above Destination
                    markerB.bindTooltip(
                        '<div class="gmaps-dest-title">📍 मंज़िल (Destination)</div>' +
                        '<div class="gmaps-dest-name">$safeDestName</div>' +
                        '<div class="gmaps-dest-meta"><span class="gmaps-eta-text">$etaString</span> • <span class="gmaps-dist-text">$formattedLand</span></div>',
                        {
                            permanent: true,
                            direction: 'top',
                            offset: [0, -42],
                            className: 'gmaps-dest-tooltip'
                        }
                    );

                    // Aerial straight line (Cyan dashed line)
                    var aerialLine = L.polyline([
                        [$originLat, $originLng],
                        [$destLat, $destLng]
                    ], {
                        color: '#00E5FF',
                        weight: 2.5,
                        dashArray: '5, 6',
                        opacity: 0.65
                    }).addTo(map);

                    var roadLayerGroup = null;

                    function renderGeoJson(geoJsonData) {
                        if (roadLayerGroup) {
                            map.removeLayer(roadLayerGroup);
                        }
                        // Outer casing (Google Maps route border)
                        var casing = L.geoJSON(geoJsonData, {
                            style: {
                                color: '#1A56DB',
                                weight: 7,
                                opacity: 0.95,
                                lineCap: 'round',
                                lineJoin: 'round'
                            }
                        });
                        // Core road line (Google Maps vibrant navigation blue)
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
                        map.fitBounds(roadLayerGroup.getBounds().pad(0.22));
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
                            weight: 4.5,
                            opacity: 0.9
                        }).addTo(map);
                        var bounds = new L.featureGroup([markerA, markerB, fallbackRoad]);
                        map.fitBounds(bounds.getBounds().pad(0.22));
                    }

                    // Route midpoint badge
                    var centerLat = ($originLat + $destLat) / 2;
                    var centerLng = ($originLng + $destLng) / 2;
                    var badgeIcon = L.divIcon({
                        className: 'gmaps-route-bubble-container',
                        html: '<div class="gmaps-route-bubble">🛣️ $formattedLand • $etaString</div>',
                        iconSize: [120, 24],
                        iconAnchor: [60, 12]
                    });
                    L.marker([centerLat, centerLng], {icon: badgeIcon}).addTo(map);

                    function fitRoute() {
                        if (roadLayerGroup) {
                            map.fitBounds(roadLayerGroup.getBounds().pad(0.22));
                        } else {
                            var g = new L.featureGroup([markerA, markerB]);
                            map.fitBounds(g.getBounds().pad(0.22));
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
            // Google Maps Style Top Destination Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFEA4335).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFFEA4335), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = "मंज़िल (Destination)",
                            tint = Color(0xFFEA4335),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "मंज़िल (DESTINATION):",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = etaString,
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = destName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Open in Google Maps navigation button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A73E8))
                            .clickable { openInGoogleMapsApp() }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Google Maps में शुरू करें",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "शुरू करें",
                                color = Color.White,
                                fontSize = 11.sp,
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

            Spacer(modifier = Modifier.height(8.dp))

            // Google Maps Navigation Green Maneuver Banner (Iconic turn-by-turn banner)
            val firstStep = realRouteResult?.steps?.firstOrNull()
            val upcomingInstruction = firstStep?.instruction ?: "गंतव्य की ओर बढ़ें (${realRouteResult?.summaryRoad ?: "मुख्य सड़क"})"
            val upcomingDistance = firstStep?.let {
                if (it.distanceMeters >= 1000) String.format(Locale.US, "%.1f km", it.distanceMeters / 1000)
                else "${it.distanceMeters.toInt()} m"
            } ?: formattedLand

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F9D58))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.TurnRight,
                            contentDescription = "Turn direction",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = upcomingInstruction,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "मंज़िल: $destName",
                                color = Color(0xFFE8F5E9),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0B8043), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = upcomingDistance,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Map Viewport Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 380.dp else 250.dp)
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
                        Text("सड़क मार्ग", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

            // Google Maps Navigation Bottom Overview Card
            val summaryText = realRouteResult?.summaryRoad?.ifBlank { null } ?: "मुख्य सड़क मार्ग"
            val totalSteps = realRouteResult?.steps?.size ?: 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF162235))
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    // Row 1: Big Green ETA, Distance & Route Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = etaString,
                                color = Color(0xFF10B981),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "($formattedLand)",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Aerial comparison pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "✈️ सीधी: $formattedAerial",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "सबसे तेज़ मार्ग • वाया: $summaryText",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Detailed Length in meters & feet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📏 कुल लम्बाई (Exact Length):",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${GeoUtils.formatMeterLength(landDistanceMeters)} • ${GeoUtils.formatFeetLength(landDistanceMeters)}",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF24334A), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons row: Big Start Button + Steps List Button + Fit Route
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Google Maps Start Navigation Button
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A73E8))
                                .clickable { openInGoogleMapsApp() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = "नेविगेट करें",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Maps नेविगेशन शुरू करें",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Steps toggle button
                        if (totalSteps > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .clickable { showStepsList = !showStepsList }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (showStepsList) "मोड़ छुपाएं" else "$totalSteps मोड़ देखें",
                                        color = Color(0xFF93C5FD),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        if (showStepsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = Color(0xFF93C5FD),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
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
