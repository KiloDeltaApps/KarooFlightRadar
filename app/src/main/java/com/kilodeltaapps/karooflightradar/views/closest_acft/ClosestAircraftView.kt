package com.kilodeltaapps.karooflightradar.views.closest_acft

import android.content.Context
import android.graphics.*
import com.kilodeltaapps.karooflightradar.data.Aircraft
import com.kilodeltaapps.karooflightradar.data.Coord
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import com.kilodeltaapps.karooflightradar.utils.Haversine
import kotlin.math.min

class ClosestAircraftView(private val context: Context) {

    // Paint objects reused for performance
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.CENTER
    }
    private val backgroundPaint = Paint().apply { color = Color.BLACK }

    /**
     * Draws the visualization for the closest aircraft.
     */
    fun drawClosestAircraft(
        aircraft: Aircraft?,
        userCoord: Coord,
        userHeading: Double,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (aircraft == null) {
            drawNoTraffic(canvas, width, height)
            return bitmap
        }

        // 2. Calculate Metrics
        val distanceMeters = Haversine.calculateDistanceMeters(userCoord, aircraft.coord)
        val bearingToTarget = Haversine.calculateBearingDegrees(userCoord, aircraft.coord)

        // Relative Bearing: 0° = Straight Ahead
        var relativeBearing = bearingToTarget - userHeading
        while (relativeBearing < -180) relativeBearing += 360
        while (relativeBearing > 180) relativeBearing -= 360

        // 3. Determine Alert Level (Color)
        // Red: < 2km horiz or < 300m vert (Danger)
        // Yellow: < 5km horiz (Warning)
        // White: Info
        val verticalSepMeters = aircraft.altitude // Absolute altitude (approximation)
        val alertColor = when {
            distanceMeters < 2000 -> Color.RED
            distanceMeters < 5000 -> Color.YELLOW
            else -> Color.GREEN
        }

        // 4. Draw Directional Arrow
        val centerX = width / 2f
        val centerY = height * 0.45f // Slightly higher to leave room for text at bottom
        val radius = min(width, height) * 0.30f

        drawArrow(canvas, centerX, centerY, radius, relativeBearing.toFloat(), alertColor)

        // 5. Draw Data Fields
        drawDataOverlay(canvas, aircraft, distanceMeters, width, height, alertColor)

        return bitmap
    }

    private fun drawNoTraffic(canvas: Canvas, width: Int, height: Int) {
        textPaint.textSize = width * 0.1f
        textPaint.color = Color.DKGRAY
        canvas.drawText("SCANNING", width / 2f, height / 2f, textPaint)
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, color: Int) {
        arrowPaint.color = color

        canvas.save()
        canvas.rotate(angle, cx, cy)

        val path = Path()
        // Arrow Shape (Triangle)
        path.moveTo(cx, cy - radius) // Tip
        path.lineTo(cx + radius * 0.8f, cy + radius * 0.8f) // Bottom Right
        path.lineTo(cx, cy + radius * 0.5f) // Indent center
        path.lineTo(cx - radius * 0.8f, cy + radius * 0.8f) // Bottom Left
        path.close()

        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    }

    private fun drawDataOverlay(
        canvas: Canvas,
        aircraft: Aircraft,
        distMeters: Double,
        w: Int,
        h: Int,
        color: Int
    ) {
        val unitDist = AppSettings.getDistanceUnits(context)
        val unitAlt = AppSettings.getAltitudeUnits(context)

        // Format Distance
        val distText = if (unitDist == AppSettings.DistanceUnits.IMPERIAL) {
            val nm = distMeters / 1852.0
            if (nm < 10) "%.1fnm".format(nm) else "%.0fnm".format(nm)
        } else {
            if (distMeters < 1000) "%.0fm".format(distMeters) else "%.1fkm".format(distMeters / 1000.0)
        }

        // Format Altitude
        val altText = if (unitAlt == AppSettings.AltitudeUnits.FEET) {
            val ft = aircraft.altitude * 3.28084
            if (ft > 10000) "FL${(ft/100).toInt()}" else "${ft.toInt()}ft"
        } else {
            "${aircraft.altitude.toInt()}m"
        }

        // Format Vertical Speed Arrow
        val vsArrow = when {
            aircraft.verticalSpeed > 2.0 -> "↑"
            aircraft.verticalSpeed < -2.0 -> "↓"
            else -> ""
        }

        // -- Draw Text Layout --

        // 1. Callsign (Top Center)
        textPaint.color = color
        textPaint.textSize = h * 0.12f
        textPaint.textAlign = Paint.Align.CENTER
        val idText = aircraft.callsign ?: aircraft.registration ?: aircraft.id
        canvas.drawText(idText, w / 2f, h * 0.12f, textPaint)

        // 2. Distance (Bottom Left)
        val bottomY = h * 0.92f

        textPaint.color = Color.WHITE
        textPaint.textSize = h * 0.18f // Big Value
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(distText, w * 0.05f, bottomY, textPaint)

        labelPaint.textSize = h * 0.08f
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("DIST", w * 0.05f, bottomY - (h * 0.18f), labelPaint)

        // 3. Altitude (Bottom Right)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$altText $vsArrow", w * 0.95f, bottomY, textPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ALT", w * 0.95f, bottomY - (h * 0.18f), labelPaint)
    }
}