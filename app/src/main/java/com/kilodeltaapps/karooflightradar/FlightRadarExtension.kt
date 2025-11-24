package com.kilodeltaapps.karooflightradar

import android.util.Log
import com.kilodeltaapps.karooflightradar.datafields.RadarDataField
import com.kilodeltaapps.karooflightradar.services.APIService
import com.kilodeltaapps.karooflightradar.services.GPSService
import com.kilodeltaapps.karooflightradar.services.RadarNotificationService
import dagger.hilt.android.AndroidEntryPoint
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import javax.inject.Inject

@AndroidEntryPoint
class FlightRadarExtension : KarooExtension("karooflightradar", "1.0.0") {

    @Inject
    lateinit var karooSystem: KarooSystemService

    @Inject
    lateinit var gpsService: GPSService

    @Inject
    lateinit var apiService: APIService

    override val types: List<DataTypeImpl> by lazy {
        Log.d("FlightRadarExtension", "REGISTERING types: RadarDataField")
        listOf(
            // FIX: Removed 'karooSystem'.
            // Arguments are now: (GPSService, APIService, String)
            RadarDataField(gpsService, apiService, "karooflightradar")
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("FlightRadarExtension", "onCreate() called")

        RadarNotificationService.createNotificationChannel(this)

        karooSystem.connect @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS) { connected ->
            Log.d("FlightRadarExtension", "KarooSystem connected: $connected")
            if (connected) {
                RadarNotificationService.showRadarControlNotification(this)
                Log.d("FlightRadarExtension", "Radar control notification shown")
            } else {
                Log.e("FlightRadarExtension", "FAILED to connect to KarooSystem")
            }
        }
    }

    override fun onDestroy() {
        Log.d("FlightRadarExtension", "onDestroy()")
        RadarNotificationService.hideRadarControlNotification(this)
        karooSystem.disconnect()
        super.onDestroy()
    }
}