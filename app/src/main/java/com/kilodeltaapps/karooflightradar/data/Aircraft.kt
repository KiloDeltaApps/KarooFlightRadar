package com.kilodeltaapps.karooflightradar.data

data class Aircraft (
    val id: String,
    val callsign: String?,
    val aircraftType: String?,
    val registration: String?, // Added Registration
    val coord: Coord,
    val altitude: Double, // meters
    val FlightLevel: Int?,
    val verticalSpeed: Double, // m/s
    val groundSpeed: Double, // km/h
    val heading: Double, // degrees
    val lastSeen: Long, // seconds
    val category: String?,
    val isMilitary: Boolean
)