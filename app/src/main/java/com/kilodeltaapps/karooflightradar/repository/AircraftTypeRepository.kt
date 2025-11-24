package com.kilodeltaapps.karooflightradar.repository

import android.content.Context
import android.util.Log
import com.kilodeltaapps.karooflightradar.radar.AircraftShape
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Singleton Repository to handle Aircraft Type lookups.
 * * Usage:
 * 1. Call AircraftTypeRepository.initialize(context) in your Application/Activity onCreate.
 * 2. Call AircraftTypeRepository.getShape(icaoCode) to get the shape.
 */
object AircraftTypeRepository {

    private const val TAG = "AircraftTypeRepo"
    private const val ASSET_FILENAME = "aircraft_icao_types.json"

    // In-memory cache: Maps ICAO code (e.g., "B738") to AircraftShape
    private val shapeCache = HashMap<String, AircraftShape>()

    private var isInitialized = false

    /**
     * Loads the JSON data from assets into memory.
     * This should be called once at app startup.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            // Run on a background thread if file is massive, but for typical ICAO DB (few hundred KB), this is fast.
            // If strict strict performance is needed, wrap this in Coroutine/Thread.
            val jsonString = loadJSONFromAsset(context, ASSET_FILENAME)
            if (jsonString != null) {
                parseAndPopulateCache(jsonString)
                isInitialized = true
                Log.d(TAG, "Initialized with ${shapeCache.size} aircraft types.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AircraftTypeRepository", e)
        }
    }

    /**
     * Very efficient O(1) lookup for drawing loops.
     */
    fun getShape(icao: String?): AircraftShape {
        if (icao.isNullOrBlank()) return AircraftShape.UNKNOWN
        return shapeCache[icao] ?: AircraftShape.UNKNOWN
    }

    private fun loadJSONFromAsset(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading asset $fileName", e)
            null
        }
    }

    private fun parseAndPopulateCache(jsonString: String) {
        try {
            // Assuming the JSON is an Array of Objects based on the table format provided
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue

                // Keys based on your table image: "icao", "class", "engine", "engine_count"
                val icao = obj.optString("icao")
                val icaoClass = obj.optString("class")
                val engineType = obj.optString("engine")

                // Handle engine_count as string or int safely
                val engineCountStr = obj.optString("engine_count", "1")
                val engineCount = engineCountStr.toIntOrNull() ?: 1

                if (icao.isNotBlank()) {
                    val shape = determineShape(icaoClass, engineType, engineCount)
                    shapeCache[icao] = shape
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON", e)
        }
    }

    /**
     * Logic to map properties to Shape.
     * Pre-calculating this during load ensures the lookup is just a map.get()
     */
    private fun determineShape(icaoClass: String, engineType: String, engineCount: Int): AircraftShape {
        // 1. Helicopter
        if (icaoClass.equals("H", ignoreCase = true) || icaoClass.equals("G", ignoreCase = true)) {
            return AircraftShape.TRIANGLE
        }

        // 2. GA Rules
        // "diamond 1 or 2 piston engines or 1 turbine engine"
        val isPiston = engineType.equals("P", ignoreCase = true)
        val isTurbineOrJet = engineType.equals("T", ignoreCase = true) || engineType.equals("J", ignoreCase = true)

        if (engineCount == 1) return AircraftShape.DIAMOND
        if (engineCount == 2 && isPiston) return AircraftShape.DIAMOND

        // 3. Airliner Rules
        // "square. any aircraft with 2+ engines" (excluding the GA twin pistons handled above)
        if (engineCount >= 2) {
            return AircraftShape.SQUARE
        }

        return AircraftShape.DIAMOND
    }
}