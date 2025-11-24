package com.kilodeltaapps.karooflightradar.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.kilodeltaapps.karooflightradar.data.AirfieldDisplayFormat
import com.kilodeltaapps.karooflightradar.data.AircraftDataType
import com.kilodeltaapps.karooflightradar.data.LabelDisplayMode

/**
 * Centralized application settings management
 * Uses SharedPreferences for persistence
 */
object AppSettings {

    private const val PREFS_NAME = "flight_radar_settings"

    // Setting keys
    private const val KEY_AUTO_RELOAD_AIRPORTS = "auto_reload_airports"
    private const val KEY_SHOW_AIRPORTS = "show_airports"
    private const val KEY_AIRPORT_PRELOAD_RADIUS = "airport_preload_radius"
    private const val KEY_API_SEARCH_RANGE = "api_search_range"
    private const val KEY_API_POLLING_INTERVAL = "api_polling_interval"
    private const val KEY_RADAR_VIEW_RANGE = "radar_view_range"
    private const val KEY_LABEL_TEXT_SIZE = "label_text_size"
    private const val KEY_NORTH_UP_ORIENTATION = "north_up_orientation"
    private const val KEY_RADAR_REDRAW_INTERVAL = "radar_redraw_interval"
    private const val KEY_AIRPORT_DISPLAY_FORMAT = "airport_display_format"
    private const val KEY_SIMULATION_ENABLED = "simulation_enabled"
    private const val KEY_SIMULATION_LATITUDE = "simulation_latitude"
    private const val KEY_SIMULATION_LONGITUDE = "simulation_longitude"
    private const val KEY_SIMULATION_HEADING = "simulation_heading"
    private const val KEY_FILTER_GLIDERS_ONLY = "filter_gliders_only"
    private const val KEY_SHOW_AIRCRAFT_TRAILS = "show_aircraft_trails"
    private const val KEY_TRAIL_LENGTH = "trail_length"
    private const val KEY_DISTANCE_UNITS = "distance_units"
    private const val KEY_ALTITUDE_UNITS = "altitude_units"

    private const val KEY_SHOW_OWNSHIP = "show_ownship"
    private const val KEY_SHOW_NOTIFICATION = "show_notification"

    // Aircraft Filter Settings
    private const val KEY_MINIMUM_ALTITUDE = "minimum_altitude"
    private const val KEY_MAXIMUM_DISTANCE = "maximum_distance"
    private const val KEY_MAXIMUM_AIRCRAFT_AGE = "maximum_aircraft_age"
    private const val KEY_AIRCRAFT_TYPE_FILTER_MODE = "aircraft_type_filter_mode"
    private const val KEY_SELECTED_AIRCRAFT_TYPES = "selected_aircraft_types"
    private const val KEY_AIRCRAFT_PRIORITY = "aircraft_priority"

    // Default values
    private const val DEFAULT_AUTO_RELOAD_AIRPORTS = true
    private const val DEFAULT_SHOW_AIRPORTS = true
    private const val DEFAULT_AIRPORT_PRELOAD_RADIUS = 100 // km
    private const val DEFAULT_API_SEARCH_RANGE = 50 // nautical miles
    private const val DEFAULT_API_POLLING_INTERVAL = 10 // seconds
    private const val DEFAULT_RADAR_VIEW_RANGE = 25000 //km
    private const val DEFAULT_LABEL_TEXT_SIZE = 14 // sp
    private const val DEFAULT_NORTH_UP_ORIENTATION = false
    private const val DEFAULT_RADAR_REDRAW_INTERVAL = 2 // seconds
    private const val DEFAULT_AIRPORT_DISPLAY_FORMAT = "IATA"
    private const val DEFAULT_SIMULATION_ENABLED = false
    private const val DEFAULT_SIMULATION_LATITUDE = 37.7749 // San Francisco
    private const val DEFAULT_SIMULATION_LONGITUDE = -122.4194
    private const val DEFAULT_SIMULATION_HEADING = 0.0
    private const val DEFAULT_FILTER_GLIDERS_ONLY = false
    private const val DEFAULT_SHOW_AIRCRAFT_TRAILS = true
    private const val DEFAULT_TRAIL_LENGTH = 60 // seconds
    private const val DEFAULT_DISTANCE_UNITS = "METRIC" // METRIC or IMPERIAL
    private const val DEFAULT_ALTITUDE_UNITS = "METERS" // METERS or FEET

    // Default values for aircraft filtering
    private const val DEFAULT_MINIMUM_ALTITUDE = 0 // meters
    private const val DEFAULT_MAXIMUM_DISTANCE = 0 // meters (0 = no limit)
    private const val DEFAULT_MAXIMUM_AIRCRAFT_AGE = 300 // seconds (5 minutes)
    private const val DEFAULT_AIRCRAFT_TYPE_FILTER_MODE = "ALL"
    private const val DEFAULT_AIRCRAFT_PRIORITY = "DISTANCE"

    // Add these keys to AppSettings.kt
    private const val KEY_MIN_ALTITUDE_FILTER = "min_altitude_filter"
    private const val KEY_MAX_ALTITUDE_FILTER = "max_altitude_filter"
    private const val KEY_MIN_SPEED_FILTER = "min_speed_filter"
    private const val KEY_MAX_SPEED_FILTER = "max_speed_filter"
    private const val KEY_ALTITUDE_FILTER_ENABLED = "altitude_filter_enabled"
    private const val KEY_SPEED_FILTER_ENABLED = "speed_filter_enabled"
    // NEW: Elevation Filter Keys
    private const val KEY_MIN_ELEVATION_FILTER = "min_elevation_filter"
    private const val KEY_ELEVATION_FILTER_ENABLED = "elevation_filter_enabled"

    // Default values
    private const val DEFAULT_MIN_ALTITUDE_FILTER = 0
    private const val DEFAULT_MAX_ALTITUDE_FILTER = 50000
    private const val DEFAULT_MIN_SPEED_FILTER = 0
    private const val DEFAULT_MAX_SPEED_FILTER = 1000
    private const val DEFAULT_ALTITUDE_FILTER_ENABLED = false
    private const val DEFAULT_SPEED_FILTER_ENABLED = false
    // NEW: Elevation Defaults
    private const val DEFAULT_MIN_ELEVATION_FILTER = 5 // degrees
    private const val DEFAULT_ELEVATION_FILTER_ENABLED = false

    // Units enums
    enum class DistanceUnits { METRIC, IMPERIAL }
    enum class AltitudeUnits { METERS, FEET }

    // Aircraft filter enums
    enum class AircraftFilterMode { ALL, INCLUDE, EXCLUDE }
    enum class AircraftPriority { DISTANCE, ALTITUDE, SPEED, TYPE }

    // NEW Label Config Keys (Persisting complex objects as Strings)
    private const val KEY_LABEL_TOP_LINE_MODE = "label_top_line_mode"
    private const val KEY_LABEL_TOP_LINE_FIELDS = "label_top_line_fields"
    private const val KEY_LABEL_BOTTOM_LINE_MODE = "label_bottom_line_mode"
    private const val KEY_LABEL_BOTTOM_LINE_FIELDS = "label_bottom_line_fields"
    private const val KEY_SMART_ALTITUDE_TRANSITION = "smart_altitude_transition"

    private const val DEFAULT_SMART_ALTITUDE_TRANSITION = 3048 // ~10k feet

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Region: Label Configuration (New)

    fun getLabelTopLineMode(context: Context): LabelDisplayMode {
        val modeStr = getSharedPreferences(context).getString(KEY_LABEL_TOP_LINE_MODE, LabelDisplayMode.STATIC_COMBINED.name)
        return try { LabelDisplayMode.valueOf(modeStr!!) } catch (e: Exception) { LabelDisplayMode.STATIC_COMBINED }
    }

    fun setLabelTopLineMode(context: Context, mode: LabelDisplayMode) {
        getSharedPreferences(context).edit { putString(KEY_LABEL_TOP_LINE_MODE, mode.name) }
    }

    fun getLabelTopLineFields(context: Context): List<AircraftDataType> {
        val fieldsStr = getSharedPreferences(context).getString(KEY_LABEL_TOP_LINE_FIELDS, "CALLSIGN") ?: "CALLSIGN"
        return fieldsStr.split(",").mapNotNull { try { AircraftDataType.valueOf(it.trim()) } catch (e: Exception) { null } }
    }

    fun setLabelTopLineFields(context: Context, fields: List<AircraftDataType>) {
        val str = fields.joinToString(",") { it.name }
        getSharedPreferences(context).edit { putString(KEY_LABEL_TOP_LINE_FIELDS, str) }
    }

    fun getLabelBottomLineMode(context: Context): LabelDisplayMode {
        val modeStr = getSharedPreferences(context).getString(KEY_LABEL_BOTTOM_LINE_MODE, LabelDisplayMode.STATIC_COMBINED.name)
        return try { LabelDisplayMode.valueOf(modeStr!!) } catch (e: Exception) { LabelDisplayMode.STATIC_COMBINED }
    }

    fun setLabelBottomLineMode(context: Context, mode: LabelDisplayMode) {
        getSharedPreferences(context).edit { putString(KEY_LABEL_BOTTOM_LINE_MODE, mode.name) }
    }

    fun getLabelBottomLineFields(context: Context): List<AircraftDataType> {
        val fieldsStr = getSharedPreferences(context).getString(KEY_LABEL_BOTTOM_LINE_FIELDS, "ALTITUDE") ?: "ALTITUDE"
        return fieldsStr.split(",").mapNotNull { try { AircraftDataType.valueOf(it.trim()) } catch (e: Exception) { null } }
    }

    fun setLabelBottomLineFields(context: Context, fields: List<AircraftDataType>) {
        val str = fields.joinToString(",") { it.name }
        getSharedPreferences(context).edit { putString(KEY_LABEL_BOTTOM_LINE_FIELDS, str) }
    }

    fun getSmartAltitudeTransition(context: Context): Int {
        return getSharedPreferences(context).getInt(KEY_SMART_ALTITUDE_TRANSITION, DEFAULT_SMART_ALTITUDE_TRANSITION)
    }

    fun setSmartAltitudeTransition(context: Context, meters: Int) {
        getSharedPreferences(context).edit { putInt(KEY_SMART_ALTITUDE_TRANSITION, meters) }
    }

    // Region: Airport Settings

    fun getAutoReloadAirports(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_AUTO_RELOAD_AIRPORTS, DEFAULT_AUTO_RELOAD_AIRPORTS)
    }

    fun setAutoReloadAirports(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_AUTO_RELOAD_AIRPORTS, enabled)
        }
    }

    fun getShowAirports(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_SHOW_AIRPORTS, DEFAULT_SHOW_AIRPORTS)
    }

    fun setShowAirports(context: Context, show: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_SHOW_AIRPORTS, show)
        }
    }

    fun getAirportPreloadRadius(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_AIRPORT_PRELOAD_RADIUS, DEFAULT_AIRPORT_PRELOAD_RADIUS)
    }

    fun setAirportPreloadRadius(context: Context, radiusKm: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_AIRPORT_PRELOAD_RADIUS, radiusKm)
        }
    }

    fun getAirportDisplayFormat(context: Context): AirfieldDisplayFormat {
        val formatString = getSharedPreferences(context)
            .getString(KEY_AIRPORT_DISPLAY_FORMAT, DEFAULT_AIRPORT_DISPLAY_FORMAT)
            ?: DEFAULT_AIRPORT_DISPLAY_FORMAT
        return try {
            AirfieldDisplayFormat.valueOf(formatString)
        } catch (e: Exception) {
            AirfieldDisplayFormat.IATA // Default fallback
        }
    }

    fun setAirportDisplayFormat(context: Context, format: AirfieldDisplayFormat) {
        getSharedPreferences(context).edit {
            putString(KEY_AIRPORT_DISPLAY_FORMAT, format.name)
        }
    }

    // Region: API & Data Settings

    fun getApiSearchRange(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_API_SEARCH_RANGE, DEFAULT_API_SEARCH_RANGE)
    }

    fun setApiSearchRange(context: Context, rangeNm: Double) {
        getSharedPreferences(context).edit {
            putInt(KEY_API_SEARCH_RANGE, rangeNm.toInt())
        }
    }

    fun getApiPollingInterval(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_API_POLLING_INTERVAL, DEFAULT_API_POLLING_INTERVAL)
    }

    fun setApiPollingInterval(context: Context, intervalSeconds: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_API_POLLING_INTERVAL, intervalSeconds)
        }
    }

    fun getFilterGlidersOnly(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_FILTER_GLIDERS_ONLY, DEFAULT_FILTER_GLIDERS_ONLY)
    }

    fun setFilterGlidersOnly(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_FILTER_GLIDERS_ONLY, enabled)
        }
    }

    // Region: Radar Display Settings

    fun getRadarViewRange(context: Context): Double {
        return getSharedPreferences(context)
            .getFloat(KEY_RADAR_VIEW_RANGE, DEFAULT_RADAR_VIEW_RANGE.toFloat())
            .toDouble()
    }

    fun setRadarViewRange(context: Context, rangeMeters: Double) {
        getSharedPreferences(context).edit {
            putFloat(KEY_RADAR_VIEW_RANGE, rangeMeters.toFloat())
        }
    }

    fun getLabelTextSize(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_LABEL_TEXT_SIZE, DEFAULT_LABEL_TEXT_SIZE)
    }

    fun setLabelTextSize(context: Context, sizeSp: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_LABEL_TEXT_SIZE, sizeSp)
        }
    }

    fun getNorthUpOrientation(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_NORTH_UP_ORIENTATION, DEFAULT_NORTH_UP_ORIENTATION)
    }

    fun setNorthUpOrientation(context: Context, northUp: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_NORTH_UP_ORIENTATION, northUp)
        }
    }

    fun getRadarRedrawInterval(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_RADAR_REDRAW_INTERVAL, DEFAULT_RADAR_REDRAW_INTERVAL)
    }

    fun setRadarRedrawInterval(context: Context, intervalSeconds: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_RADAR_REDRAW_INTERVAL, intervalSeconds)
        }
    }

    fun getShowAircraftTrails(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_SHOW_AIRCRAFT_TRAILS, DEFAULT_SHOW_AIRCRAFT_TRAILS)
    }

    fun setShowAircraftTrails(context: Context, show: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_SHOW_AIRCRAFT_TRAILS, show)
        }
    }

    fun getTrailLength(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_TRAIL_LENGTH, DEFAULT_TRAIL_LENGTH)
    }

    fun setTrailLength(context: Context, lengthSeconds: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_TRAIL_LENGTH, lengthSeconds)
        }
    }

    // Region: Simulation Settings

    fun getSimulationEnabled(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_SIMULATION_ENABLED, DEFAULT_SIMULATION_ENABLED)
    }

    fun setSimulationEnabled(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_SIMULATION_ENABLED, enabled)
        }
    }

    fun getSimulationLatitude(context: Context): Double {
        return getSharedPreferences(context)
            .getFloat(KEY_SIMULATION_LATITUDE, DEFAULT_SIMULATION_LATITUDE.toFloat())
            .toDouble()
    }

    fun setSimulationLatitude(context: Context, latitude: Double) {
        getSharedPreferences(context).edit {
            putFloat(KEY_SIMULATION_LATITUDE, latitude.toFloat())
        }
    }

    fun getSimulationLongitude(context: Context): Double {
        return getSharedPreferences(context)
            .getFloat(KEY_SIMULATION_LONGITUDE, DEFAULT_SIMULATION_LONGITUDE.toFloat())
            .toDouble()
    }

    fun setSimulationLongitude(context: Context, longitude: Double) {
        getSharedPreferences(context).edit {
            putFloat(KEY_SIMULATION_LONGITUDE, longitude.toFloat())
        }
    }

    fun getSimulationHeading(context: Context): Double {
        return getSharedPreferences(context)
            .getFloat(KEY_SIMULATION_HEADING, DEFAULT_SIMULATION_HEADING.toFloat())
            .toDouble()
    }

    fun setSimulationHeading(context: Context, heading: Double) {
        getSharedPreferences(context).edit {
            putFloat(KEY_SIMULATION_HEADING, heading.toFloat())
        }
    }

    // Region: Units Settings

    fun getDistanceUnits(context: Context): DistanceUnits {
        val unitsString = getSharedPreferences(context)
            .getString(KEY_DISTANCE_UNITS, DEFAULT_DISTANCE_UNITS)
            ?: DEFAULT_DISTANCE_UNITS
        return try {
            DistanceUnits.valueOf(unitsString)
        } catch (e: Exception) {
            DistanceUnits.METRIC
        }
    }

    fun setDistanceUnits(context: Context, units: DistanceUnits) {
        getSharedPreferences(context).edit {
            putString(KEY_DISTANCE_UNITS, units.name)
        }
    }

    fun getAltitudeUnits(context: Context): AltitudeUnits {
        val unitsString = getSharedPreferences(context)
            .getString(KEY_ALTITUDE_UNITS, DEFAULT_ALTITUDE_UNITS)
            ?: DEFAULT_ALTITUDE_UNITS
        return try {
            AltitudeUnits.valueOf(unitsString)
        } catch (e: Exception) {
            AltitudeUnits.METERS
        }
    }

    fun setAltitudeUnits(context: Context, units: AltitudeUnits) {
        getSharedPreferences(context).edit {
            putString(KEY_ALTITUDE_UNITS, units.name)
        }
    }

    // Region: Aircraft Filter Settings

    fun getMinimumAltitude(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MINIMUM_ALTITUDE, DEFAULT_MINIMUM_ALTITUDE)
    }

    fun setMinimumAltitude(context: Context, altitudeMeters: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MINIMUM_ALTITUDE, altitudeMeters)
        }
    }

    fun getMaximumDistance(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MAXIMUM_DISTANCE, DEFAULT_MAXIMUM_DISTANCE)
    }

    fun setMaximumDistance(context: Context, distanceMeters: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MAXIMUM_DISTANCE, distanceMeters)
        }
    }

    fun getMaximumAircraftAge(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MAXIMUM_AIRCRAFT_AGE, DEFAULT_MAXIMUM_AIRCRAFT_AGE)
    }

    fun setMaximumAircraftAge(context: Context, ageSeconds: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MAXIMUM_AIRCRAFT_AGE, ageSeconds)
        }
    }

    fun getAircraftTypeFilterMode(context: Context): AircraftFilterMode {
        val modeString = getSharedPreferences(context)
            .getString(KEY_AIRCRAFT_TYPE_FILTER_MODE, DEFAULT_AIRCRAFT_TYPE_FILTER_MODE)
            ?: DEFAULT_AIRCRAFT_TYPE_FILTER_MODE
        return try {
            AircraftFilterMode.valueOf(modeString)
        } catch (e: Exception) {
            AircraftFilterMode.ALL
        }
    }

    fun setAircraftTypeFilterMode(context: Context, mode: AircraftFilterMode) {
        getSharedPreferences(context).edit {
            putString(KEY_AIRCRAFT_TYPE_FILTER_MODE, mode.name)
        }
    }

    fun getSelectedAircraftTypes(context: Context): Set<String> {
        return getSharedPreferences(context)
            .getStringSet(KEY_SELECTED_AIRCRAFT_TYPES, emptySet()) ?: emptySet()
    }

    fun setSelectedAircraftTypes(context: Context, types: Set<String>) {
        getSharedPreferences(context).edit {
            putStringSet(KEY_SELECTED_AIRCRAFT_TYPES, types)
        }
    }

    fun getAircraftPriority(context: Context): AircraftPriority {
        val priorityString = getSharedPreferences(context)
            .getString(KEY_AIRCRAFT_PRIORITY, DEFAULT_AIRCRAFT_PRIORITY)
            ?: DEFAULT_AIRCRAFT_PRIORITY
        return try {
            AircraftPriority.valueOf(priorityString)
        } catch (e: Exception) {
            AircraftPriority.DISTANCE
        }
    }

    fun setAircraftPriority(context: Context, priority: AircraftPriority) {
        getSharedPreferences(context).edit {
            putString(KEY_AIRCRAFT_PRIORITY, priority.name)
        }
    }

    // Altitude Filter Settings
    fun getMinAltitudeFilter(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MIN_ALTITUDE_FILTER, DEFAULT_MIN_ALTITUDE_FILTER)
    }

    fun setMinAltitudeFilter(context: Context, altitudeMeters: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MIN_ALTITUDE_FILTER, altitudeMeters)
        }
    }

    fun getMaxAltitudeFilter(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MAX_ALTITUDE_FILTER, DEFAULT_MAX_ALTITUDE_FILTER)
    }

    fun setMaxAltitudeFilter(context: Context, altitudeMeters: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MAX_ALTITUDE_FILTER, altitudeMeters)
        }
    }

    fun getAltitudeFilterEnabled(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_ALTITUDE_FILTER_ENABLED, DEFAULT_ALTITUDE_FILTER_ENABLED)
    }

    fun setAltitudeFilterEnabled(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_ALTITUDE_FILTER_ENABLED, enabled)
        }
    }

    // Speed Filter Settings
    fun getMinSpeedFilter(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MIN_SPEED_FILTER, DEFAULT_MIN_SPEED_FILTER)
    }

    fun setMinSpeedFilter(context: Context, speedKnots: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MIN_SPEED_FILTER, speedKnots)
        }
    }

    fun getMaxSpeedFilter(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MAX_SPEED_FILTER, DEFAULT_MAX_SPEED_FILTER)
    }

    fun setMaxSpeedFilter(context: Context, speedKnots: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MAX_SPEED_FILTER, speedKnots)
        }
    }

    fun getSpeedFilterEnabled(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_SPEED_FILTER_ENABLED, DEFAULT_SPEED_FILTER_ENABLED)
    }

    fun setSpeedFilterEnabled(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_SPEED_FILTER_ENABLED, enabled)
        }
    }

    // NEW: Elevation (Angle Above Horizon) Filter
    fun getMinElevationFilter(context: Context): Int {
        return getSharedPreferences(context)
            .getInt(KEY_MIN_ELEVATION_FILTER, DEFAULT_MIN_ELEVATION_FILTER)
    }

    fun setMinElevationFilter(context: Context, angleDegrees: Int) {
        getSharedPreferences(context).edit {
            putInt(KEY_MIN_ELEVATION_FILTER, angleDegrees)
        }
    }

    fun getElevationFilterEnabled(context: Context): Boolean {
        return getSharedPreferences(context)
            .getBoolean(KEY_ELEVATION_FILTER_ENABLED, DEFAULT_ELEVATION_FILTER_ENABLED)
    }

    fun setElevationFilterEnabled(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_ELEVATION_FILTER_ENABLED, enabled)
        }
    }

    // Region: Utility Methods

    /**
     * Initialize all settings with default values if they don't exist
     */
    fun initializeSimulationSettings(context: Context) {
        val prefs = getSharedPreferences(context)

        // Only initialize if no settings exist yet
        if (!prefs.contains(KEY_SIMULATION_ENABLED)) {
            prefs.edit {
                putBoolean(KEY_SIMULATION_ENABLED, DEFAULT_SIMULATION_ENABLED)
                putFloat(KEY_SIMULATION_LATITUDE, DEFAULT_SIMULATION_LATITUDE.toFloat())
                putFloat(KEY_SIMULATION_LONGITUDE, DEFAULT_SIMULATION_LONGITUDE.toFloat())
                putFloat(KEY_SIMULATION_HEADING, DEFAULT_SIMULATION_HEADING.toFloat())
            }
        }
    }

    fun resetToDefaults(context: Context) {
        getSharedPreferences(context).edit {
            clear()
        }
        // Re-apply essential defaults
        initializeSimulationSettings(context)
    }

    fun exportSettings(context: Context): Map<String, Any?> {
        val prefs = getSharedPreferences(context)
        return prefs.all
    }

    fun isFirstRun(context: Context): Boolean {
        val prefs = getSharedPreferences(context)
        return !prefs.contains(KEY_API_POLLING_INTERVAL)
    }

    fun getShowOwnship(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_SHOW_OWNSHIP, true)
    }

    fun setShowOwnship(context: Context, show: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_SHOW_OWNSHIP, show)
        }
    }

    fun getGpsSimulationMode(context: Context): Boolean {
        return getSimulationEnabled(context)
    }

    fun setGpsSimulationMode(context: Context, enabled: Boolean) {
        setSimulationEnabled(context, enabled)
    }

    fun getShowNotification(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_SHOW_NOTIFICATION, true)
    }

    fun setShowNotification(context: Context, show: Boolean) {
        getSharedPreferences(context).edit {
            putBoolean(KEY_SHOW_NOTIFICATION, show)
        }
    }
}