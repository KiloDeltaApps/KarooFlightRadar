package com.kilodeltaapps.karooflightradar.datafields

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.kilodeltaapps.karooflightradar.R
import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.Coord
import com.kilodeltaapps.karooflightradar.services.APIService
import com.kilodeltaapps.karooflightradar.services.GPSService
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import com.kilodeltaapps.karooflightradar.utils.Haversine
import com.kilodeltaapps.karooflightradar.views.closest_acft.ClosestAircraftView
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.math.atan2

class ClosestAircraftDataField(
    private val gpsService: GPSService,
    private val apiService: APIService,
    extensionId: String
) : DataTypeImpl(extensionId, "closest-aircraft") {

    companion object {
        const val TYPE_ID = "closest-aircraft"
        const val TAG = "ClosestDataField"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var viewJob: Job? = null
    private var pollingJob: Job? = null
    private var settingsReceiver: BroadcastReceiver? = null

    private lateinit var closestView: ClosestAircraftView
    private var lastApiCallTime: Long = 0

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        closestView = ClosestAircraftView(context)

        // Listen for setting changes (Units, etc.)
        registerSettingsReceiver(context)

        // Start GPS
        scope.launch {
            try {
                gpsService.startLocationUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "GPS Error: ${e.message}")
            }
        }

        viewJob = scope.launch {
            combine(
                AircraftList.aircraft, // Uses the Shared State
                gpsService.currentLocation,
                gpsService.currentHeading
            ) { aircraft, location, heading ->
                Triple(aircraft, location, heading)
            }.collectLatest { (allAircraft, location, currentHeading) ->
                val userLat = location.latitude
                val userLon = location.longitude

                // Handle Polling (Shared Logic: only poll if data is stale)
                if (userLat != 0.0 && userLon != 0.0) {
                    ensurePolling(context, userLat, userLon)
                }

                // 1. Filter Aircraft using AppSettings
                val filtered = applyFilters(context, allAircraft, userLat, userLon)

                // 2. Find Closest
                val closest = filtered.minByOrNull {
                    Haversine.calculateDistanceMeters(Coord(userLat, userLon), it.coord)
                }

                // 3. Draw
                val headingVal = (currentHeading as? Number)?.toDouble() ?: 0.0
                val bitmap = closestView.drawClosestAircraft(
                    closest,
                    Coord(userLat, userLon),
                    headingVal,
                    200, // Standard data field width
                    200  // Standard data field height
                )

                updateView(context, emitter, bitmap)
            }
        }

        emitter.setCancellable {
            viewJob?.cancel()
            pollingJob?.cancel()
            settingsReceiver?.let { context.unregisterReceiver(it) }
        }
    }

    private fun applyFilters(context: Context, list: List<Aircraft>, lat: Double, lon: Double): List<Aircraft> {
        val maxDist = AppSettings.getRadarViewRange(context)
        val altFilter = AppSettings.getAltitudeFilterEnabled(context)
        val minAlt = AppSettings.getMinAltitudeFilter(context)
        val maxAlt = AppSettings.getMaxAltitudeFilter(context)
        val elevFilter = AppSettings.getElevationFilterEnabled(context)
        val minElev = AppSettings.getMinElevationFilter(context)

        return list.filter { ac ->
            val dist = Haversine.calculateDistanceMeters(Coord(lat, lon), ac.coord)

            // Range Check
            if (dist > maxDist) return@filter false

            // Altitude Check
            if (altFilter) {
                if (ac.altitude < minAlt || ac.altitude > maxAlt) return@filter false
            }

            // Elevation Angle Check
            if (elevFilter) {
                // tan(angle) = height / distance
                // We use absolute altitude here. Ideally relative, but user alt is not always reliable in field.
                // This is a visual filter, so approx is fine.
                val angle = Math.toDegrees(atan2(ac.altitude, dist))
                if (angle < minElev) return@filter false
            }

            true
        }
    }

    /**
     * Ensures API data is being fetched.
     * Checks if data is stale (> 1.5x polling interval) before starting its own poller.
     * This prevents double-polling if the Radar Map is also active.
     */
    private fun ensurePolling(context: Context, lat: Double, lon: Double) {
        val interval = apiService.getPollingInterval()
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastApiCallTime > interval * 1500) {
            if (pollingJob == null || !pollingJob!!.isActive) {
                pollingJob = scope.launch {
                    while (isActive) {
                        try {
                            val rangeNm = AppSettings.getApiSearchRange(context)
                            val result = apiService.fetchNearbyAircraft(lat, lon, rangeNm)

                            // Update Shared State
                            AircraftList.updateAircraft(result)

                            lastApiCallTime = System.currentTimeMillis()
                        } catch (e: Exception) {
                            Log.e(TAG, "API Error: ${e.message}")
                        }
                        delay(interval)
                    }
                }
            }
        } else {
            // Data is fresh (likely updated by RadarDataField), just update our tracker
            lastApiCallTime = currentTime
        }
    }

    private fun updateView(context: Context, emitter: ViewEmitter, bitmap: Bitmap) {
        val rv = RemoteViews(context.packageName, R.layout.datafield_radar)
        rv.setImageViewBitmap(R.id.radar_image, bitmap)
        emitter.updateView(rv)
    }

    private fun registerSettingsReceiver(context: Context) {
        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Redraw triggered automatically by flow collection on next update
            }
        }
        val filter = IntentFilter(RadarDataField.ACTION_SETTINGS_CHANGED)
        ContextCompat.registerReceiver(context, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId = TYPE_ID, values = emptyMap())))
    }
}