package com.kilodeltaapps.karooflightradar.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kilodeltaapps.karooflightradar.data.*
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Define the extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flight_radar_settings_v2")

class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore

    // --- KEYS ---
    private object Keys {
        // Label Config - Top Line
        val TOP_LINE_MODE = stringPreferencesKey("top_line_mode")
        val TOP_LINE_FIELDS = stringPreferencesKey("top_line_fields")

        // Label Config - Bottom Line
        val BOTTOM_LINE_MODE = stringPreferencesKey("bottom_line_mode")
        val BOTTOM_LINE_FIELDS = stringPreferencesKey("bottom_line_fields")

        // Smart Altitude
        val SMART_ALT_TRANSITION = intPreferencesKey("smart_alt_transition")

        // Legacy Wrappers (You can migrate other simple keys here similarly)
        val RADAR_RANGE = doublePreferencesKey("radar_view_range")
        // ... add other keys as needed for full migration
    }

    // --- FLOWS ---

    val labelConfigFlow: Flow<LabelConfiguration> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            LabelConfiguration(
                topLine = LabelLineConfig(
                    mode = parseMode(prefs[Keys.TOP_LINE_MODE]),
                    fields = parseFields(prefs[Keys.TOP_LINE_FIELDS] ?: "CALLSIGN")
                ),
                bottomLine = LabelLineConfig(
                    mode = parseMode(prefs[Keys.BOTTOM_LINE_MODE]),
                    fields = parseFields(prefs[Keys.BOTTOM_LINE_FIELDS] ?: "ALTITUDE")
                ),
                smartAltitudeTransitionMeters = prefs[Keys.SMART_ALT_TRANSITION] ?: 1524
            )
        }

    // --- UPDATE FUNCTIONS ---

    suspend fun updateTopLine(config: LabelLineConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.TOP_LINE_MODE] = config.mode.name
            prefs[Keys.TOP_LINE_FIELDS] = serializeFields(config.fields)
        }
    }

    suspend fun updateBottomLine(config: LabelLineConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.BOTTOM_LINE_MODE] = config.mode.name
            prefs[Keys.BOTTOM_LINE_FIELDS] = serializeFields(config.fields)
        }
    }

    suspend fun updateSmartAltitudeTransition(meters: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SMART_ALT_TRANSITION] = meters
        }
    }

    // --- HELPERS ---

    private fun parseMode(value: String?): LabelDisplayMode {
        return try {
            if (value != null) LabelDisplayMode.valueOf(value) else LabelDisplayMode.STATIC_COMBINED
        } catch (e: Exception) { LabelDisplayMode.STATIC_COMBINED }
    }

    private fun parseFields(value: String): List<AircraftDataType> {
        if (value.isBlank()) return emptyList()
        return value.split(",").mapNotNull {
            try { AircraftDataType.valueOf(it) } catch (e: Exception) { null }
        }
    }

    private fun serializeFields(fields: List<AircraftDataType>): String {
        return fields.joinToString(",") { it.name }
    }
}