package com.kilodeltaapps.karooflightradar.views.radar

import android.graphics.*
import android.content.Context
import kotlin.math.*
import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.AirfieldDisplayFormat
import com.kilodeltaapps.karooflightradar.data.Coord
import com.kilodeltaapps.karooflightradar.data.LabelConfiguration
import com.kilodeltaapps.karooflightradar.repository.AirfieldRepository
import com.kilodeltaapps.karooflightradar.utils.Haversine
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import com.kilodeltaapps.karooflightradar.utils.AircraftTrailManager
import com.kilodeltaapps.karooflightradar.utils.UnitConversions

class RadarScreen(private val context: Context) {

    private val aircraftIndicator = AircraftIndicator()
    private val trailManager = AircraftTrailManager()

    fun drawRadarBackground(
        canvas: Canvas,
        paint: Paint,
        userCoord: Coord,
        userHeading: Double,
        maxRange: Double, // In Meters
        northUp: Boolean,
        showAirports: Boolean
    ) {
        val size = canvas.width
        val center = size / 2f
        val distUnit = AppSettings.getDistanceUnits(context)

        drawBackground(canvas)
        drawRangeRings(canvas, paint, center, center, maxRange, size, distUnit)
        drawCompass(canvas, paint, center, center, size)

        if (northUp) {
            drawOwnship(canvas, paint, center, center, userHeading)
        } else {
            drawOwnship(canvas, paint, center, center, 0.0)
        }

        drawNorthIndicator(canvas, paint, center, center, size, userHeading, northUp)

        if (!northUp) {
            drawBearingUpIndicator(canvas, paint, size)
        }

        if (showAirports) {
            drawAirports(canvas, paint, userCoord, userHeading, maxRange, northUp)
        }
    }

    fun drawAircraft(
        canvas: Canvas,
        paint: Paint,
        aircraft: List<Aircraft>,
        userCoord: Coord,
        userHeading: Double,
        maxRange: Double,
        labelConfig: LabelConfiguration,
        labelSize: Int,
        northUp: Boolean
    ) {
        val labelInfos = mutableListOf<AircraftIndicator.LabelInfo>()
        aircraftIndicator.onScreenUpdate()
        aircraftIndicator.clearOccupiedAreas()

        val distUnit = AppSettings.getDistanceUnits(context)
        val altUnit = AppSettings.getAltitudeUnits(context)

        // 1. Update Trails Logic
        val showTrails = AppSettings.getShowAircraftTrails(context)
        if (showTrails) {
            val trailDuration = AppSettings.getTrailLength(context)
            trailManager.updateTrails(aircraft, trailDuration)
        } else {
            trailManager.clear()
        }

        // 2. Process Aircraft (Draw symbols + Calculate labels)
        aircraft.forEachIndexed { index, currentAircraft ->
            val otherAircraftPositions = aircraft
                .filterIndexed { i, _ -> i != index }
                .map {
                    calculateScreenPosition(it.coord, userCoord, userHeading, maxRange, canvas.width, northUp)
                }

            val (aircraftX, aircraftY) = calculateScreenPosition(
                currentAircraft.coord,
                userCoord,
                userHeading,
                maxRange,
                canvas.width,
                northUp
            )

            val trailHistoryScreen = if (showTrails) {
                trailManager.getTrail(currentAircraft.id).map { geoCoord ->
                    calculateScreenPosition(geoCoord, userCoord, userHeading, maxRange, canvas.width, northUp)
                }
            } else {
                emptyList()
            }

            val labelInfo = aircraftIndicator.drawAircraftWithLabel(
                canvas = canvas,
                paint = paint,
                aircraft = currentAircraft,
                aircraftX = aircraftX,
                aircraftY = aircraftY,
                config = labelConfig,
                labelSize = labelSize,
                otherAircraftPositions = otherAircraftPositions,
                showTrails = showTrails,
                trailHistory = trailHistoryScreen,
                userHeading = userHeading,
                northUp = northUp,
                distanceUnit = distUnit,
                altitudeUnit = altUnit
            )
            labelInfo?.let { labelInfos.add(it) }
        }

        // 3. Draw Labels (Second Pass)
        labelInfos.forEach { labelInfo ->
            aircraftIndicator.drawLabel(canvas, paint, labelInfo)
        }
    }

    // --- Internal Drawing Methods ---

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
    }

    private fun drawRangeRings(
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        centerY: Float,
        maxRange: Double,
        canvasSize: Int,
        distUnit: AppSettings.DistanceUnits
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#004d00")
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 20f), 0f)
        paint.alpha = 255

        val maxRadius = canvasSize / 2f - 12f
        val numRings = 4

        for (i in 1..numRings) {
            val radius = (i / numRings.toFloat()) * maxRadius
            canvas.drawCircle(centerX, centerY, radius, paint)
        }

        paint.pathEffect = null
        paint.color = Color.LTGRAY
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL
        paint.alpha = 255

        for (i in 0..numRings) {
            val ringDistMeters = (i * maxRange / numRings)
            val (valInt, unitLabel) = UnitConversions.getDistanceValueAndLabel(ringDistMeters, distUnit)
            val radius = (i / numRings.toFloat()) * maxRadius

            // Avoid drawing text on the center point
            if (i > 0) {
                canvas.drawText("$valInt$unitLabel", centerX + radius, centerY + 20f, paint)
            }
        }
    }

    private fun drawCompass(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, size: Int) {
        paint.strokeWidth = 1f
        paint.color = Color.GRAY
        paint.alpha = 100
        canvas.drawLine(centerX, 0f, centerX, size.toFloat(), paint)
        canvas.drawLine(0f, centerY, size.toFloat(), centerY, paint)
        paint.alpha = 255
    }

    private fun drawOwnship(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, heading: Double) {
        val arrowSize = 15f
        val path = Path()
        path.moveTo(centerX, centerY - arrowSize)
        path.lineTo(centerX - arrowSize / 2f, centerY + arrowSize / 2f)
        path.lineTo(centerX + arrowSize / 2f, centerY + arrowSize / 2f)
        path.close()

        canvas.save()
        canvas.rotate(heading.toFloat(), centerX, centerY)
        paint.style = Paint.Style.FILL
        paint.color = Color.RED
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawNorthIndicator(
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        centerY: Float,
        size: Int,
        userHeading: Double,
        northUp: Boolean
    ) {
        val chevronLen = 30f
        val chevronOffset = 15f

        if (northUp) {
            val x = centerX
            val y = centerY - (size / 2f - 12f) - chevronOffset
            paint.color = Color.RED
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            paint.alpha = 255
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x - chevronLen / 2f, y + chevronLen / 2f)
            path.moveTo(x, y)
            path.lineTo(x + chevronLen / 2f, y + chevronLen / 2f)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 20f
            canvas.drawText("N", x, y + chevronLen + 5f, paint)
        } else {
            val maxRadius = size / 2f - 12f
            val northBearing = -userHeading
            val adjustedBearing = Math.toRadians(northBearing)
            val x = centerX + (maxRadius * sin(adjustedBearing)).toFloat()
            val y = centerY - (maxRadius * cos(adjustedBearing)).toFloat()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(northBearing.toFloat())
            paint.color = Color.RED
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            paint.alpha = 255
            val path = Path()
            path.moveTo(0f, 0f)
            path.lineTo(-chevronLen / 2f, chevronLen)
            path.moveTo(0f, 0f)
            path.lineTo(chevronLen / 2f, chevronLen)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 20f
            canvas.drawText("N", 0f, chevronLen + 15f, paint)
            canvas.restore()
        }
    }

    private fun drawBearingUpIndicator(canvas: Canvas, paint: Paint, size: Int) {
        paint.color = Color.CYAN
        paint.textSize = 20f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEARING UP", size - 5f, 40f, paint)
    }

    private fun drawAirports(
        canvas: Canvas,
        paint: Paint,
        userCoord: Coord,
        userHeading: Double,
        maxRange: Double,
        northUp: Boolean
    ) {
        val airports = AirfieldRepository.getAirportsWithinRadarRange(
            userCoord.latitude, userCoord.longitude, maxRange
        )

        // FIX: Get the display format setting
        val displayFormat = AppSettings.getAirportDisplayFormat(context)

        airports.forEach { (airport, _) ->
            val (x, y) = calculateScreenPosition(
                Coord(airport.lat, airport.lon),
                userCoord,
                userHeading,
                maxRange,
                canvas.width,
                northUp
            )

            if (x in 0f..canvas.width.toFloat() && y in 0f..canvas.height.toFloat()) {
                paint.color = Color.CYAN
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 6f, paint)
                paint.color = Color.BLUE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(x, y, 6f, paint)

                val maxRadius = canvas.width / 2f - 12f
                val radius = sqrt((x - canvas.width/2f).pow(2) + (y - canvas.height/2f).pow(2))
                if (radius > maxRadius * 0.1) {
                    paint.color = Color.LTGRAY
                    paint.textSize = 16f
                    paint.textAlign = Paint.Align.CENTER
                    paint.style = Paint.Style.FILL

                    // FIX: Use the correct format based on settings
                    val label = when (displayFormat) {
                        AirfieldDisplayFormat.ICAO -> airport.icao ?: "APT"
                        AirfieldDisplayFormat.IATA -> airport.iata ?: airport.icao ?: "APT"
                        AirfieldDisplayFormat.CITY_CODE -> {
                            // Truncate long names for display
                            val name = airport.name
                            if (name.length > 12) name.take(9) + "..." else name
                        }

                        AirfieldDisplayFormat.NAME_ICAO -> TODO()
                        AirfieldDisplayFormat.NAME_IATA -> TODO()
                    }

                    canvas.drawText(label, x, y + 16f, paint)
                }
            }
        }
    }

    private fun calculateScreenPosition(
        targetCoord: Coord,
        userCoord: Coord,
        userHeading: Double,
        maxRange: Double,
        canvasSize: Int,
        northUp: Boolean
    ): Pair<Float, Float> {
        val distance = Haversine.calculateDistanceMeters(userCoord, targetCoord)
        val bearing = Haversine.calculateBearingDegrees(userCoord, targetCoord)
        val center = canvasSize / 2f
        val maxRadius = center - 12f
        val radius = (distance / maxRange).coerceIn(0.0, 1.0) * maxRadius
        val displayBearing = if (northUp) bearing else bearing - userHeading
        val adjustedBearing = Math.toRadians(displayBearing)
        val x = center + (radius * sin(adjustedBearing)).toFloat()
        val y = center - (radius * cos(adjustedBearing)).toFloat()
        return x to y
    }
}
