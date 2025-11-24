package com.kilodeltaapps.karooflightradar.repository
import kotlin.math.abs
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kilodeltaapps.karooflightradar.data.Airport
import com.kilodeltaapps.karooflightradar.utils.Haversine
import com.kilodeltaapps.karooflightradar.data.Coord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object AirfieldRepository {

    private const val TAG = "AirfieldRepository"
    private const val ASSETS_FILE_NAME = "airports.json"

    // In-memory cache of all airports
    private var allAirports: List<Airport> = emptyList()
    private var airportsByIcao: Map<String, Airport> = emptyMap()
    private var airportsByIata: Map<String, Airport> = emptyMap()

    // Preloaded airports for current area
    private var preloadedAirports: List<Airport> = emptyList()
    private var lastPreloadCenter: Coord? = null
    private var lastPreloadRadius: Double = 0.0

    // Cache for radar range queries
    private val radarRangeCache = mutableMapOf<String, List<Pair<Airport, Double>>>()

    /**
     * Initialize the repository by loading all airports from assets
     */
    suspend fun initialize(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = loadJsonFromAssets(context)
                allAirports = parseAirportsJson(jsonString)

                // Build lookup maps for fast access
                airportsByIcao = allAirports.associateBy { it.icao }
                airportsByIata = allAirports.filter { it.hasValidIATA() }.associateBy { it.iata!! }

                Log.d(TAG, "Loaded ${allAirports.size} airports from assets " +
                        "(${airportsByIata.size} with valid IATA codes)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize airport repository: ${e.message}")
                false
            }
        }
    }

    /**
     * Preload airports within a specific radius of a center point
     */
    suspend fun preloadAirports(
        context: Context,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Check if we already have a similar preload
                val center = Coord(centerLat, centerLon)
                if (isSimilarPreload(center, radiusKm)) {
                    Log.d(TAG, "Similar preload exists, skipping")
                    return@withContext
                }

                Log.d(TAG, "Preloading airports within ${radiusKm}km of ($centerLat, $centerLon)")

                preloadedAirports = allAirports.filter { airport ->
                    val distance = Haversine.calculateDistanceMeters(
                        center,
                        Coord(airport.lat, airport.lon)
                    ) / 1000.0 // Convert to km
                    distance <= radiusKm
                }

                lastPreloadCenter = center
                lastPreloadRadius = radiusKm

                // Clear radar range cache since preload changed
                radarRangeCache.clear()

                Log.d(TAG, "Preloaded ${preloadedAirports.size} airports")
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading airports: ${e.message}")
            }
        }
    }

    /**
     * Get airports within radar range (uses preloaded airports if available)
     */
    fun getAirportsWithinRadarRange(
        userLat: Double,
        userLon: Double,
        maxRangeMeters: Double
    ): List<Pair<Airport, Double>> {
        val cacheKey = "${userLat}_${userLon}_${maxRangeMeters}"

        return radarRangeCache.getOrPut(cacheKey) {
            val sourceAirports = if (shouldUsePreloaded(userLat, userLon, maxRangeMeters)) {
                preloadedAirports
            } else {
                allAirports
            }

            val userCoord = Coord(userLat, userLon)
            sourceAirports.mapNotNull { airport ->
                val airportCoord = Coord(airport.lat, airport.lon)
                val distance = Haversine.calculateDistanceMeters(userCoord, airportCoord)

                if (distance <= maxRangeMeters) {
                    airport to distance
                } else {
                    null
                }
            }.sortedBy { it.second } // Sort by distance
        }
    }

    /**
     * Get a specific airport by ICAO code
     */
    fun getAirportByIcao(icao: String): Airport? {
        return airportsByIcao[icao]
    }

    /**
     * Get a specific airport by IATA code
     */
    fun getAirportByIata(iata: String): Airport? {
        return airportsByIata[iata]
    }

    /**
     * Search airports by name, city, or code
     */
    fun searchAirports(query: String, limit: Int = 20): List<Airport> {
        if (query.length < 2) return emptyList()

        val searchTerm = query.trim().lowercase()

        return allAirports.filter { airport ->
            airport.icao.lowercase().contains(searchTerm) ||
                    airport.iata?.lowercase()?.contains(searchTerm) == true ||
                    airport.name.lowercase().contains(searchTerm) ||
                    airport.city.lowercase().contains(searchTerm) ||
                    airport.country.lowercase().contains(searchTerm)
        }.take(limit)
    }

    /**
     * Get airports by country
     */
    fun getAirportsByCountry(country: String): List<Airport> {
        return allAirports.filter { it.country.equals(country, ignoreCase = true) }
    }

    /**
     * Get airports with IATA codes only
     */
    fun getAirportsWithIata(): List<Airport> {
        return allAirports.filter { it.hasValidIATA() }
    }

    /**
     * Get nearest airport to a location
     */
    fun getNearestAirport(lat: Double, lon: Double, maxDistanceKm: Double = 100.0): Pair<Airport, Double>? {
        val userCoord = Coord(lat, lon)

        return allAirports.minByOrNull { airport ->
            Haversine.calculateDistanceMeters(userCoord, Coord(airport.lat, airport.lon))
        }?.let { airport ->
            val distance = Haversine.calculateDistanceMeters(userCoord, Coord(airport.lat, airport.lon)) / 1000.0
            if (distance <= maxDistanceKm) airport to distance else null
        }
    }

    /**
     * Get multiple nearest airports to a location
     */
    fun getNearestAirports(lat: Double, lon: Double, count: Int = 5, maxDistanceKm: Double = 100.0): List<Pair<Airport, Double>> {
        val userCoord = Coord(lat, lon)

        return allAirports.map { airport ->
            val distance = Haversine.calculateDistanceMeters(userCoord, Coord(airport.lat, airport.lon)) / 1000.0
            airport to distance
        }.filter { it.second <= maxDistanceKm }
            .sortedBy { it.second }
            .take(count)
    }

    /**
     * Check if airports should be reloaded based on significant movement
     */
    fun shouldReloadAirports(
        currentLat: Double,
        currentLon: Double,
        reloadThresholdKm: Double
    ): Boolean {
        val lastCenter = lastPreloadCenter ?: return true // No preload exists

        val currentCoord = Coord(currentLat, currentLon)
        val distance = Haversine.calculateDistanceMeters(lastCenter, currentCoord) / 1000.0

        return distance > reloadThresholdKm
    }

    /**
     * Get statistics about loaded airports
     */
    fun getStatistics(): AirportStatistics {
        return AirportStatistics(
            totalAirports = allAirports.size,
            preloadedAirports = preloadedAirports.size,
            countries = allAirports.map { it.country }.distinct().size,
            withIataCodes = allAirports.count { it.hasValidIATA() }
        )
    }

    // Private helper methods

    private fun loadJsonFromAssets(context: Context): String {
        return context.assets.open(ASSETS_FILE_NAME).use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun parseAirportsJson(jsonString: String): List<Airport> {
        val type = object : TypeToken<Map<String, Airport>>() {}.type
        val airportMap: Map<String, Airport> = Gson().fromJson(jsonString, type)
        return airportMap.values.toList()
    }

    private fun isSimilarPreload(newCenter: Coord, newRadius: Double): Boolean {
        val lastCenter = lastPreloadCenter ?: return false

        val distance = Haversine.calculateDistanceMeters(lastCenter, newCenter) / 1000.0
        val radiusDiff = abs(newRadius - lastPreloadRadius)

        // Consider similar if center moved less than 10% of radius and radius changed less than 20%
        return distance < (lastPreloadRadius * 0.1) && radiusDiff < (lastPreloadRadius * 0.2)
    }

    private fun shouldUsePreloaded(userLat: Double, userLon: Double, maxRangeMeters: Double): Boolean {
        if (preloadedAirports.isEmpty()) return false

        val userCoord = Coord(userLat, userLon)
        val lastCenter = lastPreloadCenter ?: return false

        // Use preloaded if user is within preload radius + a small buffer
        val distanceToPreloadCenter = Haversine.calculateDistanceMeters(userCoord, lastCenter) / 1000.0
        val requiredPreloadRadius = (maxRangeMeters / 1000.0) * 1.5 // Buffer of 50%

        return distanceToPreloadCenter <= (lastPreloadRadius - requiredPreloadRadius)
    }

    data class AirportStatistics(
        val totalAirports: Int,
        val preloadedAirports: Int,
        val countries: Int,
        val withIataCodes: Int
    )
}