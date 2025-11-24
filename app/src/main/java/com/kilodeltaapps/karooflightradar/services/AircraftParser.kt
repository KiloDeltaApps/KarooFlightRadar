package com.kilodeltaapps.karooflightradar.services

import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.Coord
import com.google.gson.annotations.SerializedName

object AircraftParser {

    data class AirplanesLiveResponse(
        @SerializedName("ac") val aircraft: List<AircraftJson>?
    )

    data class AircraftJson(
        @SerializedName("hex") val hex: String,
        @SerializedName("flight") val flight: String?,
        @SerializedName("r") val registration: String?,
        @SerializedName("t") val type: String?,
        @SerializedName("lat") val lat: Double?,
        @SerializedName("lon") val lon: Double?,
        @SerializedName("gs") val groundSpeedKnots: Double?,
        @SerializedName("track") val track: Double?,
        @SerializedName("alt_baro") val altitudeBaro: Any?,
        @SerializedName("baro_rate") val verticalSpeedFpm: Double?,
        @SerializedName("category") val category: String?,
        @SerializedName("dbFlags") val dbFlags: Int?          // <-- NEW
    )

    fun parseAircraftList(jsonString: String): List<Aircraft> {
        return try {
            val response = com.google.gson.Gson().fromJson(jsonString, AirplanesLiveResponse::class.java)
            response.aircraft?.mapNotNull { parseAircraft(it) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseAircraft(ac: AircraftJson): Aircraft? {
        return try {
            val altitudeMeters = when (ac.altitudeBaro) {
                is Double -> ac.altitudeBaro * 0.3048
                is String -> if (ac.altitudeBaro == "ground") 0.0 else ac.altitudeBaro.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val groundSpeedKmh = (ac.groundSpeedKnots ?: 0.0) * 1.852
            val verticalSpeedMps = (ac.verticalSpeedFpm ?: 0.0) * 0.00508
            val convertedFlightLevel = toFlightLevel(ac.altitudeBaro)

            val displayId = ac.flight?.trim()?.ifBlank { null }
                ?: ac.registration?.trim()
                ?: ac.hex

            /* ---------- military flag ---------- */
            val isMil = ac.dbFlags == 1   // only true when field exists and equals 1

            Aircraft(
                id = ac.hex,
                callsign = displayId,
                aircraftType = ac.type,
                registration = ac.registration,
                coord = Coord(ac.lat ?: 0.0, ac.lon ?: 0.0),
                altitude = altitudeMeters,
                FlightLevel = convertedFlightLevel,
                verticalSpeed = verticalSpeedMps,
                groundSpeed = groundSpeedKmh,
                heading = ac.track ?: 0.0,
                lastSeen = System.currentTimeMillis() / 1000,
                category = ac.category,
                isMilitary = isMil
            )
        } catch (e: Exception) {
            null
        }
    }
}

fun toFlightLevel(altitude: Any?): Int? {
    if (altitude == null) return null
    if (altitude is String && altitude.lowercase() == "ground") return null
    val altitudeFt = when (altitude) {
        is Number -> altitude.toDouble()
        is String -> altitude.toDoubleOrNull() ?: return null
        else -> return null
    }
    return (altitudeFt / 100).toInt()
}