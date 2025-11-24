package com.kilodeltaapps.karooflightradar.ui.settings

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kilodeltaapps.karooflightradar.data.*
import com.kilodeltaapps.karooflightradar.datafields.RadarDataField
import com.kilodeltaapps.karooflightradar.repository.AirfieldRepository
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import com.kilodeltaapps.karooflightradar.utils.UnitConversions
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Wrapper for Airport list items
data class AirportInfo(
    val name: String,
    val icao: String?,
    val iata: String?,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double
)

// Combined UI State
data class SettingsUiState(
    val labelConfig: LabelConfiguration = LabelConfiguration(),

    // Units
    val distanceUnits: AppSettings.DistanceUnits = AppSettings.DistanceUnits.METRIC,
    val altitudeUnits: AppSettings.AltitudeUnits = AppSettings.AltitudeUnits.METERS,

    // Display Settings (Strings hold values in User's Unit)
    val radarRange: String = "25", // km or nm
    val labelSize: String = "14",
    val isNorthUp: Boolean = false,
    val showOwnship: Boolean = true,
    val showAircraftTrails: Boolean = true,
    val trailLength: String = "60",

    // Airport Settings
    val showAirports: Boolean = true,
    val airfieldDisplayFormat: AirfieldDisplayFormat = AirfieldDisplayFormat.IATA,
    val airportPreloadRadius: String = "100",

    // Data Settings
    val apiSearchRange: String = "50",
    val apiPollingInterval: String = "10",
    val filterGlidersOnly: Boolean = false,

    // System Settings
    val isGpsSimulationEnabled: Boolean = false,
    val showNotification: Boolean = true,

    // Filter settings (User Units)
    val minAltitudeFilter: String = "0",
    val maxAltitudeFilter: String = "50000",
    val minSpeedFilter: String = "0",
    val maxSpeedFilter: String = "1000",
    val altitudeFilterEnabled: Boolean = false,
    val speedFilterEnabled: Boolean = false,

    val minElevationFilter: String = "5",
    val elevationFilterEnabled: Boolean = false,

    // User location for airport loading
    val userLat: Double = 51.0,
    val userLon: Double = 4.0
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext

    // State Flow
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _loadedAirports = MutableStateFlow<List<AirportInfo>>(emptyList())
    val loadedAirports = _loadedAirports.asStateFlow()

    private val _isLoadingAirports = MutableStateFlow(false)
    val isLoadingAirports = _isLoadingAirports.asStateFlow()

    init {
        loadAllSettings()
        viewModelScope.launch {
            ensureRepositoryInitialized()
            refreshLoadedAirports()
        }
    }

    private suspend fun ensureRepositoryInitialized() {
        try {
            if (!isAirfieldRepositoryInitialized()) {
                Log.d("SettingsViewModel", "Initializing AirfieldRepository...")
                AirfieldRepository.initialize(context)
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to initialize repository: ${e.message}", e)
        }
    }

    private suspend fun isAirfieldRepositoryInitialized(): Boolean {
        return try {
            AirfieldRepository.getStatistics().totalAirports > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update user location (to be called from outside when GPS updates)
     */
    fun updateUserLocation(lat: Double, lon: Double) {
        _uiState.update { it.copy(userLat = lat, userLon = lon) }
    }

    /**
     * Loads settings from AppSettings (Storage Units) and converts to UI State (User Units)
     */
    private fun loadAllSettings() {
        viewModelScope.launch {
            val distUnit = AppSettings.getDistanceUnits(context)
            val altUnit = AppSettings.getAltitudeUnits(context)

            // Convert Radar Range (Meters -> KM or NM)
            val rangeMeters = AppSettings.getRadarViewRange(context)
            val rangeUi = if (distUnit == AppSettings.DistanceUnits.METRIC)
                (rangeMeters / 1000.0).toInt().toString()
            else
                UnitConversions.metersToNm(rangeMeters).toInt().toString()

            // Convert Altitude Filters (Meters -> M or FT)
            val minAltMeters = AppSettings.getMinAltitudeFilter(context)
            val maxAltMeters = AppSettings.getMaxAltitudeFilter(context)
            val minAltUi = if (altUnit == AppSettings.AltitudeUnits.METERS) minAltMeters.toString() else UnitConversions.metersToFeet(minAltMeters.toDouble()).roundToInt().toString()
            val maxAltUi = if (altUnit == AppSettings.AltitudeUnits.METERS) maxAltMeters.toString() else UnitConversions.metersToFeet(maxAltMeters.toDouble()).roundToInt().toString()

            // Convert Speed Filters (Kmh -> Kmh or Kts)
            val minSpeedKmh = AppSettings.getMinSpeedFilter(context)
            val maxSpeedKmh = AppSettings.getMaxSpeedFilter(context)
            val minSpeedUi = if (distUnit == AppSettings.DistanceUnits.METRIC) minSpeedKmh.toString() else UnitConversions.kmhToKnots(minSpeedKmh.toDouble()).roundToInt().toString()
            val maxSpeedUi = if (distUnit == AppSettings.DistanceUnits.METRIC) maxSpeedKmh.toString() else UnitConversions.kmhToKnots(maxSpeedKmh.toDouble()).roundToInt().toString()

            val labelConfig = LabelConfiguration(
                topLine = LabelLineConfig(
                    mode = AppSettings.getLabelTopLineMode(context),
                    fields = AppSettings.getLabelTopLineFields(context)
                ),
                bottomLine = LabelLineConfig(
                    mode = AppSettings.getLabelBottomLineMode(context),
                    fields = AppSettings.getLabelBottomLineFields(context)
                ),
                smartAltitudeTransitionMeters = AppSettings.getSmartAltitudeTransition(context)
            )

            _uiState.update { currentState ->
                currentState.copy(
                    labelConfig = labelConfig,
                    distanceUnits = distUnit,
                    altitudeUnits = altUnit,

                    radarRange = rangeUi,
                    minAltitudeFilter = minAltUi,
                    maxAltitudeFilter = maxAltUi,
                    minSpeedFilter = minSpeedUi,
                    maxSpeedFilter = maxSpeedUi,

                    labelSize = AppSettings.getLabelTextSize(context).toString(),
                    isNorthUp = AppSettings.getNorthUpOrientation(context),
                    showOwnship = AppSettings.getShowOwnship(context),
                    showAircraftTrails = AppSettings.getShowAircraftTrails(context),
                    trailLength = AppSettings.getTrailLength(context).toString(),

                    showAirports = AppSettings.getShowAirports(context),
                    airfieldDisplayFormat = AppSettings.getAirportDisplayFormat(context),
                    airportPreloadRadius = AppSettings.getAirportPreloadRadius(context).toString(),

                    apiSearchRange = AppSettings.getApiSearchRange(context).toString(),
                    apiPollingInterval = AppSettings.getApiPollingInterval(context).toString(),
                    filterGlidersOnly = AppSettings.getFilterGlidersOnly(context),

                    isGpsSimulationEnabled = AppSettings.getGpsSimulationMode(context),
                    showNotification = AppSettings.getShowNotification(context),

                    altitudeFilterEnabled = AppSettings.getAltitudeFilterEnabled(context),
                    speedFilterEnabled = AppSettings.getSpeedFilterEnabled(context),
                    minElevationFilter = AppSettings.getMinElevationFilter(context).toString(),
                    elevationFilterEnabled = AppSettings.getElevationFilterEnabled(context),
                )
            }
        }
    }

    // --- Unit Changes ---

    fun onDistanceUnitsChange(units: AppSettings.DistanceUnits) {
        viewModelScope.launch {
            AppSettings.setDistanceUnits(context, units)
            loadAllSettings()
        }
    }

    fun onAltitudeUnitsChange(units: AppSettings.AltitudeUnits) {
        viewModelScope.launch {
            AppSettings.setAltitudeUnits(context, units)
            loadAllSettings()
        }
    }

    // --- Value Updates (UI -> Storage) ---

    fun onRadarRangeChange(rangeUi: String) {
        val filtered = rangeUi.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return
        _uiState.update { it.copy(radarRange = filtered) }

        filtered.toDoubleOrNull()?.let { value ->
            val meters = if (_uiState.value.distanceUnits == AppSettings.DistanceUnits.METRIC)
                value * 1000.0
            else
                UnitConversions.nmToMeters(value)

            viewModelScope.launch { AppSettings.setRadarViewRange(context, meters) }
        }
    }

    fun onMinAltitudeFilterChange(valueUi: String) {
        val filtered = valueUi.filter { it.isDigit() }
        _uiState.update { it.copy(minAltitudeFilter = filtered) }

        filtered.toIntOrNull()?.let { value ->
            val meters = if (_uiState.value.altitudeUnits == AppSettings.AltitudeUnits.METERS)
                value
            else
                UnitConversions.feetToMeters(value.toDouble()).roundToInt()

            viewModelScope.launch { AppSettings.setMinAltitudeFilter(context, meters) }
        }
    }

    fun onMaxAltitudeFilterChange(valueUi: String) {
        val filtered = valueUi.filter { it.isDigit() }
        _uiState.update { it.copy(maxAltitudeFilter = filtered) }

        filtered.toIntOrNull()?.let { value ->
            val meters = if (_uiState.value.altitudeUnits == AppSettings.AltitudeUnits.METERS)
                value
            else
                UnitConversions.feetToMeters(value.toDouble()).roundToInt()

            viewModelScope.launch { AppSettings.setMaxAltitudeFilter(context, meters) }
        }
    }

    fun onMinSpeedFilterChange(valueUi: String) {
        val filtered = valueUi.filter { it.isDigit() }
        _uiState.update { it.copy(minSpeedFilter = filtered) }

        filtered.toIntOrNull()?.let { value ->
            val kmh = if (_uiState.value.distanceUnits == AppSettings.DistanceUnits.METRIC)
                value
            else
                UnitConversions.knotsToKmh(value.toDouble()).roundToInt()

            viewModelScope.launch { AppSettings.setMinSpeedFilter(context, kmh) }
        }
    }

    fun onMaxSpeedFilterChange(valueUi: String) {
        val filtered = valueUi.filter { it.isDigit() }
        _uiState.update { it.copy(maxSpeedFilter = filtered) }

        filtered.toIntOrNull()?.let { value ->
            val kmh = if (_uiState.value.distanceUnits == AppSettings.DistanceUnits.METRIC)
                value
            else
                UnitConversions.knotsToKmh(value.toDouble()).roundToInt()

            viewModelScope.launch { AppSettings.setMaxSpeedFilter(context, kmh) }
        }
    }

    fun updateSmartAltitudeTransition(valueUi: Int) {
        val meters = if (_uiState.value.altitudeUnits == AppSettings.AltitudeUnits.METERS)
            valueUi
        else
            UnitConversions.feetToMeters(valueUi.toDouble()).roundToInt()

        _uiState.update { it.copy(labelConfig = it.labelConfig.copy(smartAltitudeTransitionMeters = meters)) }
        viewModelScope.launch {
            AppSettings.setSmartAltitudeTransition(context, meters)
            notifyRadarService()
        }
    }

    // --- Passthroughs (Unchanged logic) ---

    fun updateTopLineMode(mode: LabelDisplayMode) {
        _uiState.update { it.copy(labelConfig = it.labelConfig.copy(topLine = it.labelConfig.topLine.copy(mode = mode))) }
        viewModelScope.launch { AppSettings.setLabelTopLineMode(context, mode); notifyRadarService() }
    }
    fun updateTopLineFields(fields: List<AircraftDataType>) {
        _uiState.update { it.copy(labelConfig = it.labelConfig.copy(topLine = it.labelConfig.topLine.copy(fields = fields))) }
        viewModelScope.launch { AppSettings.setLabelTopLineFields(context, fields); notifyRadarService() }
    }
    fun updateBottomLineMode(mode: LabelDisplayMode) {
        _uiState.update { it.copy(labelConfig = it.labelConfig.copy(bottomLine = it.labelConfig.bottomLine.copy(mode = mode))) }
        viewModelScope.launch { AppSettings.setLabelBottomLineMode(context, mode); notifyRadarService() }
    }
    fun updateBottomLineFields(fields: List<AircraftDataType>) {
        _uiState.update { it.copy(labelConfig = it.labelConfig.copy(bottomLine = it.labelConfig.bottomLine.copy(fields = fields))) }
        viewModelScope.launch { AppSettings.setLabelBottomLineFields(context, fields); notifyRadarService() }
    }

    private fun notifyRadarService() {
        val intent = Intent(RadarDataField.ACTION_SETTINGS_CHANGED)
        context.sendBroadcast(intent)
    }

    // --------------------------------------------------------
    // Settings Methods
    // --------------------------------------------------------

    fun refreshLoadedAirports() {
        viewModelScope.launch {
            _isLoadingAirports.value = true
            try {
                // Ensure repository is initialized
                ensureRepositoryInitialized()

                // Check if repository has data
                val stats = AirfieldRepository.getStatistics()
                if (stats.totalAirports == 0) {
                    Log.w("SettingsViewModel", "Airport repository is empty")
                    _loadedAirports.value = emptyList()
                    return@launch
                }

                val userLat = _uiState.value.userLat
                val userLon = _uiState.value.userLon
                val preloadRadius = AppSettings.getAirportPreloadRadius(context)

                Log.d("SettingsViewModel", "Loading airports at ($userLat, $userLon) with radius ${preloadRadius}km")

                AirfieldRepository.preloadAirports(context, userLat, userLon, preloadRadius.toDouble())

                val maxRangeMeters = preloadRadius * 1000.0 * 2.0
                val airportsWithDistances = AirfieldRepository.getAirportsWithinRadarRange(
                    userLat, userLon, maxRangeMeters
                )

                _loadedAirports.value = airportsWithDistances.map { (airport, distanceMeters) ->
                    AirportInfo(
                        name = airport.name,
                        icao = airport.icao,
                        iata = airport.iata,
                        lat = airport.lat,
                        lon = airport.lon,
                        distanceKm = distanceMeters / 1000.0
                    )
                }.sortedBy { it.distanceKm }

                Log.d("SettingsViewModel", "Loaded ${_loadedAirports.value.size} airports")

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error loading airports: ${e.message}", e)
                _loadedAirports.value = emptyList()
            } finally {
                _isLoadingAirports.value = false
            }
        }
    }

    fun onLabelSizeChange(size: String) {
        val filteredSize = size.filter { it.isDigit() }.take(2)
        _uiState.update { it.copy(labelSize = filteredSize) }
        filteredSize.toIntOrNull()?.let { sizeSp ->
            viewModelScope.launch {
                AppSettings.setLabelTextSize(context, sizeSp)
                notifyRadarService()
            }
        }
    }

    fun onNorthUpChange(enabled: Boolean) {
        _uiState.update { it.copy(isNorthUp = enabled) }
        viewModelScope.launch {
            AppSettings.setNorthUpOrientation(context, enabled)
            notifyRadarService()
        }
    }

    fun onShowOwnshipChange(enabled: Boolean) {
        _uiState.update { it.copy(showOwnship = enabled) }
        viewModelScope.launch {
            AppSettings.setShowOwnship(context, enabled)
            notifyRadarService()
        }
    }

    fun onShowAircraftTrailsChange(enabled: Boolean) {
        _uiState.update { it.copy(showAircraftTrails = enabled) }
        viewModelScope.launch {
            AppSettings.setShowAircraftTrails(context, enabled)
            notifyRadarService()
        }
    }

    fun onTrailLengthChange(length: String) {
        val filteredLength = length.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(trailLength = filteredLength) }
        filteredLength.toIntOrNull()?.let { lengthSec ->
            viewModelScope.launch {
                AppSettings.setTrailLength(context, lengthSec)
                notifyRadarService()
            }
        }
    }

    fun onShowAirportsChange(enabled: Boolean) {
        _uiState.update { it.copy(showAirports = enabled) }
        viewModelScope.launch {
            AppSettings.setShowAirports(context, enabled)
            notifyRadarService()
            refreshLoadedAirports()
        }
    }

    fun onAirfieldFormatChange(format: AirfieldDisplayFormat) {
        _uiState.update { it.copy(airfieldDisplayFormat = format) }
        viewModelScope.launch {
            AppSettings.setAirportDisplayFormat(context, format)
            notifyRadarService()  // FIX: Added this to make format changes take effect immediately
        }
    }

    fun onAirportPreloadRadiusChange(radius: String) {
        val filteredRadius = radius.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(airportPreloadRadius = filteredRadius) }
        filteredRadius.toIntOrNull()?.let { radiusKm ->
            viewModelScope.launch {
                AppSettings.setAirportPreloadRadius(context, radiusKm)
                refreshLoadedAirports()
            }
        }
    }

    fun onApiSearchRangeChange(range: String) {
        val filteredRange = range.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(apiSearchRange = filteredRange) }
        filteredRange.toDoubleOrNull()?.let { rangeNm ->
            viewModelScope.launch { AppSettings.setApiSearchRange(context, rangeNm) }
        }
    }

    fun onApiPollingIntervalChange(interval: String) {
        val filteredInterval = interval.filter { it.isDigit() }.take(2)
        _uiState.update { it.copy(apiPollingInterval = filteredInterval) }
        filteredInterval.toIntOrNull()?.let { intervalSec ->
            viewModelScope.launch { AppSettings.setApiPollingInterval(context, intervalSec) }
        }
    }

    fun onFilterGlidersOnlyChange(enabled: Boolean) {
        _uiState.update { it.copy(filterGlidersOnly = enabled) }
        viewModelScope.launch { AppSettings.setFilterGlidersOnly(context, enabled) }
    }

    fun onGpsSimulationChange(enabled: Boolean) {
        _uiState.update { it.copy(isGpsSimulationEnabled = enabled) }
        viewModelScope.launch {
            AppSettings.setGpsSimulationMode(context, enabled)
            refreshLoadedAirports()
        }
    }

    fun onShowNotificationChange(enabled: Boolean) {
        _uiState.update { it.copy(showNotification = enabled) }
        viewModelScope.launch { AppSettings.setShowNotification(context, enabled) }
    }

    fun onAltitudeFilterEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(altitudeFilterEnabled = enabled) }
        viewModelScope.launch { AppSettings.setAltitudeFilterEnabled(context, enabled) }
    }

    fun onSpeedFilterEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(speedFilterEnabled = enabled) }
        viewModelScope.launch { AppSettings.setSpeedFilterEnabled(context, enabled) }
    }

    fun onMinElevationFilterChange(value: String) {
        val filteredValue = value.filter { it.isDigit() }.take(2)
        _uiState.update { it.copy(minElevationFilter = filteredValue) }
        filteredValue.toIntOrNull()?.let { angle ->
            viewModelScope.launch { AppSettings.setMinElevationFilter(context, angle) }
        }
    }

    fun onElevationFilterEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(elevationFilterEnabled = enabled) }
        viewModelScope.launch { AppSettings.setElevationFilterEnabled(context, enabled) }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            AppSettings.resetToDefaults(context)
            loadAllSettings()
            refreshLoadedAirports()
        }
    }
}
