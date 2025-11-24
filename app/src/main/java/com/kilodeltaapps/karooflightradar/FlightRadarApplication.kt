package com.kilodeltaapps.karooflightradar

import com.kilodeltaapps.karooflightradar.utils.AppSettings
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlightRadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize settings if first run
        AppSettings.initializeSimulationSettings(this)
    }
}