package com.kilodeltaapps.karooflightradar.utils

import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.Coord
import java.util.concurrent.ConcurrentHashMap

class AircraftTrailManager {

    private data class TrailPoint(
        val coord: Coord,
        val timestamp: Long
    )

    // Map of Hex ID -> List of Points
    private val trails = ConcurrentHashMap<String, ArrayDeque<TrailPoint>>()

    /**
     * Updates trails for visible aircraft and prunes old data.
     * @param aircraftList The current list of active aircraft.
     * @param maxAgeSeconds Points older than this will be removed.
     */
    fun updateTrails(aircraftList: List<Aircraft>, maxAgeSeconds: Int) {
        val now = System.currentTimeMillis()
        val expirationThreshold = now - (maxAgeSeconds * 1000)

        // 1. Add new points for current aircraft
        aircraftList.forEach { aircraft ->
            val history = trails.getOrPut(aircraft.id) { ArrayDeque() }

            // Only add point if it has moved significantly (e.g., > 10 meters) or if history is empty
            // This prevents bunching up points when hovering/stationary
            if (history.isEmpty() || shouldAddPoint(history.last().coord, aircraft.coord)) {
                history.add(TrailPoint(aircraft.coord, now))
            }
        }

        // 2. Prune old points and remove empty trails
        val iterator = trails.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val history = entry.value

            // Remove expired points from the front (oldest)
            while (history.isNotEmpty() && history.first().timestamp < expirationThreshold) {
                history.removeFirst()
            }

            // If aircraft is gone from API list AND trail is empty/expired, remove entry
            // Note: We keep trails for a bit even if aircraft disappears from API momentarily
            if (history.isEmpty()) {
                iterator.remove()
            }
        }
    }

    /**
     * Returns the history of coordinates for a specific aircraft.
     */
    fun getTrail(aircraftId: String): List<Coord> {
        return trails[aircraftId]?.map { it.coord } ?: emptyList()
    }

    private fun shouldAddPoint(last: Coord, current: Coord): Boolean {
        // Simple optimization: roughly 0.0001 deg is ~11 meters
        val diffLat = kotlin.math.abs(last.latitude - current.latitude)
        val diffLon = kotlin.math.abs(last.longitude - current.longitude)
        return diffLat > 0.0001 || diffLon > 0.0001
    }

    fun clear() {
        trails.clear()
    }
}