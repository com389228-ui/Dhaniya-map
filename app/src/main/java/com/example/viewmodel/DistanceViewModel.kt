package com.example.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.DestinationEntity
import com.example.data.DestinationPreset
import com.example.data.PresetDestinations
import com.example.location.DeviceOrientationTracker
import com.example.location.LocationClient
import com.example.location.SearchResult
import com.example.util.DistanceUnit
import com.example.util.GeoUtils
import com.example.util.RoadDetourProfile
import com.example.util.RouteResult
import com.example.util.RouteService
import com.example.util.RouteStep
import com.example.util.TravelMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class CalculationSummary(
    val aerialDistanceMeters: Double,
    val landDistanceMeters: Double,
    val bearingDegrees: Float,
    val cardinalDirection: String,
    val shortCardinal: String,
    val relativeBearingDegrees: Float,
    val flightTimeMinutes: Int,
    val driveTimeMinutes: Int,
    val trainTimeMinutes: Int,
    val walkTimeMinutes: Int,
    val speedKmH: Float = 0f,
    val etaAtCurrentSpeed: String? = null
)

data class UiState(
    val hasLocationPermission: Boolean = false,
    val isTracking: Boolean = true,
    val currentLocation: Location? = null,
    val currentAddress: String = "Detecting current location...",
    val destination: SearchResult? = null,
    val calculation: CalculationSummary? = null,
    val selectedUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val deviceAzimuth: Float = 0f,
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val savedDestinations: List<DestinationEntity> = emptyList(),
    val isSimulatingMovement: Boolean = false,
    val simulatedSpeedKmH: Float = 60f,
    val distanceDelta: Double = 0.0, // negative means getting closer
    val statusMessage: String? = null,
    val isFixedDistanceMode: Boolean = false, // When true, calculates exact fixed distance between Point A and Point B
    val fixedOrigin: SearchResult? = null, // Fixed Origin (Point A)
    val isPickingOrigin: Boolean = false, // Sheet picker mode for Point A vs Point B
    val roadDetourProfile: RoadDetourProfile = RoadDetourProfile.GOOGLE_MAPS_STANDARD,
    val customRoadFactor: Double = GeoUtils.DEFAULT_GOOGLE_MAPS_ROAD_FACTOR, // Calibrated to 1.12 to match Google Maps accurately
    val isMapViewVisible: Boolean = true, // Toggle Map View like Google Maps
    val travelMode: TravelMode = TravelMode.DRIVING, // Mode: Driving, Walking (Gali-Mohalla), Bike
    val realRouteResult: RouteResult? = null, // Real street-level turn-by-turn routing result
    val isFetchingRoute: Boolean = false
)

class DistanceViewModel(application: Application) : AndroidViewModel(application) {

    private val locationClient = LocationClient(application)
    private val orientationTracker = DeviceOrientationTracker(application)

    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "distance_tracker.db"
    ).build()
    private val destinationDao = database.destinationDao()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var locationTrackingJob: Job? = null
    private var simulationJob: Job? = null
    private var previousDistance: Double? = null

    init {
        // Start listening to compass orientation
        orientationTracker.startListening()
        viewModelScope.launch {
            orientationTracker.deviceAzimuth.collectLatest { azimuth ->
                _uiState.update { current ->
                    val relative = current.calculation?.let { calc ->
                        (calc.bearingDegrees - azimuth + 360f) % 360f
                    } ?: 0f
                    current.copy(
                        deviceAzimuth = azimuth,
                        calculation = current.calculation?.copy(relativeBearingDegrees = relative)
                    )
                }
            }
        }

        // Collect saved destinations from Room
        viewModelScope.launch {
            destinationDao.getAllDestinations().collectLatest { saved ->
                _uiState.update { it.copy(savedDestinations = saved) }
            }
        }

        // Set default destination (e.g., Taj Mahal, Agra)
        val defaultPreset = PresetDestinations.list.first()
        setDestination(
            SearchResult(
                name = defaultPreset.name,
                address = defaultPreset.description,
                latitude = defaultPreset.latitude,
                longitude = defaultPreset.longitude
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        orientationTracker.stopListening()
        locationTrackingJob?.cancel()
        simulationJob?.cancel()
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = isGranted) }
        if (isGranted) {
            startLocationTracking()
        } else {
            // Provide a default fallback location (e.g. New Delhi) so app is immediately usable
            setFallbackCurrentLocation()
        }
    }

    private fun setFallbackCurrentLocation() {
        val fallback = Location("fallback").apply {
            latitude = 28.6139
            longitude = 77.2090
            altitude = 216.0
            accuracy = 12f
            time = System.currentTimeMillis()
        }
        updateCurrentLocation(fallback)
        viewModelScope.launch {
            val addr = locationClient.reverseGeocode(fallback.latitude, fallback.longitude)
            _uiState.update { it.copy(currentAddress = addr) }
        }
    }

    fun startLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = viewModelScope.launch {
            // Try last known location first for instantaneous response
            val lastLoc = locationClient.getLastKnownLocation()
            if (lastLoc != null) {
                updateCurrentLocation(lastLoc)
                val addr = locationClient.reverseGeocode(lastLoc.latitude, lastLoc.longitude)
                _uiState.update { it.copy(currentAddress = addr) }
            }

            locationClient.getLocationUpdates(2000L).collectLatest { loc ->
                if (!_uiState.value.isSimulatingMovement) {
                    updateCurrentLocation(loc)
                    val addr = locationClient.reverseGeocode(loc.latitude, loc.longitude)
                    _uiState.update { it.copy(currentAddress = addr) }
                }
            }
        }
    }

    fun updateCurrentLocation(location: Location) {
        val state = _uiState.value
        val newCalc = if (state.isFixedDistanceMode && state.fixedOrigin != null && state.destination != null) {
            // Keep fixed distance between Point A and Point B
            val fixedLoc = Location("fixed").apply {
                latitude = state.fixedOrigin.latitude
                longitude = state.fixedOrigin.longitude
                speed = 0f
                accuracy = 0f
            }
            computeCalculation(fixedLoc, state.destination, state.deviceAzimuth)
        } else if (state.destination != null) {
            computeCalculation(location, state.destination, state.deviceAzimuth)
        } else null

        val delta = if (newCalc != null && previousDistance != null) {
            newCalc.aerialDistanceMeters - previousDistance!!
        } else 0.0

        if (newCalc != null) {
            previousDistance = newCalc.aerialDistanceMeters
        }

        val proximityMsg = if (newCalc != null && newCalc.aerialDistanceMeters < 50) {
            "Aap manzil ke bilkul paas hain! (Reached destination)"
        } else state.statusMessage

        _uiState.update {
            it.copy(
                currentLocation = location,
                calculation = newCalc,
                distanceDelta = delta,
                statusMessage = proximityMsg
            )
        }
    }

    fun recalculateDistance() {
        val state = _uiState.value
        val dest = state.destination ?: return
        val originLoc = if (state.isFixedDistanceMode && state.fixedOrigin != null) {
            Location("fixed").apply {
                latitude = state.fixedOrigin.latitude
                longitude = state.fixedOrigin.longitude
                speed = 0f
                accuracy = 0f
            }
        } else {
            state.currentLocation
        }

        if (originLoc != null) {
            val newCalc = computeCalculation(originLoc, dest, state.deviceAzimuth)
            _uiState.update { it.copy(calculation = newCalc) }
            fetchStreetRoute(originLoc.latitude, originLoc.longitude, dest.latitude, dest.longitude)
        }
    }

    private var streetRouteJob: Job? = null

    fun fetchStreetRoute(startLat: Double, startLng: Double, destLat: Double, destLng: Double) {
        streetRouteJob?.cancel()
        streetRouteJob = viewModelScope.launch {
            _uiState.update { it.copy(isFetchingRoute = true) }
            val mode = _uiState.value.travelMode
            val route = RouteService.fetchRoute(startLat, startLng, destLat, destLng, mode)
            if (route != null) {
                _uiState.update { current ->
                    val updatedCalc = current.calculation?.copy(
                        landDistanceMeters = route.distanceMeters,
                        driveTimeMinutes = if (current.travelMode == TravelMode.DRIVING) (route.durationSeconds / 60.0).roundToInt().coerceAtLeast(1) else current.calculation.driveTimeMinutes,
                        walkTimeMinutes = if (current.travelMode == TravelMode.WALKING) (route.durationSeconds / 60.0).roundToInt().coerceAtLeast(1) else current.calculation.walkTimeMinutes
                    )
                    current.copy(
                        realRouteResult = route,
                        calculation = updatedCalc,
                        isFetchingRoute = false,
                        statusMessage = "🛣️ सड़क मार्ग मिला: ${route.summaryRoad} (${GeoUtils.formatDistanceValue(route.distanceMeters, current.selectedUnit)} ${current.selectedUnit.shortLabel})"
                    )
                }
            } else {
                _uiState.update { it.copy(isFetchingRoute = false) }
            }
        }
    }

    fun setTravelMode(mode: TravelMode) {
        _uiState.update { it.copy(travelMode = mode, statusMessage = "यात्रा मोड: ${mode.label}") }
        recalculateDistance()
    }

    fun applyRealRouteResult(route: RouteResult) {
        _uiState.update { current ->
            val updatedCalc = current.calculation?.copy(
                landDistanceMeters = route.distanceMeters,
                driveTimeMinutes = if (current.travelMode == TravelMode.DRIVING) (route.durationSeconds / 60.0).roundToInt().coerceAtLeast(1) else current.calculation?.driveTimeMinutes ?: 0,
                walkTimeMinutes = if (current.travelMode == TravelMode.WALKING) (route.durationSeconds / 60.0).roundToInt().coerceAtLeast(1) else current.calculation?.walkTimeMinutes ?: 0
            )
            current.copy(
                realRouteResult = route,
                calculation = updatedCalc,
                isFetchingRoute = false
            )
        }
    }

    fun toggleFixedDistanceMode() {
        val currentFixed = _uiState.value.isFixedDistanceMode
        val nextFixed = !currentFixed
        if (nextFixed && _uiState.value.fixedOrigin == null) {
            // Auto lock current location as origin if none set
            val curLoc = _uiState.value.currentLocation
            val curAddr = _uiState.value.currentAddress
            if (curLoc != null) {
                val origin = SearchResult(
                    name = "Point A (प्रारंभिक बिंदु - Fixed)",
                    address = curAddr,
                    latitude = curLoc.latitude,
                    longitude = curLoc.longitude
                )
                _uiState.update {
                    it.copy(
                        isFixedDistanceMode = true,
                        fixedOrigin = origin,
                        statusMessage = "🔒 फिक्स दूरी मोड चालू: Point A और Point B के बीच स्थिर दूरी लॉक हो गई है!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isFixedDistanceMode = true,
                        statusMessage = "🔒 फिक्स दूरी मोड चालू (Fixed Distance Mode)"
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    isFixedDistanceMode = nextFixed,
                    statusMessage = if (nextFixed) "🔒 फिक्स दूरी मोड चालू (Fixed Distance Mode)" else "📍 लाइव जीपीएस ट्रैकिंग फिर से शुरू (Live Tracking Resumed)"
                )
            }
        }
        recalculateDistance()
    }

    fun lockOriginToCurrentLocation() {
        val curLoc = _uiState.value.currentLocation ?: return
        val curAddr = _uiState.value.currentAddress
        val origin = SearchResult(
            name = "Point A (स्थान लॉक किया गया)",
            address = curAddr,
            latitude = curLoc.latitude,
            longitude = curLoc.longitude
        )
        _uiState.update {
            it.copy(
                isFixedDistanceMode = true,
                fixedOrigin = origin,
                statusMessage = "📌 वर्तमान स्थान को Point A (आरंभिक बिंदु) लॉक कर दिया गया!"
            )
        }
        recalculateDistance()
    }

    fun swapOriginAndDestination() {
        val state = _uiState.value
        val curDest = state.destination
        val curOrigin = state.fixedOrigin ?: run {
            state.currentLocation?.let {
                SearchResult(
                    name = "Point A (आरंभिक स्थान)",
                    address = state.currentAddress,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
        }

        if (curDest != null && curOrigin != null) {
            _uiState.update {
                it.copy(
                    isFixedDistanceMode = true,
                    fixedOrigin = curDest,
                    destination = curOrigin,
                    statusMessage = "🔄 Point A ⇄ Point B बदल दिए गए!"
                )
            }
            recalculateDistance()
        }
    }

    fun setFixedOrigin(origin: SearchResult) {
        _uiState.update {
            it.copy(
                isFixedDistanceMode = true,
                fixedOrigin = origin,
                statusMessage = "Point A सेट हो गया: ${origin.name}"
            )
        }
        recalculateDistance()
    }

    fun openOriginPicker() {
        _uiState.update { it.copy(isPickingOrigin = true) }
    }

    fun openDestinationPicker() {
        _uiState.update { it.copy(isPickingOrigin = false) }
    }

    fun cycleUnit() {
        val units = DistanceUnit.values()
        val currentIndex = units.indexOf(_uiState.value.selectedUnit)
        val nextUnit = units[(currentIndex + 1) % units.size]
        setUnit(nextUnit)
        _uiState.update { it.copy(statusMessage = "इकाई बदली: ${nextUnit.label}") }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun computeCalculation(
        currentLocation: Location,
        destination: SearchResult,
        azimuth: Float
    ): CalculationSummary {
        val aerialMeters = GeoUtils.calculateAerialDistanceMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            destination.latitude,
            destination.longitude
        )
        val realRoute = _uiState.value.realRouteResult
        val landMeters = realRoute?.distanceMeters ?: GeoUtils.estimateLandDistanceMeters(aerialMeters, factor = _uiState.value.customRoadFactor)
        val bearing = GeoUtils.calculateBearing(
            currentLocation.latitude,
            currentLocation.longitude,
            destination.latitude,
            destination.longitude
        )
        val cardinal = GeoUtils.bearingToCardinal(bearing)
        val shortCard = GeoUtils.bearingToShortCardinal(bearing)
        val relative = (bearing - azimuth + 360f) % 360f

        val speedKmH = currentLocation.speed * 3.6f
        val etaLive = if (speedKmH > 3f) {
            val hours = (aerialMeters / 1000.0) / speedKmH
            val mins = (hours * 60).toInt()
            GeoUtils.formatDuration(mins)
        } else null

        val driveMins = if (realRoute != null && _uiState.value.travelMode == TravelMode.DRIVING) {
            (realRoute.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
        } else {
            GeoUtils.estimateDriveTimeMinutes(landMeters)
        }
        val walkMins = if (realRoute != null && _uiState.value.travelMode == TravelMode.WALKING) {
            (realRoute.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
        } else {
            GeoUtils.estimateWalkingTimeMinutes(landMeters)
        }

        return CalculationSummary(
            aerialDistanceMeters = aerialMeters,
            landDistanceMeters = landMeters,
            bearingDegrees = bearing,
            cardinalDirection = cardinal,
            shortCardinal = shortCard,
            relativeBearingDegrees = relative,
            flightTimeMinutes = GeoUtils.estimateFlightTimeMinutes(aerialMeters),
            driveTimeMinutes = driveMins,
            trainTimeMinutes = GeoUtils.estimateTrainTimeMinutes(landMeters),
            walkTimeMinutes = walkMins,
            speedKmH = speedKmH,
            etaAtCurrentSpeed = etaLive
        )
    }

    fun setDestination(dest: SearchResult) {
        _uiState.update { it.copy(destination = dest, searchQuery = "") }
        recalculateDistance()

        // Save to Room history
        viewModelScope.launch {
            destinationDao.insertDestination(
                DestinationEntity(
                    name = dest.name,
                    address = dest.address,
                    latitude = dest.latitude,
                    longitude = dest.longitude
                )
            )
        }
    }

    fun setOrigin(origin: SearchResult) {
        _uiState.update {
            it.copy(
                fixedOrigin = origin,
                isFixedDistanceMode = true,
                searchQuery = "",
                statusMessage = "Point A चुना गया: ${origin.name}"
            )
        }
        recalculateDistance()
    }

    fun setManualOrigin(name: String, lat: Double, lng: Double) {
        setOrigin(
            SearchResult(
                name = name.ifBlank { "Point A (${lat}, ${lng})" },
                address = String.format("Lat: %.4f, Lng: %.4f", lat, lng),
                latitude = lat,
                longitude = lng
            )
        )
    }

    fun setDestinationFromPreset(preset: DestinationPreset) {
        setDestination(
            SearchResult(
                name = preset.name,
                address = preset.description,
                latitude = preset.latitude,
                longitude = preset.longitude
            )
        )
    }

    fun setManualDestination(name: String, lat: Double, lng: Double) {
        setDestination(
            SearchResult(
                name = name.ifBlank { "Custom Target (${lat}, ${lng})" },
                address = String.format("Lat: %.4f, Lng: %.4f", lat, lng),
                latitude = lat,
                longitude = lng
            )
        )
    }

    fun setManualCurrentLocation(lat: Double, lng: Double) {
        val customLoc = Location("custom").apply {
            latitude = lat
            longitude = lng
            accuracy = 5f
            time = System.currentTimeMillis()
        }
        updateCurrentLocation(customLoc)
        viewModelScope.launch {
            val addr = locationClient.reverseGeocode(lat, lng)
            _uiState.update { it.copy(currentAddress = addr) }
        }
    }

    fun setUnit(unit: DistanceUnit) {
        _uiState.update { it.copy(selectedUnit = unit) }
    }

    fun setRoadDetourProfile(profile: RoadDetourProfile) {
        _uiState.update {
            it.copy(
                roadDetourProfile = profile,
                customRoadFactor = profile.factor,
                statusMessage = "मार्ग मॉडल बदला: ${profile.label} (${profile.shortLabel})"
            )
        }
        recalculateDistance()
    }

    fun setCustomRoadFactor(factor: Double) {
        val clamped = factor.coerceIn(1.00, 1.40)
        _uiState.update {
            it.copy(
                customRoadFactor = clamped,
                statusMessage = String.format(java.util.Locale.getDefault(), "गूगल मैप्स कैलिब्रेशन: %.2fx", clamped)
            )
        }
        recalculateDistance()
    }

    fun toggleMapViewVisibility() {
        _uiState.update { it.copy(isMapViewVisible = !it.isMapViewVisible) }
    }

    fun setBothCoordinates(
        originName: String,
        originLat: Double,
        originLng: Double,
        destName: String,
        destLat: Double,
        destLng: Double
    ) {
        val origin = SearchResult(
            name = originName.ifBlank { String.format(java.util.Locale.getDefault(), "Point A (%.4f, %.4f)", originLat, originLng) },
            address = String.format(java.util.Locale.getDefault(), "Lat: %.5f, Lng: %.5f", originLat, originLng),
            latitude = originLat,
            longitude = originLng
        )
        val dest = SearchResult(
            name = destName.ifBlank { String.format(java.util.Locale.getDefault(), "Point B (%.4f, %.4f)", destLat, destLng) },
            address = String.format(java.util.Locale.getDefault(), "Lat: %.5f, Lng: %.5f", destLat, destLng),
            latitude = destLat,
            longitude = destLng
        )
        _uiState.update {
            it.copy(
                isFixedDistanceMode = true,
                fixedOrigin = origin,
                destination = dest,
                statusMessage = "🎯 Point A और Point B के Lat/Lon दर्ज हो गए!"
            )
        }
        recalculateDistance()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.trim().length >= 3) {
            performSearch(query.trim())
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    private var searchJob: Job? = null
    fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(400) // debounce
            val results = locationClient.searchLocation(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun toggleSimulation() {
        val currentlySimulating = _uiState.value.isSimulatingMovement
        if (currentlySimulating) {
            simulationJob?.cancel()
            _uiState.update { it.copy(isSimulatingMovement = false) }
            if (_uiState.value.hasLocationPermission) {
                startLocationTracking()
            }
        } else {
            val dest = _uiState.value.destination ?: return
            val currentLoc = _uiState.value.currentLocation ?: run {
                setFallbackCurrentLocation()
                _uiState.value.currentLocation!!
            }
            _uiState.update { it.copy(isSimulatingMovement = true) }

            simulationJob?.cancel()
            simulationJob = viewModelScope.launch {
                var curLat = currentLoc.latitude
                var curLng = currentLoc.longitude
                val speedKmh = _uiState.value.simulatedSpeedKmH

                while (isActive) {
                    delay(1000)
                    val distanceM = GeoUtils.calculateAerialDistanceMeters(
                        curLat, curLng, dest.latitude, dest.longitude
                    )
                    if (distanceM <= 30.0) {
                        _uiState.update {
                            it.copy(
                                isSimulatingMovement = false,
                                statusMessage = "Manzil par pahunch gaye! (Destination reached!)"
                            )
                        }
                        break
                    }

                    // Move step towards destination
                    val bearing = GeoUtils.calculateBearing(curLat, curLng, dest.latitude, dest.longitude)
                    val stepMeters = (speedKmh * 1000.0) / 3600.0 // meters per second

                    val bearingRad = Math.toRadians(bearing.toDouble())
                    val dLat = (stepMeters * cos(bearingRad)) / 111320.0
                    val dLng = (stepMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(curLat)))

                    curLat += dLat
                    curLng += dLng

                    val updatedLoc = Location("simulated").apply {
                        latitude = curLat
                        longitude = curLng
                        speed = (speedKmh / 3.6f)
                        accuracy = 2.0f
                        time = System.currentTimeMillis()
                    }
                    updateCurrentLocation(updatedLoc)
                }
            }
        }
    }

    fun setSimulatedSpeed(speedKmH: Float) {
        _uiState.update { it.copy(simulatedSpeedKmH = speedKmH) }
    }

    fun toggleFavorite(destination: DestinationEntity) {
        viewModelScope.launch {
            destinationDao.setFavorite(destination.id, !destination.isFavorite)
        }
    }

    fun deleteSaved(id: Long) {
        viewModelScope.launch {
            destinationDao.deleteDestination(id)
        }
    }
}
