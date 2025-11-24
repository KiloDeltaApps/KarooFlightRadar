package com.kilodeltaapps.karooflightradar.data

enum class AircraftDataType(val displayName: String) {
    CALLSIGN("Callsign"),
    REGISTRATION("Registration"),
    TYPE("Type"),
    ALTITUDE("Altitude"),
    FLIGHT_LEVEL("Flight Level"),
    SMART_ALTITUDE("Smart Altitude"),
    GROUND_SPEED("Ground Speed"),
    VERTICAL_SPEED("Vertical Speed"),
    HEADING("Heading")
}

enum class LabelDisplayMode(val displayName: String) {
    STATIC_COMBINED("Combined (All at once)"),
    ROLLING("Rolling (Cycle)")
}

data class LabelLineConfig(
    val mode: LabelDisplayMode = LabelDisplayMode.STATIC_COMBINED,
    val fields: List<AircraftDataType> = emptyList()
)

data class LabelConfiguration(
    val topLine: LabelLineConfig = LabelLineConfig(
        mode = LabelDisplayMode.STATIC_COMBINED,
        fields = listOf(AircraftDataType.CALLSIGN)
    ),
    val bottomLine: LabelLineConfig = LabelLineConfig(
        mode = LabelDisplayMode.STATIC_COMBINED,
        fields = listOf(AircraftDataType.ALTITUDE)
    ),
    val smartAltitudeTransitionMeters: Int = 1524 // Default ~5000ft
)