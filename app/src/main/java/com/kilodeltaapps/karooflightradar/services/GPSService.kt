package com.kilodeltaapps.karooflightradar.services

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import com.kilodeltaapps.karooflightradar.data.Coord
import com.kilodeltaapps.karooflightradar.extensions.streamDataFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped  // Changed from @Singleton
class GPSService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val karooSystem: KarooSystemService
) {

    val currentLocation = MutableStateFlow(Coord(0.0, 0.0))
    val tempLocation = Coord(26.0,-80.1)
    val currentHeading = MutableStateFlow(0.0)
    val isSimulationActive = MutableStateFlow(false)

    suspend fun startLocationUpdates() {
        try {
            // Connect to Karoo system first
            karooSystem.connect { connected ->
                if (connected) {
                    Log.d("GPSService", "Connected to Karoo system")
                } else {
                    Log.e("GPSService", "Failed to connect to Karoo system")
                }
            }

            // Use the extension function streamDataFlow
            karooSystem.streamDataFlow(DataType.Type.LOCATION).collectLatest { locState ->
                Log.d("GPSService", "Location state received: ${locState::class.simpleName}")


                when (locState) {
                    is StreamState.Streaming -> {
                        val dataPoint = locState.dataPoint
                        val latValue = dataPoint.values[DataType.Field.LOC_LATITUDE]?.toDouble()
                        val lonValue = dataPoint.values[DataType.Field.LOC_LONGITUDE]?.toDouble()

                        val hdgValue = dataPoint.values[DataType.Field.LOC_BEARING]?.toString()?.toDoubleOrNull()

                        val accuracy = dataPoint.values[DataType.Field.LOC_ACCURACY]?.toDouble()

                        Log.d("GPSService", "GPS Data - Lat: $latValue, Lon: $lonValue, Accuracy: $accuracy")

                        if (latValue != null && lonValue != null && accuracy != null && accuracy < 500) {
                            Log.d("GPSService", "✓ Using REAL GPS: $latValue, $lonValue. Accuracy: $accuracy")
                            currentLocation.value = Coord(latValue, lonValue)
                            if (hdgValue != null) {
                                currentHeading.value = hdgValue
                            }
                            isSimulationActive.value = false
                        } else {
                            Log.w("GPSService", "⚠ Poor GPS accuracy: $accuracy, not updating location")
                            currentLocation.value = tempLocation
                            currentHeading.value = 0.0
                            isSimulationActive.value = true
                        }
                    }
                    is StreamState.Searching -> {
                        Log.d("GPSService", "GPS searching...")
                        currentLocation.value = tempLocation
                        currentHeading.value = 0.0
                        isSimulationActive.value = true
                    }
                    is StreamState.Idle -> {
                        Log.d("GPSService", "GPS idle")
                        currentLocation.value = tempLocation
                        currentHeading.value = 0.0
                        Log.d("GPSService", "${currentLocation.value} ${currentHeading.value}")
                        isSimulationActive.value = true
                    }
                    is StreamState.NotAvailable -> {
                        Log.w("GPSService", "GPS not available")
                        currentLocation.value = tempLocation
                        currentHeading.value = 0.0
                        isSimulationActive.value = true
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("GPSService", "Error starting location updates: ${e.message}")
            isSimulationActive.value = true
            currentLocation.value = tempLocation
        }
    }

    fun getCurrentLocation(): Coord {
        return currentLocation.value
    }

    fun getCurrentHeading(): Double {
        Log.d("getCurrentHeading", "Heading: ${currentHeading.value}")
        return currentHeading.value
    }
}