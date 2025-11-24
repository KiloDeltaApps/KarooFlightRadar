package com.kilodeltaapps.karooflightradar.views.radar

import android.graphics.*
import kotlin.math.*
import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.AircraftDataType
import com.kilodeltaapps.karooflightradar.data.LabelConfiguration
import com.kilodeltaapps.karooflightradar.data.LabelDisplayMode
import com.kilodeltaapps.karooflightradar.data.LabelLineConfig
import com.kilodeltaapps.karooflightradar.repository.AircraftTypeRepository
import com.kilodeltaapps.karooflightradar.utils.LabelLocationManager_bak
import com.kilodeltaapps.karooflightradar.utils.UnitConversions
import com.kilodeltaapps.karooflightradar.utils.AppSettings

class AircraftIndicator {

    // --- Configuration Classes ---

    data class LabelInfo(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val topText: String,
        val bottomText: String,
        val isReduced: Boolean = false,
        val leaderEndX: Float? = null,
        val leaderEndY: Float? = null
    )

    private data class LabelDimensions(
        val fullWidth: Float,
        val fullHeight: Float,
        val reducedWidth: Float,
        val reducedHeight: Float
    )

    // --- State & Storage ---
    private val occupiedAreas = mutableListOf<FloatArray>()
    private val existingLeaderLines = mutableListOf<FloatArray>()
    private var screenUpdateCount = 0
    private val LABEL_UPDATE_INTERVAL = 3

    // --- Paint Objects ---
    private val leaderLinePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 200
    }

    // --- Public API ---

    fun drawAircraftWithLabel(
        canvas: Canvas,
        paint: Paint,
        aircraft: Aircraft,
        aircraftX: Float,
        aircraftY: Float,
        config: LabelConfiguration,
        labelSize: Int,
        otherAircraftPositions: List<Pair<Float, Float>> = emptyList(),
        showTrails: Boolean = false,
        trailHistory: List<Pair<Float, Float>> = emptyList(),
        userHeading: Double,
        northUp: Boolean,
        distanceUnit: AppSettings.DistanceUnits,
        altitudeUnit: AppSettings.AltitudeUnits
    ): LabelInfo? {

        // 1. Screen Bounds Check
        if (aircraftX < 0 || aircraftX > canvas.width || aircraftY < 0 || aircraftY > canvas.height) {
            return null
        }

        // 2. Draw Standard Elements (Marker, Vector, Trail)
        if (showTrails && trailHistory.isNotEmpty()) {
            drawTrail(canvas, paint, trailHistory)
        }

        drawAircraftMarker(canvas, paint, aircraftX, aircraftY, aircraft)
        val velocityTip = drawVelocityVector(canvas, paint, aircraft, aircraftX, aircraftY, userHeading, northUp)

        paint.textSize = labelSize.toFloat()

        val topText = formatLine(aircraft, config.topLine, config.smartAltitudeTransitionMeters, distanceUnit, altitudeUnit)
        val bottomText = formatLine(aircraft, config.bottomLine, config.smartAltitudeTransitionMeters, distanceUnit, altitudeUnit)

        if (topText.isEmpty() && bottomText.isEmpty()) return null

        // 4. Measure Sizes
        val dims = measureLabelSizes(paint, topText, bottomText)

        // 5. Optimize Position
        val result = LabelLocationManager_bak.optimizeLabel(
            aircraftX = aircraftX,
            aircraftY = aircraftY,
            aircraftHeading = aircraft.heading,
            fullWidth = dims.fullWidth,
            fullHeight = dims.fullHeight,
            reducedWidth = dims.reducedWidth,
            reducedHeight = dims.reducedHeight,
            occupiedAreas = occupiedAreas,
            otherAircraftPositions = otherAircraftPositions,
            existingLeaderLines = existingLeaderLines,
            velocityVectorTip = velocityTip,
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            userHeading = userHeading,
            northUp = northUp,
            lastValidResult = null,
            proMode = false
        )

        // 6. Construct Result
        val labelInfo = LabelInfo(
            x = result.x,
            y = result.y,
            width = dims.fullWidth,
            height = dims.fullHeight,
            topText = topText,
            bottomText = bottomText,
            isReduced = result.isReducedContent,
            leaderEndX = aircraftX,
            leaderEndY = aircraftY
        )

        // 7. Draw Vertical Speed Indicator (Next to Label)
        drawVerticalSpeedIndicator(canvas, paint, labelInfo.x, labelInfo.y, aircraft.verticalSpeed.toFloat())

        // 8. Register Collision
        registerCollisionData(labelInfo)

        return labelInfo
    }

    /**
     * Draws aircraft without a label.
     */
    fun drawAircraft(
        canvas: Canvas,
        paint: Paint,
        aircraft: Aircraft,
        aircraftX: Float,
        aircraftY: Float,
        showTrails: Boolean = false,
        trailHistory: List<Pair<Float, Float>> = emptyList(),
        userHeading: Double,
        northUp: Boolean
    ) {
        if (showTrails && trailHistory.isNotEmpty()) {
            drawTrail(canvas, paint, trailHistory)
        }
        drawAircraftMarker(canvas, paint, aircraftX, aircraftY, aircraft)
        drawVelocityVector(canvas, paint, aircraft, aircraftX, aircraftY, userHeading, northUp)

        // Draw VSI next to aircraft marker since no label exists
        drawVerticalSpeedIndicator(canvas, paint, aircraftX + 24f, aircraftY, aircraft.verticalSpeed.toFloat())
    }

    fun drawLabel(canvas: Canvas, paint: Paint, info: LabelInfo) {
        if (info.leaderEndX != null && info.leaderEndY != null) {
            canvas.drawLine(info.leaderEndX, info.leaderEndY, info.x, info.y, leaderLinePaint)
        }

        val originalStyle = paint.style
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER

        val x = info.x
        val y = info.y
        val fontOffset = (paint.descent() + paint.ascent()) / 2
        val originalColor = paint.color

        // Halo
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.BLACK
        if (info.isReduced || info.bottomText.isBlank()) {
            canvas.drawText(info.topText, x, y - fontOffset, paint)
        } else {
            canvas.drawText(info.topText, x, y - (info.height / 4) - fontOffset, paint)
            canvas.drawText(info.bottomText, x, y + (info.height / 4) - fontOffset, paint)
        }

        // Fill
        paint.style = Paint.Style.FILL
        paint.color = originalColor
        if (info.isReduced || info.bottomText.isBlank()) {
            canvas.drawText(info.topText, x, y - fontOffset, paint)
        } else {
            canvas.drawText(info.topText, x, y - (info.height / 4) - fontOffset, paint)
            canvas.drawText(info.bottomText, x, y + (info.height / 4) - fontOffset, paint)
        }

        paint.style = originalStyle
    }

    private fun drawVerticalSpeedIndicator(canvas: Canvas, paint: Paint, x: Float, y: Float, verticalSpeed: Float) {
        if (abs(verticalSpeed) < 0.2f) return

        val originalColor = paint.color
        val originalStyle = paint.style
        paint.style = Paint.Style.FILL
        paint.color = if (verticalSpeed > 0) Color.GREEN else Color.RED

        val path = Path()
        val arrowSize = 12f
        val halfSize = arrowSize / 2

        if (verticalSpeed > 0) {
            path.moveTo(x, y - halfSize)
            path.lineTo(x - halfSize, y + halfSize)
            path.lineTo(x + halfSize, y + halfSize)
        } else {
            path.moveTo(x, y + halfSize)
            path.lineTo(x - halfSize, y - halfSize)
            path.lineTo(x + halfSize, y - halfSize)
        }
        path.close()
        canvas.drawPath(path, paint)
        paint.color = originalColor
        paint.style = originalStyle
    }

    // --- Internal Helpers ---

    private fun registerCollisionData(info: LabelInfo) {
        occupiedAreas.add(floatArrayOf(
            info.x - info.width / 2,
            info.y - info.height / 2,
            info.x + info.width / 2,
            info.y + info.height / 2
        ))
        if (info.leaderEndX != null && info.leaderEndY != null) {
            existingLeaderLines.add(floatArrayOf(info.leaderEndX, info.leaderEndY, info.x, info.y))
        }
    }

    private fun drawTrail(canvas: Canvas, paint: Paint, history: List<Pair<Float, Float>>) {
        if (history.size < 2) return
        val originalShader = paint.shader
        val originalStyle = paint.style
        val originalStroke = paint.strokeWidth
        val originalColor = paint.color
        val head = history.first()
        val tail = history.last()
        val shader = LinearGradient(head.first, head.second, tail.first, tail.second, Color.TRANSPARENT, Color.GRAY, Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.alpha = 255
        val path = Path()
        path.moveTo(head.first, head.second)
        for (i in 1 until history.size) { path.lineTo(history[i].first, history[i].second) }
        canvas.drawPath(path, paint)
        paint.shader = originalShader
        paint.style = originalStyle
        paint.strokeWidth = originalStroke
        paint.color = originalColor
    }

    private fun drawAircraftMarker(canvas: Canvas, paint: Paint, x: Float, y: Float, aircraft: Aircraft) {
        paint.color = Color.MAGENTA
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val shape = AircraftTypeRepository.getShape(aircraft.aircraftType)
        canvas.drawPath(shape.getPath(x, y, 24f), paint)
    }

    private fun drawVelocityVector(canvas: Canvas, paint: Paint, aircraft: Aircraft, x: Float, y: Float, userHeading: Double, northUp: Boolean): Pair<Float, Float>? {
        if (aircraft.groundSpeed < 10) return null
        val vectorHeading = if (northUp) aircraft.heading else {
            var rel = aircraft.heading - userHeading
            if (rel < 0) rel += 360
            if (rel >= 360) rel -= 360
            rel
        }
        val lineLength = (aircraft.groundSpeed / 15f).coerceAtMost(150.0).toFloat()
        val rad = Math.toRadians(vectorHeading - 90)
        val endX = x + lineLength * cos(rad).toFloat()
        val endY = y + lineLength * sin(rad).toFloat()
        paint.color = Color.YELLOW
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawLine(x, y, endX, endY, paint)
        return Pair(endX, endY)
    }

    private fun measureLabelSizes(paint: Paint, topText: String, bottomText: String): LabelDimensions {
        val topWidth = paint.measureText(topText)
        val bottomWidth = paint.measureText(bottomText)
        val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
        val maxWidth = max(topWidth, bottomWidth) + 16f
        val fullHeight = textHeight * 2.2f
        return LabelDimensions(maxWidth, fullHeight, maxWidth, fullHeight)
    }

    // --- Content Formatting ---

    private fun formatLine(
        aircraft: Aircraft,
        lineConfig: LabelLineConfig,
        transitionAltMeters: Int,
        distUnit: AppSettings.DistanceUnits,
        altUnit: AppSettings.AltitudeUnits
    ): String {
        if (lineConfig.fields.isEmpty()) return ""
        val availableValues = lineConfig.fields.map { type ->
            getAircraftDataString(aircraft, type, transitionAltMeters, distUnit, altUnit)
        }
        return when (lineConfig.mode) {
            LabelDisplayMode.STATIC_COMBINED -> availableValues.joinToString(" ")
            LabelDisplayMode.ROLLING -> {
                if (availableValues.isEmpty()) ""
                else availableValues[(screenUpdateCount / LABEL_UPDATE_INTERVAL) % availableValues.size]
            }
        }
    }

    private fun getAircraftDataString(
        aircraft: Aircraft,
        type: AircraftDataType,
        transitionAlt: Int,
        distUnit: AppSettings.DistanceUnits,
        altUnit: AppSettings.AltitudeUnits
    ): String {
        return when (type) {
            AircraftDataType.CALLSIGN -> aircraft.callsign ?: ""
            AircraftDataType.REGISTRATION -> aircraft.registration ?: ""
            AircraftDataType.TYPE -> aircraft.aircraftType ?: ""
            AircraftDataType.ALTITUDE -> UnitConversions.formatAltitude(aircraft.altitude, altUnit)
            AircraftDataType.FLIGHT_LEVEL -> "FL${aircraft.FlightLevel ?: "---"}"
            AircraftDataType.SMART_ALTITUDE -> {
                if (aircraft.altitude >= transitionAlt) "FL${aircraft.FlightLevel ?: "---"}"
                else UnitConversions.formatAltitude(aircraft.altitude, altUnit)
            }
            AircraftDataType.GROUND_SPEED -> UnitConversions.formatSpeed(aircraft.groundSpeed, distUnit)
            AircraftDataType.VERTICAL_SPEED -> UnitConversions.formatVerticalSpeed(aircraft.verticalSpeed, altUnit)
            AircraftDataType.HEADING -> "${aircraft.heading.toInt()}°"
        }
    }

    fun clearOccupiedAreas() {
        occupiedAreas.clear()
        existingLeaderLines.clear()
    }

    fun onScreenUpdate() {
        screenUpdateCount++
        if (screenUpdateCount > 10000) screenUpdateCount = 0
    }
}