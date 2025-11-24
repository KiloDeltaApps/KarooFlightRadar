package com.kilodeltaapps.karooflightradar.datafields

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.kilodeltaapps.karooflightradar.R
import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.Coord
import com.kilodeltaapps.karooflightradar.data.LabelConfiguration
import com.kilodeltaapps.karooflightradar.data.LabelLineConfig
import com.kilodeltaapps.karooflightradar.services.APIService
import com.kilodeltaapps.karooflightradar.services.GPSService
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import com.kilodeltaapps.karooflightradar.utils.Haversine
import com.kilodeltaapps.karooflightradar.views.radar.RadarScreen
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.math.atan2
import kotlin.math.sqrt

// ------------------------------------------------------------
// Shared State and Utility Data Classes
// ------------------------------------------------------------
object AircraftList {
    val aircraft = MutableStateFlow<List<Aircraft>>(emptyList())
    fun updateAircraft(newAircraft: List<Aircraft>) {
        aircraft.value = newAircraft
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


class RadarDataField(
    private val gpsService: GPSService,
    private val apiService: APIService,
    extensionId: String
) : DataTypeImpl(extensionId, "radar-view") {

    companion object {
        const val TYPE_ID = "radar-view"
        const val TAG = "RadarDataField"
        const val ACTION_SETTINGS_CHANGED = "com.kilodeltaapps.karooflightradar.SETTINGS_CHANGED"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var viewJob: Job? = null
    private var streamJob: Job? = null
    private var pollingJob: Job? = null
    private var settingsReceiver: BroadcastReceiver? = null

    private lateinit var radarScreen: RadarScreen

    private var lastStatus: String = "Starting..."
    private var lastError: String? = null
    private var lastRedrawTime: Long = 0
    private var lastApiCallTime: Long = 0

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(TAG, "Starting radar view")
        lastStatus = "View Started"

        try {
            radarScreen = RadarScreen(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init RadarScreen: ${e.message}")
            lastError = "Init Fail: ${e.message}"
            updateRadarBitmap(context, emitter, createErrorBitmap(e.message ?: "Unknown Init Error"))
            return
        }

        registerSettingsReceiver(context)

        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        updateRadarBitmap(context, emitter, createStatusBitmap("Waiting for Data..."))

        scope.launch {
            try {
                Log.d(TAG, "Starting Location Updates...")
                gpsService.startLocationUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "GPS Start Error: ${e.message}")
                lastError = "GPS Error: ${e.message}"
            }
        }

        viewJob = scope.launch {
            Log.d(TAG, "Launching Combine Flow...")

            combine(
                AircraftList.aircraft,
                gpsService.currentLocation,
                gpsService.currentHeading,
                gpsService.isSimulationActive
            ) { aircraft, location, currentHeading, sim ->
                Quad(aircraft, location, currentHeading, sim)
            }.collectLatest { (aircraft, location, currentHeading, simulation) ->
                try {
                    val userLat = location.latitude
                    val userLon = location.longitude

                    val drawingHeading = if (simulation) {
                        0.0
                    } else {
                        when (currentHeading) {
                            is Float -> currentHeading.toDouble()
                            is Double -> currentHeading
                            is Int -> currentHeading.toDouble()
                            is Long -> currentHeading.toDouble()
                            else -> 0.0
                        }
                    }

                    lastStatus = "Planes: ${aircraft.size} | GPS: ${"%.4f".format(userLat)} | HDG: ${"%.1f".format(drawingHeading)}"

                    if (userLat != 0.0 && userLon != 0.0) {
                        startPolling(context, userLat, userLon)
                    } else {
                        lastStatus = "Waiting for GPS Fix..."
                        pollingJob?.cancel()
                    }

                    val redrawInterval = AppSettings.getRadarRedrawInterval(context) * 1000L
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastRedrawTime >= redrawInterval) {
                        val bitmap = drawRadarBitmap(
                            context,
                            aircraft,
                            userLat,
                            userLon,
                            drawingHeading
                        )

                        updateRadarBitmap(context, emitter, bitmap)
                        lastRedrawTime = currentTime
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "CRASH in collectLatest: ${e.message}")
                    e.printStackTrace()
                    lastError = "Render Crash: ${e.message}"
                    updateRadarBitmap(context, emitter, createErrorBitmap(lastError ?: "Unknown"))
                }
            }
        }

        emitter.setCancellable {
            viewJob?.cancel()
            pollingJob?.cancel()
            settingsReceiver?.let { context.unregisterReceiver(it) }
        }
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Searching)
        streamJob = scope.launch {
            AircraftList.aircraft.collectLatest { aircraft ->
                emitter.onNext(
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId = TYPE_ID,
                            values = mapOf("count" to aircraft.size.toDouble())
                        )
                    )
                )
            }
        }
        emitter.setCancellable { streamJob?.cancel() }
    }

    private fun startPolling(context: Context, lat: Double, lon: Double) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val interval = apiService.getPollingInterval()
                    triggerApiPolling(context, lat, lon)
                    delay(interval)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    delay(5000)
                }
            }
        }
    }

    private fun registerSettingsReceiver(context: Context) {
        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_SETTINGS_CHANGED -> {
                        Log.d(TAG, "Settings changed - forcing redraw")
                        lastRedrawTime = 0
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_SETTINGS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                settingsReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun createStatusBitmap(status: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        canvas.drawColor(Color.BLACK)
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(status, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun createErrorBitmap(error: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        canvas.drawColor(Color.DKGRAY)
        paint.color = Color.RED
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER

        val lines = error.chunked(30)
        var y = size / 2f - ((lines.size * 30) / 2)

        lines.forEach { line ->
            canvas.drawText(line, size / 2f, y, paint)
            y += 35
        }
        return bitmap
    }

    private fun drawRadarBitmap(
        context: Context,
        aircraft: List<Aircraft>,
        userLat: Double,
        userLon: Double,
        userHeading: Number
    ): Bitmap {
        val radarRange = AppSettings.getRadarViewRange(context)
        val labelSize = AppSettings.getLabelTextSize(context)
        val northUp = AppSettings.getNorthUpOrientation(context)
        val showAirports = AppSettings.getShowAirports(context)

        // Load filter settings
        val altFilterEnabled = AppSettings.getAltitudeFilterEnabled(context)
        val minAlt = AppSettings.getMinAltitudeFilter(context)
        val maxAlt = AppSettings.getMaxAltitudeFilter(context)

        val speedFilterEnabled = AppSettings.getSpeedFilterEnabled(context)
        val minSpeed = AppSettings.getMinSpeedFilter(context)
        val maxSpeed = AppSettings.getMaxSpeedFilter(context)

        // NEW: Elevation Filter Settings
        val elevFilterEnabled = AppSettings.getElevationFilterEnabled(context)
        val minElevation = AppSettings.getMinElevationFilter(context)

        val labelConfig = buildLabelConfigFromAppSettings(context)

        // Filter logic
        val nearby = aircraft.filter { ac ->
            try {
                // 1. Distance Check (Basic Radar Range)
                val distance = Haversine.calculateDistanceMeters(Coord(userLat, userLon), ac.coord)
                if (distance > radarRange) return@filter false

                // 2. Altitude Filter
                if (altFilterEnabled) {
                    val alt = ac.altitude
                    if (alt < minAlt || alt > maxAlt) return@filter false
                }

                // 3. Speed Filter
                if (speedFilterEnabled) {
                    val speedKmh = ac.groundSpeed
                    if (speedKmh < minSpeed || speedKmh > maxSpeed) return@filter false
                }

                // 4. NEW: Elevation Angle Filter
                // Calculate angle: tan(angle) = altitude / distance
                if (elevFilterEnabled) {
                    // Basic flat-earth approximation is sufficient for visual filtering
                    val angle = Math.toDegrees(atan2(ac.altitude, distance))
                    if (angle < minElevation) return@filter false
                }

                true
            } catch (e: Exception) {
                false
            }
        }

        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()

        radarScreen.drawRadarBackground(
            canvas, paint,
            Coord(userLat, userLon),
            userHeading as Double,
            radarRange,
            northUp,
            showAirports
        )

        radarScreen.drawAircraft(
            canvas, paint,
            nearby,
            Coord(userLat, userLon),
            userHeading,
            radarRange,
            labelConfig = labelConfig,
            labelSize = labelSize,
            northUp
        )

        // Debug Overlay
        paint.color = Color.GREEN
        paint.textSize = 20f
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL

        canvas.drawText("GPS: ${"%.4f".format(userLat)}, ${"%.4f".format(userLon)}", 10f, 30f, paint)
        canvas.drawText("HDG: ${"%.1f".format(userHeading)}", 10f, 55f, paint)

        if (lastError != null) {
            paint.color = Color.RED
            canvas.drawText("Err: $lastError", 10f, size - 20f, paint)
        }

        return bmp
    }

    /**
     * Reads the new list-based label configuration from AppSettings.
     */
    private fun buildLabelConfigFromAppSettings(context: Context): LabelConfiguration {
        return LabelConfiguration(
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
    }

    private fun triggerApiPolling(context: Context, lat: Double, lon: Double) {
        scope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastApiCallTime >= apiService.getPollingInterval()) {
                    val rangeNm = AppSettings.getApiSearchRange(context)
                    val result = apiService.fetchNearbyAircraft(lat, lon, rangeNm)
                    AircraftList.updateAircraft(result)
                    lastApiCallTime = currentTime
                    lastError = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "API polling failed: ${e.message}")
                lastError = "API: ${e.message}"
            }
        }
    }

    private fun updateRadarBitmap(
        context: Context,
        emitter: ViewEmitter,
        bitmap: Bitmap
    ) {
        try {
            val rv = RemoteViews(context.packageName, R.layout.datafield_radar)
            rv.setImageViewBitmap(R.id.radar_image, bitmap)
            emitter.updateView(rv)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update bitmap: ${e.message}")
        }
    }
}