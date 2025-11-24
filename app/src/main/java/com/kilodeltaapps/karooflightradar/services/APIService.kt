package com.kilodeltaapps.karooflightradar.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped  // Changed from @Singleton
class APIService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val BASE_URL = "https://api.airplanes.live/v2/point"

    suspend fun fetchNearbyAircraft(lat: Double, lon: Double, rangeNm: Int): List<Aircraft> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$lat/$lon/$rangeNm"
                Log.d("ApiService", "Fetching aircraft from: $url")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("ApiService", "API error: ${response.code} - ${response.message}")
                    return@withContext emptyList()
                }

                val responseData = response.body?.string()
                if (responseData != null) {
                    val aircraft = AircraftParser.parseAircraftList(responseData)
                    Log.d("ApiService", "Fetched ${aircraft.size} aircraft")
                    aircraft.filter { aircraft ->
                        // 1. Type Filtering
                        val filterMode = AppSettings.getAircraftTypeFilterMode(context)
                        val selectedTypes = AppSettings.getSelectedAircraftTypes(context)

                        val isTypeValid = when (filterMode) {
                            AppSettings.AircraftFilterMode.ALL -> true
                            AppSettings.AircraftFilterMode.INCLUDE -> aircraft.aircraftType in selectedTypes
                            AppSettings.AircraftFilterMode.EXCLUDE -> aircraft.aircraftType !in selectedTypes
                        }

                        // 2. Altitude Filtering
                        // Check if filter is enabled in settings, then check range
                        val isAltitudeValid = if (AppSettings.getAltitudeFilterEnabled(context)) {
                            aircraft.altitude >= AppSettings.getMinAltitudeFilter(context) &&
                                    aircraft.altitude <= AppSettings.getMaxAltitudeFilter(context)
                        } else {
                            true // Pass everyone if filter is disabled
                        }

                        // 3. Speed Filtering
                        // Check if filter is enabled in settings, then check range
                        val isSpeedValid = if (AppSettings.getSpeedFilterEnabled(context)) {
                            aircraft.groundSpeed >= AppSettings.getMinSpeedFilter(context) &&
                                    aircraft.groundSpeed <= AppSettings.getMaxSpeedFilter(context)
                        } else {
                            true // Pass everyone if filter is disabled
                        }

                        // 4. Return the combined result (ALL must be true)
                        isTypeValid && isAltitudeValid && isSpeedValid
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("ApiService", "Network error: ${e.message}")
                emptyList()
            }
        }
    }

    fun getPollingInterval(): Long {
        return (AppSettings.getApiPollingInterval(context) * 1000L)
    }

    fun getSearchRange(): Int {
        return AppSettings.getApiSearchRange(context)
    }

    private fun isValidAircraftType(aircraftType: String?, context: Context): Boolean {
        if (aircraftType.isNullOrBlank()) return true

        val filterMode = AppSettings.getAircraftTypeFilterMode(context)
        val selectedTypes = AppSettings.getSelectedAircraftTypes(context)

        return when (filterMode) {
            AppSettings.AircraftFilterMode.ALL -> true
            AppSettings.AircraftFilterMode.INCLUDE -> aircraftType in selectedTypes
            AppSettings.AircraftFilterMode.EXCLUDE -> aircraftType !in selectedTypes
        }
    }

    fun prioritizeAircraft(aircraft: List<Aircraft>, context: Context): List<Aircraft> {
        return when (AppSettings.getAircraftPriority(context)) {
            AppSettings.AircraftPriority.DISTANCE -> aircraft.sortedBy { it.altitude }
            AppSettings.AircraftPriority.ALTITUDE -> aircraft.sortedByDescending { it.altitude }
            AppSettings.AircraftPriority.SPEED -> aircraft.sortedByDescending { it.groundSpeed }
            AppSettings.AircraftPriority.TYPE -> aircraft.sortedBy { it.aircraftType }
            else -> aircraft
        }
    }
}