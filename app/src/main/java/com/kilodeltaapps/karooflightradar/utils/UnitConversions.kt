package com.kilodeltaapps.karooflightradar.utils

import com.kilodeltaapps.karooflightradar.utils.AppSettings.AltitudeUnits
import com.kilodeltaapps.karooflightradar.utils.AppSettings.DistanceUnits
import kotlin.math.roundToInt
import kotlin.math.abs

object UnitConversions {

    // --- Conversion Constants ---
    private const val METERS_PER_FOOT = 0.3048
    private const val KMH_PER_KNOT = 1.852
    private const val METERS_PER_NM = 1852.0
    private const val SECONDS_PER_MINUTE = 60.0

    // --- Raw Calculations ---

    fun metersToFeet(meters: Double): Double = meters / METERS_PER_FOOT
    fun feetToMeters(feet: Double): Double = feet * METERS_PER_FOOT

    fun metersToNm(meters: Double): Double = meters / METERS_PER_NM
    fun nmToMeters(nm: Double): Double = nm * METERS_PER_NM

    fun kmhToKnots(kmh: Double): Double = kmh / KMH_PER_KNOT
    fun knotsToKmh(knots: Double): Double = knots * KMH_PER_KNOT

    /**
     * Converts m/s to ft/min
     */
    fun mpsToFpm(mps: Double): Double = (mps / METERS_PER_FOOT) * SECONDS_PER_MINUTE

    // --- Formatted Strings (For UI Labels) ---

    fun formatAltitude(meters: Double, unit: AltitudeUnits): String {
        return when (unit) {
            AltitudeUnits.METERS -> "${meters.toInt()}m"
            AltitudeUnits.FEET -> "${metersToFeet(meters).roundToInt()}ft"
        }
    }

    fun formatSpeed(kmh: Double, unit: DistanceUnits): String {
        return when (unit) {
            DistanceUnits.METRIC -> "${kmh.toInt()}kmh"
            DistanceUnits.IMPERIAL -> "${kmhToKnots(kmh).roundToInt()}kts"
        }
    }

    fun formatVerticalSpeed(mps: Double, unit: AltitudeUnits): String {
        // If VS is negligible, return empty
        if (abs(mps) < 0.2) return ""

        return when (unit) {
            AltitudeUnits.METERS -> {
                // Format: +5.2 or -3.1
                val formatted = "%.1f".format(mps)
                if (mps > 0) "+$formatted" else formatted
            }
            AltitudeUnits.FEET -> {
                // Format: +500 or -500
                val fpm = mpsToFpm(mps).toInt()
                if (fpm > 0) "+$fpm" else "$fpm"
            }
        }
    }

    /**
     * Returns distance in the user's preferred unit for Range Rings.
     * @return Pair(Value, UnitLabel) e.g., (10, "km") or (5, "nm")
     */
    fun getDistanceValueAndLabel(meters: Double, unit: DistanceUnits): Pair<Int, String> {
        return when (unit) {
            DistanceUnits.METRIC -> {
                Pair((meters / 1000.0).toInt(), "km")
            }
            DistanceUnits.IMPERIAL -> {
                Pair(metersToNm(meters).toInt(), "nm")
            }
        }
    }
}