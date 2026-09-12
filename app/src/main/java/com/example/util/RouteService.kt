package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class RouteStep(
    val instruction: String,
    val streetName: String,
    val distanceMeters: Double,
    val durationSeconds: Double
)

data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val summaryRoad: String,
    val steps: List<RouteStep>,
    val geometryGeoJson: String? = null
)

enum class TravelMode(val apiValue: String, val label: String, val icon: String, val description: String) {
    DRIVING("driving", "कार / मुख्य सड़क", "🚗", "राजमार्ग और मुख्य सड़कें"),
    WALKING("walking", "पैदल / गली-मोहल्ला", "🚶", "तंग गलियां, पैदल मार्ग व शॉर्टकट"),
    BICYCLE("bicycle", "साइकिल / दोपहिया", "🚲", "सामान्य सड़कें और गलियां")
}

object RouteService {

    suspend fun fetchRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TravelMode = TravelMode.DRIVING
    ): RouteResult? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://router.project-osrm.org/route/v1/${mode.apiValue}/$startLng,$startLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DistanceTracker/1.0 (Android)")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                if (root.optString("code") == "Ok") {
                    val routes = root.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val totalDistance = route.optDouble("distance", 0.0)
                        val totalDuration = route.optDouble("duration", 0.0)
                        val geometryObj = route.optJSONObject("geometry")
                        val geometryStr = geometryObj?.toString()

                        val legs = route.optJSONArray("legs")
                        var summary = ""
                        val stepsList = mutableListOf<RouteStep>()

                        if (legs != null && legs.length() > 0) {
                            val leg = legs.getJSONObject(0)
                            summary = leg.optString("summary", "")
                            val steps = leg.optJSONArray("steps")
                            if (steps != null) {
                                for (i in 0 until steps.length()) {
                                    val step = steps.getJSONObject(i)
                                    val name = step.optString("name", "").trim()
                                    val dist = step.optDouble("distance", 0.0)
                                    val dur = step.optDouble("duration", 0.0)
                                    val maneuver = step.optJSONObject("maneuver")
                                    val type = maneuver?.optString("type", "") ?: ""
                                    val modifier = maneuver?.optString("modifier", "") ?: ""
                                    val instruction = buildInstruction(type, modifier, name, dist)
                                    stepsList.add(RouteStep(instruction, name, dist, dur))
                                }
                            }
                        }

                        if (summary.isBlank() && stepsList.isNotEmpty()) {
                            val uniqueNames = stepsList.map { it.streetName }.filter { it.isNotBlank() }.distinct()
                            summary = uniqueNames.take(3).joinToString(" → ")
                        }

                        return@withContext RouteResult(
                            distanceMeters = totalDistance,
                            durationSeconds = totalDuration,
                            summaryRoad = summary.ifBlank { "सड़क मार्ग" },
                            steps = stepsList,
                            geometryGeoJson = geometryStr
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun buildInstruction(type: String, modifier: String, name: String, distMeters: Double): String {
        val roadLabel = if (name.isNotBlank()) "«$name»" else "सड़क"
        val distLabel = if (distMeters >= 1000) {
            String.format(Locale.US, "%.1f km", distMeters / 1000)
        } else {
            "${distMeters.toInt()} m"
        }

        val dirHindi = when (modifier) {
            "left" -> "बाएं मुड़ें"
            "right" -> "दाएं मुड़ें"
            "sharp left" -> "तेज बाएं मुड़ें"
            "sharp right" -> "तेज दाएं मुड़ें"
            "slight left" -> "हल्का बाएं मुड़ें"
            "slight right" -> "हल्का दाएं मुड़ें"
            "straight" -> "सीधे चलें"
            "uturn" -> "यू-टर्न (U-Turn) लें"
            else -> ""
        }

        return when (type) {
            "depart" -> "$roadLabel से यात्रा प्रारंभ करें ($distLabel)"
            "arrive" -> "मंज़िल पर पहुंचे ($roadLabel)"
            "turn" -> if (dirHindi.isNotBlank()) "$dirHindi $roadLabel पर ($distLabel आगे बढ़ें)" else "$roadLabel की ओर मुड़ें ($distLabel)"
            "new name" -> "$roadLabel पर सीधे आगे जारी रखें ($distLabel)"
            "roundabout" -> "गोल चक्कर (चौराहा) से $roadLabel पर निकलें"
            "fork" -> "$dirHindi कांटा मार्ग (Fork) से $roadLabel चुनें ($distLabel)"
            else -> if (dirHindi.isNotBlank()) "$dirHindi $roadLabel ($distLabel)" else "$roadLabel पर चलें ($distLabel)"
        }
    }
}
