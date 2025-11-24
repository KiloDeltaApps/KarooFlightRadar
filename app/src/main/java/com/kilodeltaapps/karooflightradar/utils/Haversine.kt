package com.kilodeltaapps.karooflightradar.utils

import com.kilodeltaapps.karooflightradar.data.Coord
import kotlin.math.*

/**
 * Provides static methods for calculating geographical distances and bearings.
 */
object Haversine {

    private const val EARTH_RADIUS_M = 6371000.0 // Mean radius of the Earth in meters

    /**
     * Calculates the great-circle distance between two points on the Earth
     * using the Haversine formula.
     *
     * @param coord1 The first coordinate.
     * @param coord2 The second coordinate.
     * @return The distance in meters.
     */
    fun calculateDistanceMeters(coord1: Coord, coord2: Coord): Double {
        val lat1Rad = Math.toRadians(coord1.latitude)
        val lat2Rad = Math.toRadians(coord2.latitude)

        // Differences in latitude and longitude, converted to radians
        val dLat = Math.toRadians(coord2.latitude - coord1.latitude)
        val dLon = Math.toRadians(coord2.longitude - coord1.longitude)

        // Haversine formula core calculation: a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)

        // c = 2 * atan2(sqrt(a), sqrt(1-a)) (Angular distance in radians)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        // Distance = R * c
        return EARTH_RADIUS_M * c
    }

    /**
     * Calculates the initial bearing (forward azimuth) from coordinate 1 to coordinate 2.
     * This is useful for drawing the glider direction on your radar view.
     *
     * @param coord1 The starting coordinate (e.g., Karoo device).
     * @param coord2 The destination coordinate (e.g., Glider).
     * @return The bearing in degrees (0 to 360, where 0 is North).
     */
    fun calculateBearingDegrees(coord1: Coord, coord2: Coord): Double {
        val lat1 = Math.toRadians(coord1.latitude)
        val lon1 = Math.toRadians(coord1.longitude)
        val lat2 = Math.toRadians(coord2.latitude)
        val lon2 = Math.toRadians(coord2.longitude)

        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        // atan2 returns radians from -pi to +pi
        var bearingRad = atan2(y, x)

        // Convert to degrees and normalize to 0-360
        bearingRad = Math.toDegrees(bearingRad)
        return (bearingRad + 360) % 360
    }
}