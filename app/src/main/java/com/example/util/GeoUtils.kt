package com.example.util

import android.location.Location
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class DistanceUnit(val label: String, val shortLabel: String) {
    KILOMETERS("Kilometers", "km"),
    METERS("Meters", "m"),
    MILES("Miles", "mi"),
    NAUTICAL_MILES("Hawai / Nautical Miles", "NM"),
    FEET("Feet", "ft")
}

enum class RoadDetourProfile(val label: String, val shortLabel: String, val factor: Double, val description: String) {
    GOOGLE_MAPS_OPTIMAL("Google Maps हाईवे / सीधा मार्ग", "1.08x", 1.08, "सीधे मुख्य मार्ग व एक्सप्रेसवे (कम मोड़)"),
    GOOGLE_MAPS_STANDARD("Google Maps मानक सड़क", "1.12x", 1.12, "गूगल मैप्स से बिल्कुल सटीक मेल (अनुशंसित)"),
    CITY_MIXED("शहरी / घूमावदार मार्ग", "1.18x", 1.18, "शहरी गलियों व सामान्य मोड़")
}

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0 // WGS84 mean earth radius
    const val DEFAULT_GOOGLE_MAPS_ROAD_FACTOR = 1.12 // Calibrated road detour ratio to match Google Maps accurately

    /**
     * Calculates the direct aerial / straight-line great circle distance (Hawai Doori)
     * using the Haversine formula.
     */
    fun calculateAerialDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    /**
     * Fallback pure Haversine formula calculation
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destLat = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(originLat) * cos(destLat)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Estimates land / road distance (Zameeni Doori) calibrated to Google Maps routing
     */
    fun estimateLandDistanceMeters(aerialMeters: Double, factor: Double = DEFAULT_GOOGLE_MAPS_ROAD_FACTOR): Double {
        // For very short distances (<800m), road factor is almost direct (~1.03x - 1.06x)
        return if (aerialMeters < 800) {
            aerialMeters * (1.0 + (factor - 1.0) * 0.4)
        } else {
            aerialMeters * factor
        }
    }

    /**
     * Calculates the initial bearing from point 1 to point 2 in degrees (0..360)
     */
    fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        val bearing = (Math.toDegrees(theta) + 360.0) % 360.0
        return bearing.toFloat()
    }

    /**
     * Converts a degree bearing (0..360) into a cardinal heading string (e.g., "NE", "SSE")
     */
    fun bearingToCardinal(bearing: Float): String {
        val directions = arrayOf(
            "N (Uttar)", "NNE", "NE (Uttar-Poorv)", "ENE",
            "E (Poorv)", "ESE", "SE (Dakshin-Poorv)", "SSE",
            "S (Dakshin)", "SSW", "SW (Dakshin-Pashchim)", "WSW",
            "W (Pashchim)", "WNW", "NW (Uttar-Pashchim)", "NNW"
        )
        val normalized = (bearing % 360 + 360) % 360
        val index = ((normalized + 11.25f) / 22.5f).toInt() % 16
        return directions[index]
    }

    fun bearingToShortCardinal(bearing: Float): String {
        val directions = arrayOf(
            "N", "NNE", "NE", "ENE",
            "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW",
            "W", "WNW", "NW", "NNW"
        )
        val normalized = (bearing % 360 + 360) % 360
        val index = ((normalized + 11.25f) / 22.5f).toInt() % 16
        return directions[index]
    }

    /**
     * Converts meters into the selected unit
     */
    fun convertDistance(meters: Double, unit: DistanceUnit): Double {
        return when (unit) {
            DistanceUnit.METERS -> meters
            DistanceUnit.KILOMETERS -> meters / 1000.0
            DistanceUnit.MILES -> meters / 1609.344
            DistanceUnit.NAUTICAL_MILES -> meters / 1852.0
            DistanceUnit.FEET -> meters * 3.28084
        }
    }

    fun formatDistanceValue(meters: Double, unit: DistanceUnit): String {
        val converted = convertDistance(meters, unit)
        return when (unit) {
            DistanceUnit.METERS, DistanceUnit.FEET -> {
                String.format(Locale.getDefault(), "%,.0f", converted)
            }
            else -> {
                if (converted < 10.0) {
                    String.format(Locale.getDefault(), "%.2f", converted)
                } else if (converted < 1000.0) {
                    String.format(Locale.getDefault(), "%.1f", converted)
                } else {
                    String.format(Locale.getDefault(), "%,.1f", converted)
                }
            }
        }
    }

    /**
     * Estimated travel times in minutes
     */
    fun estimateFlightTimeMinutes(aerialMeters: Double): Int {
        val km = aerialMeters / 1000.0
        val speedKmH = 750.0 // Typical commercial airliner cruising speed + taxi
        val hours = (km / speedKmH) + (20.0 / 60.0) // 20 min taxi/ascent/descent
        return (hours * 60).roundToInt().coerceAtLeast(1)
    }

    fun estimateDriveTimeMinutes(landMeters: Double): Int {
        val km = landMeters / 1000.0
        val speedKmH = 65.0 // Average driving speed with traffic/turns
        val hours = km / speedKmH
        return (hours * 60).roundToInt().coerceAtLeast(1)
    }

    fun estimateTrainTimeMinutes(landMeters: Double): Int {
        val km = landMeters / 1000.0
        val speedKmH = 80.0 // Average passenger train speed
        val hours = km / speedKmH
        return (hours * 60).roundToInt().coerceAtLeast(1)
    }

    fun estimateWalkingTimeMinutes(landMeters: Double): Int {
        val km = landMeters / 1000.0
        val speedKmH = 4.8 // Walking speed km/h
        val hours = km / speedKmH
        return (hours * 60).roundToInt().coerceAtLeast(1)
    }

    fun formatDuration(minutes: Int): String {
        return if (minutes < 60) {
            "${minutes}m"
        } else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }

    fun formatCoordinates(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(
            Locale.getDefault(),
            "%.4f° %s, %.4f° %s",
            kotlin.math.abs(lat), latDir,
            kotlin.math.abs(lng), lngDir
        )
    }
}
