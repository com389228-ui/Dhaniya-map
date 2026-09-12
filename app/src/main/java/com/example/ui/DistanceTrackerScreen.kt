package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CompassRadarView
import com.example.ui.components.DestinationPickerSheet
import com.example.util.DistanceUnit
import com.example.util.GeoUtils
import com.example.viewmodel.DistanceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistanceTrackerScreen(
    viewModel: DistanceViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPickerSheet by remember { mutableStateOf(false) }
    var isPickingForOrigin by remember { mutableStateOf(false) }

    val copyReport: () -> Unit = {
        val calc = uiState.calculation
        if (calc != null) {
            val originName = if (uiState.isFixedDistanceMode) {
                uiState.fixedOrigin?.name ?: "Point A (आरंभिक बिंदु)"
            } else {
                uiState.currentAddress.ifBlank { "Maujooda GPS Sthaan" }
            }
            val destName = uiState.destination?.name ?: "Destination (मंज़िल)"
            val report = buildString {
                append("📍 DISTANCE TRACKER REPORT / दूरी ब्योरा\n")
                append("• मोड: ${if (uiState.isFixedDistanceMode) "🔒 फिक्स दूरी (Fixed Distance - Locked)" else "📍 लाइव जीपीएस (Live GPS Tracking)"}\n")
                append("• Point A (आरंभ): $originName\n")
                append("• Point B (मंज़िल): $destName\n")
                append("• हवाई दूरी (Aerial / सीधी): ${GeoUtils.formatDistanceValue(calc.aerialDistanceMeters, uiState.selectedUnit)} ${uiState.selectedUnit.shortLabel} (${calc.aerialDistanceMeters.toInt()}m)\n")
                append("• ज़मीनी दूरी (Road / सड़क): ${GeoUtils.formatDistanceValue(calc.landDistanceMeters, uiState.selectedUnit)} ${uiState.selectedUnit.shortLabel} (~1.28x Detour)\n")
                append("• दिशा (Bearing): ${String.format(Locale.getDefault(), "%.0f°", calc.bearingDegrees)} (${calc.cardinalDirection})\n")
                append("• अनुमानित यात्रा समय: कार ~${calc.driveTimeMinutes}m | फ्लाइट ~${calc.flightTimeMinutes}m | ट्रेन ~${calc.trainTimeMinutes}m")
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Distance Report", report)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "दूरी रिपोर्ट क्लिपबोर्ड पर कॉपी हो गई!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "कोई दूरी डेटा उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for Fine and Coarse location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF070E1A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Distance Tracker",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (uiState.isFixedDistanceMode) "🔒 फिक्स दूरी मोड (Fixed Geodesic)" else "📍 लाइव हवाई और ज़मीनी दूरी",
                            color = if (uiState.isFixedDistanceMode) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (uiState.isFixedDistanceMode) Color(0xFFFFD54F).copy(alpha = 0.2f)
                                else if (uiState.isSimulatingMovement) Color(0xFFFF9100).copy(alpha = 0.2f)
                                else if (uiState.currentLocation != null) Color(0xFF00E676).copy(alpha = 0.2f)
                                else Color(0xFFFF5252).copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (uiState.isFixedDistanceMode) Color(0xFFFFD54F)
                                else if (uiState.isSimulatingMovement) Color(0xFFFF9100)
                                else if (uiState.currentLocation != null) Color(0xFF00E676)
                                else Color(0xFFFF5252),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (uiState.isFixedDistanceMode) Color(0xFFFFD54F)
                                        else if (uiState.isSimulatingMovement) Color(0xFFFF9100)
                                        else if (uiState.currentLocation != null) Color(0xFF00E676)
                                        else Color(0xFFFF5252),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (uiState.isFixedDistanceMode) "Fixed Lock"
                                else if (uiState.isSimulatingMovement) "Simulating"
                                else if (uiState.currentLocation != null) "GPS Live"
                                else "No GPS",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B1626))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission Alert Banner if not granted
            if (!uiState.hasLocationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Permission Chahiye",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Maujooda location aur live GPS tracking ke liye permission dein ya simulation use karein.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Allow", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Proximity message if any
            uiState.statusMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00E676).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00E5FF))))
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 1. PRIMARY MODE SWITCHER: Live GPS vs Fixed Distance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mode Tab 1: Live GPS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (!uiState.isFixedDistanceMode) Color(0xFF00E5FF).copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (!uiState.isFixedDistanceMode) Color(0xFF00E5FF) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (uiState.isFixedDistanceMode) viewModel.toggleFixedDistanceMode()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = if (!uiState.isFixedDistanceMode) Color(0xFF00E5FF) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📍 लाइव जीपीएस (Live)",
                            color = if (!uiState.isFixedDistanceMode) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (!uiState.isFixedDistanceMode) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Mode Tab 2: Fixed Distance
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (uiState.isFixedDistanceMode) Color(0xFFFFD54F).copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (uiState.isFixedDistanceMode) Color(0xFFFFD54F) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (!uiState.isFixedDistanceMode) viewModel.toggleFixedDistanceMode()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (uiState.isFixedDistanceMode) Color(0xFFFFD54F) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🔒 फिक्स दूरी (Fixed)",
                            color = if (uiState.isFixedDistanceMode) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (uiState.isFixedDistanceMode) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fixed Mode Notice Banner
            if (uiState.isFixedDistanceMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2411)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF9100))))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "फिक्स दूरी लॉक है (100% Fixed Geodesic)",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Point A और Point B के बीच की दूरी स्थिर है। यह जीपीएस ड्रिफ्ट से नहीं बदलेगी।",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. QUICK / GESTURE ACTION RIBBON
            QuickActionsRibbon(
                isFixedMode = uiState.isFixedDistanceMode,
                onToggleFixMode = { viewModel.toggleFixedDistanceMode() },
                onLockPointA = { viewModel.lockOriginToCurrentLocation() },
                onSwapPoints = { viewModel.swapOriginAndDestination() },
                onCycleUnit = { viewModel.cycleUnit() },
                onCopyReport = copyReport
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. ROUTE LOCATIONS CARD (Point A & Point B with Swap)
            RouteLocationsCard(
                isFixedDistanceMode = uiState.isFixedDistanceMode,
                currentAddress = uiState.currentAddress,
                currentLocation = uiState.currentLocation,
                fixedOrigin = uiState.fixedOrigin,
                destination = uiState.destination,
                onChangeOriginClick = {
                    isPickingForOrigin = true
                    showPickerSheet = true
                },
                onChangeDestinationClick = {
                    isPickingForOrigin = false
                    showPickerSheet = true
                },
                onSwapClick = { viewModel.swapOriginAndDestination() },
                onLockCurrentLocationAsOrigin = { viewModel.lockOriginToCurrentLocation() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Unit Selector Chips (KM, Meters, Miles, Nautical Miles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Unit:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DistanceUnit.values().forEach { unit ->
                        FilterChip(
                            selected = uiState.selectedUnit == unit,
                            onClick = { viewModel.setUnit(unit) },
                            label = { Text(unit.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            border = null,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Hero Dual Distance Cards (Hawai Doori vs Zameeni Doori)
            val calc = uiState.calculation
            if (calc != null) {
                DualDistanceHero(
                    aerialMeters = calc.aerialDistanceMeters,
                    landMeters = calc.landDistanceMeters,
                    selectedUnit = uiState.selectedUnit,
                    cardinalDirection = calc.cardinalDirection,
                    bearingDegrees = calc.bearingDegrees,
                    speedKmH = calc.speedKmH,
                    isFixedMode = uiState.isFixedDistanceMode,
                    onToggleFixMode = { viewModel.toggleFixedDistanceMode() },
                    onCycleUnit = { viewModel.cycleUnit() },
                    onCopyDistance = copyReport
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Compass & Radar HUD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1626)),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Disha / Bearing Compass",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.getDefault(), "%.0f° %s", calc.bearingDegrees, calc.shortCardinal),
                                    color = Color(0xFFFFD54F),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Radar Compass View
                        CompassRadarView(
                            bearingDegrees = calc.bearingDegrees,
                            deviceAzimuth = uiState.deviceAzimuth,
                            isTrackingActive = uiState.isTracking
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Lal teer manzil ki taraf ishara karta hai (${calc.cardinalDirection})",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Travel Time Estimation Grid
                TravelModesCard(calc = calc)

                Spacer(modifier = Modifier.height(16.dp))

                // Coordinates & Telemetry Details Card
                TelemetryDetailsCard(
                    currentLocation = uiState.currentLocation,
                    destination = uiState.destination,
                    onCopyCoordinates = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Coordinates", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Movement Simulation Controls (Test / Simulator)
                SimulationControlsCard(
                    isSimulating = uiState.isSimulatingMovement,
                    simulatedSpeedKmH = uiState.simulatedSpeedKmH,
                    onToggleSimulation = { viewModel.toggleSimulation() },
                    onSpeedChange = { viewModel.setSimulatedSpeed(it) }
                )
            } else {
                // Empty or loading state
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.NearMe,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Duri ki ginti ho rahi hai...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Maujooda GPS location lock ho raha hai. Manzil chunein.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showPickerSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Manzil Chunein", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal Bottom Sheet for Origin or Destination Selection
    if (showPickerSheet) {
        DestinationPickerSheet(
            onDismiss = { showPickerSheet = false },
            onSelectDestination = { dest ->
                if (isPickingForOrigin) {
                    viewModel.setOrigin(dest)
                } else {
                    viewModel.setDestination(dest)
                }
                showPickerSheet = false
            },
            onSearch = { query ->
                viewModel.onSearchQueryChanged(query)
            },
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching,
            savedDestinations = uiState.savedDestinations,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDeleteSaved = { viewModel.deleteSaved(it) },
            onSetManualCoordinates = { name, lat, lng ->
                if (isPickingForOrigin) {
                    viewModel.setManualOrigin(name, lat, lng)
                } else {
                    viewModel.setManualDestination(name, lat, lng)
                }
                showPickerSheet = false
            },
            title = if (isPickingForOrigin) "आरंभिक स्थान (Point A) चुनें" else "मंज़िल (Point B) चुनें",
            subtitle = if (isPickingForOrigin)
                "Point A सेट करें जिससे Point B तक की फिक्स दूरी नापी जाएगी"
            else
                "Hawai aur zameeni duri track karne ke liye sthaan chunein"
        )
    }
}

@Composable
fun QuickActionsRibbon(
    isFixedMode: Boolean,
    onToggleFixMode: () -> Unit,
    onLockPointA: () -> Unit,
    onSwapPoints: () -> Unit,
    onCycleUnit: () -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1626)),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF00E5FF).copy(alpha = 0.3f), Color(0xFFFFD54F).copy(alpha = 0.3f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ त्वरित एक्शन बार (Quick Actions)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isFixedMode) "🔒 फिक्स मोड" else "📍 लाइव जीपीएस",
                    color = if (isFixedMode) Color(0xFFFFD54F) else Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Action 1: Toggle Fix Mode
                QuickActionButton(
                    icon = if (isFixedMode) Icons.Default.Lock else Icons.Default.LockOpen,
                    label = if (isFixedMode) "फिक्स लॉक" else "फिक्स करें",
                    highlight = isFixedMode,
                    activeColor = Color(0xFFFFD54F),
                    onClick = onToggleFixMode,
                    modifier = Modifier.weight(1f)
                )

                // Action 2: Lock Current Location as Point A
                QuickActionButton(
                    icon = Icons.Default.PushPin,
                    label = "Point A लॉक",
                    highlight = false,
                    activeColor = Color(0xFF00E676),
                    onClick = onLockPointA,
                    modifier = Modifier.weight(1f)
                )

                // Action 3: Swap A <-> B
                QuickActionButton(
                    icon = Icons.Default.SwapVert,
                    label = "A ⇄ B स्वैप",
                    highlight = false,
                    activeColor = Color(0xFF00E5FF),
                    onClick = onSwapPoints,
                    modifier = Modifier.weight(1f)
                )

                // Action 4: Cycle Unit
                QuickActionButton(
                    icon = Icons.Default.Refresh,
                    label = "यूनिट",
                    highlight = false,
                    activeColor = Color(0xFF90CAF9),
                    onClick = onCycleUnit,
                    modifier = Modifier.weight(1f)
                )

                // Action 5: Copy Report
                QuickActionButton(
                    icon = Icons.Default.ContentCopy,
                    label = "कॉपी",
                    highlight = false,
                    activeColor = Color(0xFFB388FF),
                    onClick = onCopyReport,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlight) activeColor.copy(alpha = 0.2f)
                else Color(0xFF162235)
            )
            .border(
                1.dp,
                if (highlight) activeColor else Color(0xFF24334A),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlight) activeColor else Color(0xFFCBD5E1),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (highlight) activeColor else Color(0xFFCBD5E1),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RouteLocationsCard(
    isFixedDistanceMode: Boolean,
    currentAddress: String,
    currentLocation: android.location.Location?,
    fixedOrigin: com.example.location.SearchResult?,
    destination: com.example.location.SearchResult?,
    onChangeOriginClick: () -> Unit,
    onChangeDestinationClick: () -> Unit,
    onSwapClick: () -> Unit,
    onLockCurrentLocationAsOrigin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A2C)),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(Color(0xFF00E5FF).copy(alpha = 0.3f), Color(0xFFFFD54F).copy(alpha = 0.3f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Point A (Origin)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isFixedDistanceMode) Color(0xFFFFD54F).copy(alpha = 0.2f)
                            else Color(0xFF00E676).copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFixedDistanceMode) Icons.Default.Lock else Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = if (isFixedDistanceMode) Color(0xFFFFD54F) else Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isFixedDistanceMode) "आरंभिक बिंदु (Point A - फिक्स)" else "वर्तमान स्थान (Point A - GPS)",
                        color = if (isFixedDistanceMode) Color(0xFFFFD54F) else Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFixedDistanceMode) {
                            fixedOrigin?.name ?: "Point A सेट करें"
                        } else {
                            currentAddress.ifBlank { "GPS खोजा जा रहा है..." }
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isFixedDistanceMode && fixedOrigin != null) {
                        Text(
                            text = GeoUtils.formatCoordinates(fixedOrigin.latitude, fixedOrigin.longitude),
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    } else {
                        currentLocation?.let {
                            Text(
                                text = GeoUtils.formatCoordinates(it.latitude, it.longitude),
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onLockCurrentLocationAsOrigin,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "GPS लॉक करें",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onChangeOriginClick,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("बदलें", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Central Swap Connector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFF334155))
                )
                Surface(
                    onClick = onSwapClick,
                    shape = CircleShape,
                    color = Color(0xFF1E293B),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFFD54F)))),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "Point A aur B Swap Karein",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0xFF334155))
                )
            }

            // Point B (Destination)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFFF3D00).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFFF3D00),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "मंज़िल (Point B - Destination)",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = destination?.name ?: "Chuna nahi gaya",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = destination?.address ?: "मंज़िल चुनें",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onChangeDestinationClick,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("change_destination_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.EditLocation,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("बदलें", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DualDistanceHero(
    aerialMeters: Double,
    landMeters: Double,
    selectedUnit: DistanceUnit,
    cardinalDirection: String,
    bearingDegrees: Float,
    speedKmH: Float,
    isFixedMode: Boolean,
    onToggleFixMode: () -> Unit,
    onCycleUnit: () -> Unit,
    onCopyDistance: () -> Unit
) {
    var accumulatedDragX by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onToggleFixMode() },
                    onLongPress = { onCopyDistance() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (Math.abs(accumulatedDragX) > 60f) {
                            onCycleUnit()
                        }
                        accumulatedDragX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        accumulatedDragX += dragAmount
                    }
                )
            }
    ) {
        // Aerial Card (Hawai Doori)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("aerial_distance_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2644)),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    if (isFixedMode) listOf(Color(0xFFFFD54F), Color(0xFFFF9100))
                    else listOf(Color(0xFF00E5FF), Color(0xFF0288D1))
                )
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isFixedMode) Color(0xFFFFD54F).copy(alpha = 0.2f)
                                    else Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AirplanemodeActive,
                                contentDescription = null,
                                tint = if (isFixedMode) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "HAWAI DURI (AERIAL)",
                                color = if (isFixedMode) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isFixedMode) "निश्चित सीधी रेखा (Fixed Point A ➔ B)" else "Seedhi Rekha / As The Crow Flies",
                                color = Color(0xFF90CAF9),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0B192E), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isFixedMode) "🔒 100% FIXED" else "📍 LIVE GPS",
                            color = if (isFixedMode) Color(0xFFFFD54F) else Color(0xFF80DEEA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = GeoUtils.formatDistanceValue(aerialMeters, selectedUnit),
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedUnit.shortLabel,
                        color = if (isFixedMode) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Alternative units summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedUnit != DistanceUnit.KILOMETERS) {
                        Text(
                            text = "${GeoUtils.formatDistanceValue(aerialMeters, DistanceUnit.KILOMETERS)} km",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    if (selectedUnit != DistanceUnit.MILES) {
                        Text(
                            text = "${GeoUtils.formatDistanceValue(aerialMeters, DistanceUnit.MILES)} mi",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    if (selectedUnit != DistanceUnit.NAUTICAL_MILES) {
                        Text(
                            text = "${GeoUtils.formatDistanceValue(aerialMeters, DistanceUnit.NAUTICAL_MILES)} NM",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Land / Road Route Card (Zameeni Doori)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("land_distance_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00))))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0xFFFFB300).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ZAMEENI / ROAD DURI",
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Sadak Marg Anumaan (~1.28x Circuity Factor)",
                                color = Color(0xFFFFE082),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C1E0A), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "+28% detour",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = GeoUtils.formatDistanceValue(landMeters, selectedUnit),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedUnit.shortLabel,
                        color = Color(0xFFFFB300),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Gesture Tips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "जेस्चर टिप्स: 👆 2-टैप = फिक्स लॉक | 👈👉 स्वाइप = यूनिट बदलें | 📋 लॉन्ग-प्रेस = कॉपी",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TravelModesCard(calc: com.example.viewmodel.CalculationSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1626)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Yatra Samay Anumaan (Travel Time Estimates)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TravelTimePill(
                    icon = Icons.Default.AirplanemodeActive,
                    mode = "Hawai Jahaz",
                    speed = "750 km/h",
                    duration = GeoUtils.formatDuration(calc.flightTimeMinutes),
                    tint = Color(0xFF00E5FF)
                )
                TravelTimePill(
                    icon = Icons.Default.DirectionsCar,
                    mode = "Car / Gaadi",
                    speed = "65 km/h",
                    duration = GeoUtils.formatDuration(calc.driveTimeMinutes),
                    tint = Color(0xFFFFB300)
                )
                TravelTimePill(
                    icon = Icons.Default.DirectionsTransit,
                    mode = "Train / Rail",
                    speed = "80 km/h",
                    duration = GeoUtils.formatDuration(calc.trainTimeMinutes),
                    tint = Color(0xFF81C784)
                )
                TravelTimePill(
                    icon = Icons.Default.DirectionsRun,
                    mode = "Paidal",
                    speed = "4.8 km/h",
                    duration = GeoUtils.formatDuration(calc.walkTimeMinutes),
                    tint = Color(0xFFFF8A80)
                )
            }
        }
    }
}

@Composable
fun TravelTimePill(
    icon: ImageVector,
    mode: String,
    speed: String,
    duration: String,
    tint: Color
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF132238), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = duration, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = mode, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(text = speed, color = Color(0xFF64748B), fontSize = 9.sp)
    }
}

@Composable
fun TelemetryDetailsCard(
    currentLocation: android.location.Location?,
    destination: com.example.location.SearchResult?,
    onCopyCoordinates: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1626)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live GPS Telemetry",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (currentLocation != null) {
                    IconButton(
                        onClick = {
                            val text = "Current: ${currentLocation.latitude}, ${currentLocation.longitude} | Destination: ${destination?.latitude}, ${destination?.longitude}"
                            onCopyCoordinates(text)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Coordinates",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            currentLocation?.let { loc ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryItem(
                        label = "Latitude",
                        value = String.format(Locale.getDefault(), "%.5f°", loc.latitude)
                    )
                    TelemetryItem(
                        label = "Longitude",
                        value = String.format(Locale.getDefault(), "%.5f°", loc.longitude)
                    )
                    TelemetryItem(
                        label = "Gati / Speed",
                        value = String.format(Locale.getDefault(), "%.1f km/h", loc.speed * 3.6f)
                    )
                    TelemetryItem(
                        label = "Accuracy",
                        value = String.format(Locale.getDefault(), "±%.0fm", loc.accuracy)
                    )
                }
            } ?: run {
                Text(
                    text = "GPS reading aane ka intezar hai...",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun TelemetryItem(label: String, value: String) {
    Column {
        Text(text = label, color = Color(0xFF64748B), fontSize = 10.sp)
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun SimulationControlsCard(
    isSimulating: Boolean,
    simulatedSpeedKmH: Float,
    onToggleSimulation: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E33)),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isSimulating) listOf(Color(0xFFFF9100), Color(0xFFFF3D00))
                else listOf(Color(0xFF1E293B), Color(0xFF334155))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gati & Duri Simulation (Test Mode)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Bina chaley real-time distance countdown test karein",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onToggleSimulation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulating) Color(0xFFFF5252) else Color(0xFFFF9100)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSimulating) "Stop" else "Start Test",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            AnimatedVisibility(visible = isSimulating) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Simulated Speed: ${simulatedSpeedKmH.toInt()} km/h",
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (simulatedSpeedKmH > 300) "Aviation (Hawai) Speed" else "Vehicle Speed",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    Slider(
                        value = simulatedSpeedKmH,
                        onValueChange = onSpeedChange,
                        valueRange = 20f..800f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF9100),
                            activeTrackColor = Color(0xFFFF9100),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                }
            }
        }
    }
}
