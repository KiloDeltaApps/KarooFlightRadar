package com.kilodeltaapps.karooflightradar.data

/**
 * Represents a single airport with its core, immutable data.
 * This class is decoupled from Android Context and AppSettings.
 */
data class Airport(
    val icao: String,
    val iata: String?,
    val name: String,
    val city: String,
    val state: String?,
    val country: String,
    val elevation: Int, // Assumed to be in meters
    val lat: Double,
    val lon: Double,
    val tz: String
) {
    val coord: Coord = Coord(lat, lon)

    val elevationFeet: Int
        get() = (elevation * 3.28084).toInt()

    val iataOrIcao: String
        get() = if (!iata.isNullOrBlank()) iata else icao

    val cityNameOrName: String
        get() = city.ifBlank { name }

    val locationDescription: String
        get() = listOfNotNull(city, state, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")

    fun hasValidIATA(): Boolean {
        return !iata.isNullOrBlank() && iata != "null" && iata.length in 3..4
    }
}