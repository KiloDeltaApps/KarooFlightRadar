package com.kilodeltaapps.karooflightradar.radar

import android.graphics.Path
import kotlin.math.sqrt

enum class AircraftShape {
    SQUARE,     // Airliner / Heavy
    DIAMOND,    // GA / Unknown / Light
    TRIANGLE,   // Helicopter
    MILITARY,   // <-- NEW
    UNKNOWN;

    /**
     * Generates the drawing path for the shape centered at (x, y).
     */
    fun getPath(x: Float, y: Float, size: Float): Path {
        val path = Path()
        val halfSize = size / 2f

        when (this) {
            SQUARE -> {
                path.moveTo(x - halfSize, y + halfSize)
                path.lineTo(x + halfSize, y + halfSize)
                path.lineTo(x + halfSize, y - halfSize)
                path.lineTo(x - halfSize, y - halfSize)
                path.close()
            }
            TRIANGLE -> {
                val height = size * (sqrt(3.0) / 2.0).toFloat()
                val yOffset = height / 2f
                path.moveTo(x, y - yOffset)
                path.lineTo(x + halfSize, y + yOffset)
                path.lineTo(x - halfSize, y + yOffset)
                path.close()
            }
            MILITARY -> {
                /* half-square top: horizontal bar above a small diamond */
                val barHeight = size * 0.25f                  // thickness of the bar
                val barTop    = y - halfSize                  // top edge
                val barBot    = barTop + barHeight            // bottom edge of bar

                /* top horizontal bar (half-square) */
                path.moveTo(x - halfSize, barBot)             // BL of bar
                path.lineTo(x + halfSize, barBot)             // BR of bar
                path.lineTo(x + halfSize, barTop)             // TR of bar
                path.lineTo(x - halfSize, barTop)             // TL of bar
                path.close()

                /* small diamond below the bar (shared centre) */
                val diamondSize = size * 0.5f
                val dHalf = diamondSize / 2f
                path.moveTo(x, barBot + dHalf)                // bottom tip
                path.lineTo(x + dHalf, y)                     // right tip
                path.lineTo(x, barBot - dHalf)                // top tip (under bar)
                path.lineTo(x - dHalf, y)                     // left tip
                path.close()
            }
            DIAMOND, UNKNOWN -> {
                path.moveTo(x, y - halfSize)
                path.lineTo(x + halfSize, y)
                path.lineTo(x, y + halfSize)
                path.lineTo(x - halfSize, y)
                path.close()
            }
        }
        return path
    }
}