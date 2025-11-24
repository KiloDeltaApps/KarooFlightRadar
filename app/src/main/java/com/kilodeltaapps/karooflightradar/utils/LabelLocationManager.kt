package com.kilodeltaapps.karooflightradar.utils

import kotlin.math.*

object LabelLocationManager_backup {

    data class OptimizationResult(
        val x: Float,
        val y: Float,
        val isReducedContent: Boolean,
        val leaderLineLength: Float,
        val angleDegrees: Double,
        val attemptUsed: OptimizationAttempt = OptimizationAttempt.FULL_PREFERRED
    )

    enum class OptimizationAttempt {
        FULL_PREFERRED,
        FULL_VARIABLE_LENGTH,
        REDUCED_PREFERRED,
        REDUCED_VARIABLE_LENGTH
    }

    // Configuration constants
    private const val PREFERRED_LEADER_LENGTH = 60f
    private const val MIN_LEADER_LENGTH = 40f
    private const val MAX_LEADER_LENGTH = 100f
    private const val MIN_DIST_TO_AIRCRAFT = 40f
    private const val MIN_DIST_SQ = MIN_DIST_TO_AIRCRAFT * MIN_DIST_TO_AIRCRAFT
    private const val LEADER_LINE_CROSSING_TOLERANCE = 2f
    private const val INFLUENCE_RADIUS_SQ = 300f * 300f

    // Standard angles relative to track (0 = straight ahead)
    private const val ANGLE_REST = 135.0
    private val STANDARD_ANGLES = doubleArrayOf(
        ANGLE_REST, 45.0, 225.0, 315.0, 180.0, 90.0, 270.0
    )

    /**
     * Calculates the optimal label position with Hysteresis (Stability).
     */
    fun optimizeLabel(
        aircraftX: Float,
        aircraftY: Float,
        aircraftHeading: Double,
        fullWidth: Float,
        fullHeight: Float,
        reducedWidth: Float,
        reducedHeight: Float,
        occupiedAreas: List<FloatArray>,
        otherAircraftPositions: List<Pair<Float, Float>>,
        existingLeaderLines: List<FloatArray>,
        velocityVectorTip: Pair<Float, Float>?,
        canvasWidth: Int,
        canvasHeight: Int,
        userHeading: Double,
        northUp: Boolean,
        lastValidResult: OptimizationResult? // NEW PARAMETER: The label's position from the last frame
    ): OptimizationResult {

        val visualHeading = calculateVisualHeading(aircraftHeading, userHeading, northUp)

        // --- PRIORITY 1: CHECK IDEAL "REST" POSITION ---
        // Always try to return to the standard 135 degree, full length position if possible.
        val idealPos = calculatePosition(aircraftX, aircraftY, visualHeading, ANGLE_REST, PREFERRED_LEADER_LENGTH)
        if (isValid(
                idealPos.first, idealPos.second, fullWidth, fullHeight,
                aircraftX, aircraftY, occupiedAreas, otherAircraftPositions, existingLeaderLines, velocityVectorTip,
                canvasWidth, canvasHeight, visualHeading, null
            )) {
            return OptimizationResult(idealPos.first, idealPos.second, false, PREFERRED_LEADER_LENGTH, visualHeading + ANGLE_REST, OptimizationAttempt.FULL_PREFERRED)
        }

        // --- PRIORITY 2: HYSTERESIS (STICKINESS) ---
        // If we can't be perfect, stick to the previous valid position if it's still safe.
        // This prevents "dancing" between two suboptimal positions.
        if (lastValidResult != null) {
            val width = if (lastValidResult.isReducedContent) reducedWidth else fullWidth
            val height = if (lastValidResult.isReducedContent) reducedHeight else fullHeight

            // Recalculate exact XY based on the *current* aircraft X/Y but *previous* Angle/Length
            // This keeps the label relative to the moving plane.
            val relativeAngle = lastValidResult.angleDegrees - visualHeading // approximate relative angle conservation
            // Ideally, we keep the absolute visual angle or the relative one.
            // Eurocontrol suggests keeping orientation relative to track constant.
            // We will use the exact parameters from the last result to reconstruct the position.

            // Note: We use the stored relative angle to re-project from the new aircraft center
            // We need to recover the relative angle from the stored absolute angle if possible,
            // or just use the stored angle if it was relative-based.
            // Simplified: We re-verify the specific Angle/Length combo stored in result.

            val rad = Math.toRadians(lastValidResult.angleDegrees - 90)
            val stickX = aircraftX + lastValidResult.leaderLineLength * cos(rad).toFloat()
            val stickY = aircraftY + lastValidResult.leaderLineLength * sin(rad).toFloat()

            if (isValid(
                    stickX, stickY, width, height,
                    aircraftX, aircraftY, occupiedAreas, otherAircraftPositions, existingLeaderLines, velocityVectorTip,
                    canvasWidth, canvasHeight, visualHeading, lastValidResult.angleDegrees
                )) {
                return lastValidResult.copy(x = stickX, y = stickY)
            }
        }

        // --- PRIORITY 3: FULL SEARCH ---
        // If Ideal blocked AND Previous blocked, find a new spot.
        val sortedAngles = getSortedSearchAngles(visualHeading, aircraftX, aircraftY, otherAircraftPositions)

        val optimizationAttempts = arrayOf(
            OptimizationAttempt.FULL_PREFERRED,
            OptimizationAttempt.FULL_VARIABLE_LENGTH,
            OptimizationAttempt.REDUCED_PREFERRED,
            OptimizationAttempt.REDUCED_VARIABLE_LENGTH
        )

        for (attempt in optimizationAttempts) {
            val (useReduced, lengths) = getAttemptConfig(attempt)
            val width = if (useReduced) reducedWidth else fullWidth
            val height = if (useReduced) reducedHeight else fullHeight

            for (angleRelative in sortedAngles) {
                for (length in lengths) {
                    val totalAngle = visualHeading + angleRelative
                    val pos = calculatePosition(aircraftX, aircraftY, 0.0, totalAngle, length) // angle already total

                    if (isValid(
                            pos.first, pos.second, width, height,
                            aircraftX, aircraftY, occupiedAreas, otherAircraftPositions, existingLeaderLines, velocityVectorTip,
                            canvasWidth, canvasHeight, visualHeading, totalAngle
                        )) {
                        return OptimizationResult(
                            pos.first, pos.second, useReduced, length,
                            totalAngle, attempt
                        )
                    }
                }
            }
        }

        // Fallback
        val fRad = Math.toRadians(visualHeading + ANGLE_REST - 90)
        return OptimizationResult(
            aircraftX + PREFERRED_LEADER_LENGTH * cos(fRad).toFloat(),
            aircraftY + PREFERRED_LEADER_LENGTH * sin(fRad).toFloat(),
            true, PREFERRED_LEADER_LENGTH, visualHeading + ANGLE_REST, OptimizationAttempt.REDUCED_VARIABLE_LENGTH
        )
    }

    // --- HELPER LOGIC ---

    private fun calculatePosition(ax: Float, ay: Float, baseHeading: Double, relAngle: Double, len: Float): Pair<Float, Float> {
        val rad = Math.toRadians(baseHeading + relAngle - 90)
        return (ax + len * cos(rad).toFloat()) to (ay + len * sin(rad).toFloat())
    }

    private fun isValid(
        candX: Float, candY: Float, w: Float, h: Float,
        aircraftX: Float, aircraftY: Float,
        occupiedAreas: List<FloatArray>,
        otherAircraftPositions: List<Pair<Float, Float>>,
        existingLeaderLines: List<FloatArray>,
        velocityVectorTip: Pair<Float, Float>?,
        canvasWidth: Int, canvasHeight: Int,
        visualHeading: Double,
        totalAngle: Double?
    ): Boolean {
        val halfW = w / 2f
        val halfH = h / 2f

        // BOUNDS
        if (candX - halfW < 0 || candY - halfH < 0 || candX + halfW > canvasWidth || candY + halfH > canvasHeight) return false

        // OVERLAP
        if (checkLabelOverlap(candX, candY, halfW, halfH, occupiedAreas)) return false

        // PROXIMITY
        if (checkAircraftProximity(candX, candY, otherAircraftPositions)) return false

        // LEADER LINE CROSSING
        if (checkLeaderLineCrossing(aircraftX, aircraftY, candX, candY, existingLeaderLines)) return false

        // VELOCITY VECTOR
        if (checkVelocityVector(aircraftX, aircraftY, velocityVectorTip, candX, candY, halfW, halfH)) return false

        // FORWARD SECTOR
        if (totalAngle != null && isInForwardSector(aircraftX, aircraftY, candX, candY, visualHeading)) return false

        return true
    }
    // --- HELPER LOGIC ---

    private fun getAttemptConfig(attempt: OptimizationAttempt): Pair<Boolean, FloatArray> {
        return when (attempt) {
            OptimizationAttempt.FULL_PREFERRED -> false to floatArrayOf(PREFERRED_LEADER_LENGTH)
            OptimizationAttempt.FULL_VARIABLE_LENGTH -> false to floatArrayOf(MAX_LEADER_LENGTH, MIN_LEADER_LENGTH, PREFERRED_LEADER_LENGTH * 0.75f)
            OptimizationAttempt.REDUCED_PREFERRED -> true to floatArrayOf(PREFERRED_LEADER_LENGTH)
            OptimizationAttempt.REDUCED_VARIABLE_LENGTH -> true to floatArrayOf(MAX_LEADER_LENGTH, MIN_LEADER_LENGTH, PREFERRED_LEADER_LENGTH * 0.75f)
        }
    }

    private fun getSortedSearchAngles(
        visualHeading: Double,
        ax: Float,
        ay: Float,
        others: List<Pair<Float, Float>>
    ): DoubleArray {
        if (others.isEmpty()) return STANDARD_ANGLES

        // 1. Calculate Center of Mass (Repulsive Force)
        var sumX = 0f
        var sumY = 0f
        var count = 0

        for (other in others) {
            val dx = other.first - ax
            val dy = other.second - ay
            if (dx * dx + dy * dy < INFLUENCE_RADIUS_SQ) {
                sumX += other.first
                sumY += other.second
                count++
            }
        }

        if (count == 0) return STANDARD_ANGLES

        val centerX = sumX / count
        val centerY = sumY / count

        // 2. Calculate "Escape Angle" (Away from center)
        val escapeRad = atan2(ay - centerY, ax - centerX)
        var escapeDeg = Math.toDegrees(escapeRad.toDouble())
        escapeDeg = (escapeDeg + 90) % 360
        if (escapeDeg < 0) escapeDeg += 360

        // 3. Sort STANDARD_ANGLES based on closeness to escape angle
        // Create a copy to sort so we don't mutate the static array
        val sorted = STANDARD_ANGLES.clone()

        // Simple insertion sort or selection sort is faster than Arrays.sort for 7 elements
        // But for brevity/readability we use a comparator here.
        // In critical paths, unroll this loop.
        val heading = visualHeading
        return sorted.sortedBy { relAngle ->
            val absAngle = (heading + relAngle) % 360
            var diff = abs(absAngle - escapeDeg)
            if (diff > 180) diff = 360 - diff
            diff
        }.toDoubleArray()
    }

    private fun checkLabelOverlap(cx: Float, cy: Float, hw: Float, hh: Float, occupied: List<FloatArray>): Boolean {
        val l = cx - hw
        val t = cy - hh
        val r = cx + hw
        val b = cy + hh

        // occupied is [left, top, right, bottom]
        for (rect in occupied) {
            if (l < rect[2] && r > rect[0] && t < rect[3] && b > rect[1]) {
                return true
            }
        }
        return false
    }

    private fun checkAircraftProximity(cx: Float, cy: Float, others: List<Pair<Float, Float>>): Boolean {
        for (other in others) {
            val dx = cx - other.first
            val dy = cy - other.second
            if (dx * dx + dy * dy < MIN_DIST_SQ) return true
        }
        return false
    }

    private fun checkLeaderLineCrossing(
        ax: Float, ay: Float, lx: Float, ly: Float,
        existingLines: List<FloatArray>
    ): Boolean {
        for (line in existingLines) {
            // line is [x1, y1, x2, y2]
            if (segmentsIntersect(ax, ay, lx, ly, line[0], line[1], line[2], line[3])) {
                return true
            }
        }
        return false
    }

    private fun segmentsIntersect(
        a1x: Float, a1y: Float, a2x: Float, a2y: Float, // Segment A
        b1x: Float, b1y: Float, b2x: Float, b2y: Float  // Segment B
    ): Boolean {
        // 1. Fast Bounding Box Check
        if (max(a1x, a2x) < min(b1x, b2x) - LEADER_LINE_CROSSING_TOLERANCE ||
            min(a1x, a2x) > max(b1x, b2x) + LEADER_LINE_CROSSING_TOLERANCE ||
            max(a1y, a2y) < min(b1y, b2y) - LEADER_LINE_CROSSING_TOLERANCE ||
            min(a1y, a2y) > max(b1y, b2y) + LEADER_LINE_CROSSING_TOLERANCE) {
            return false
        }

        // 2. Cross Product Check
        val d1x = a2x - a1x
        val d1y = a2y - a1y
        val d2x = b2x - b1x
        val d2y = b2y - b1y

        val denominator = d1x * d2y - d1y * d2x
        if (abs(denominator) < 0.0001f) return false // Parallel

        val uNum = (b1x - a1x) * d2y - (b1y - a1y) * d2x
        val vNum = (b1x - a1x) * d1y - (b1y - a1y) * d1x

        val u = uNum / denominator
        val v = vNum / denominator

        return (u in 0f..1f) && (v in 0f..1f)
    }

    private fun checkVelocityVector(
        ax: Float, ay: Float,
        tip: Pair<Float, Float>?,
        lx: Float, ly: Float, hw: Float, hh: Float
    ): Boolean {
        if (tip == null) return false

        // Bounding box of velocity line
        val vl = min(ax, tip.first) - 10f
        val vt = min(ay, tip.second) - 10f
        val vr = max(ax, tip.first) + 10f
        val vb = max(ay, tip.second) + 10f

        // Label bounds
        val ll = lx - hw
        val lt = ly - hh
        val lr = lx + hw
        val lb = ly + hh

        return (ll < vr && lr > vl && lt < vb && lb > vt)
    }

    private fun isInForwardSector(ax: Float, ay: Float, lx: Float, ly: Float, heading: Double): Boolean {
        val angleToLabel = Math.toDegrees(atan2((ly - ay).toDouble(), (lx - ax).toDouble()))
        val normHeading = (heading + 360) % 360
        val normLabel = (angleToLabel + 360) % 360
        var diff = normLabel - normHeading
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        return abs(diff) < 90
    }

    private fun calculateVisualHeading(heading: Double, userHeading: Double, northUp: Boolean): Double {
        return if (northUp) heading else {
            var rel = heading - userHeading
            while (rel < 0) rel += 360
            while (rel >= 360) rel -= 360
            rel
        }
    }
}
