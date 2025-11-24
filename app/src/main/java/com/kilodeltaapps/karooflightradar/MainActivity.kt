package com.kilodeltaapps.karooflightradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kilodeltaapps.karooflightradar.repository.AircraftTypeRepository
import com.kilodeltaapps.karooflightradar.ui.settings.SettingsScreen
import com.kilodeltaapps.karooflightradar.ui.theme.KarooFlightRadarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AircraftTypeRepository.initialize(this)

        setContent {
            KarooFlightRadarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}