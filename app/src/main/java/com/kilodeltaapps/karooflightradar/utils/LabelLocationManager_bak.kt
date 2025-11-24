package com.kilodeltaapps.karooflightradar.utils

import kotlin.math.*

/**
 * Pro-mode LabelLocationManager.kt (cleaned & type-correct)
 *
 * - Backwards-compatible: same primary API plus optional proMode flag (default = false).
 * - Adds:
 * * angular & distance hysteresis to reduce jitter
 * * soft penalties (crossings, neighbour proximity) + score-based candidate selection
 * * iterative micro-nudge (multiple passes)
 * * optional "pro mode" uses fast DETERMINISTIC LOCAL SEARCH refinement over continuous angles/lengths
 *
 * The calling code can opt-in to pro mode by passing `proMode = true` to optimizeLabel().
 *
 * NOTE: kept many helper names & behaviours from original for easy replace-in-place.
 */
object LabelLocationManager_bak {

    /* ---------- public data classes unchanged ---------- */
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
        REDUCED_VARIABLE_LENGTH,
        PRO_REFINED // Retaining this name for backward compatibility
    }

    /* ---------- pro-mode configurable constants (updated for Local Search) ---------- */
    private const val PREFERRED_LEADER_LENGTH = 60f
    private const val MIN_LEADER_LENGTH      = 40f
    private const val MAX_LEADER_LENGTH      = 100f
    private const val MIN_DIST_TO_AIRCRAFT   = 40f
    private val MIN_DIST_SQ: Float get() = MIN_DIST_TO_AIRCRAFT * MIN_DIST_TO_AIRCRAFT
    private const val LEADER_LINE_CROSSING_TOLERANCE = 4f          // bumped for 480p
    private const val INFLUENCE_RADIUS_SQ    = 300f * 300f

    private const val ANGLE_REST = 135.0
    private val STANDARD_ANGLES  = doubleArrayOf(ANGLE_REST, 45.0, 225.0, 315.0, 180.0, 90.0, 270.0)

    /* hysteresis thresholds (reduce jitter) */
    private const val ANGLE_HYSTERESIS = 22.0          // degrees allowed around previous angle
    private const val LENGTH_HYSTERESIS = 8f           // px allowed around previous length
    private const val POSITION_HYSTERESIS = 6f         // px allowed offset to prefer previous

    /* scoring weights (tunable) */
    private const val PENALTY_CROSSING = 120
    private const val PENALTY_NEAR_LABEL = 30
    private const val PENALTY_OFFCANVAS = 1000
    private const val PENALTY_FORWARD_SECTOR = 200
    private const val WEIGHT_LEADER_LENGTH_DIFF = 1.5f
    private const val WEIGHT_DISTANCE_TO_PREFERRED = 1.0f

    /* local search constants (replace annealing) */
    private const val LOCAL_REFINEMENT_ITER = 10
    private const val LOCAL_SEARCH_ANGLE_STEP = 5.0 // Degrees to step by
    private const val LOCAL_SEARCH_LENGTH_STEP = 5f // Pixels to step by

    /* ---------- 2-phase entry point (backwards-compatible; new param proMode=false) ---------- */
    fun optimizeLabel(
        aircraftX: Float,
        aircraftY: Float,
        aircraftHeading: Double,
        fullWidth: Float,
        fullHeight: Float,
        reducedWidth: Float,
        reducedHeight: Float,
        occupiedAreas: List<FloatArray>,          // static map objects (airports, rings…)
        otherAircraftPositions: List<Pair<Float, Float>>,
        existingLeaderLines: List<FloatArray>,    // static lines (range rings, compass…)
        velocityVectorTip: Pair<Float, Float>?,
        canvasWidth: Int,
        canvasHeight: Int,
        userHeading: Double,
        northUp: Boolean,
        lastValidResult: OptimizationResult?,
        proMode: Boolean = false
    ): OptimizationResult {

        val visualHeading = calculateVisualHeading(aircraftHeading, userHeading, northUp)

        /* ---------- phase 1 – ideal / hysteresis (cheap) ---------- */
        val ideal = calculatePosition(aircraftX, aircraftY, visualHeading, ANGLE_REST, PREFERRED_LEADER_LENGTH)
        if (isValidSingle(ideal.first, ideal.second, fullWidth, fullHeight,
                aircraftX, aircraftY, occupiedAreas, otherAircraftPositions, existingLeaderLines,
                velocityVectorTip, canvasWidth, canvasHeight, visualHeading, null))
            return OptimizationResult(ideal.first, ideal.second, false,
                PREFERRED_LEADER_LENGTH, visualHeading + ANGLE_REST, OptimizationAttempt.FULL_PREFERRED)

        /* ---------- hysteresis: prefer previous if still 'close enough' ---------- */
        lastValidResult?.let { last ->
            val w = if (last.isReducedContent) reducedWidth else fullWidth
            val h = if (last.isReducedContent) reducedHeight else fullHeight
            val rad = Math.toRadians(last.angleDegrees - 90)
            val sx = aircraftX + last.leaderLineLength * cos(rad).toFloat()
            val sy = aircraftY + last.leaderLineLength * sin(rad).toFloat()

            val angleDiff = angularDifferenceDegrees(last.angleDegrees, visualHeading + ANGLE_REST)
            val lengthDiff = abs(last.leaderLineLength - PREFERRED_LEADER_LENGTH)
            val prefRad = Math.toRadians(visualHeading + ANGLE_REST - 90)
            val prefX = aircraftX + PREFERRED_LEADER_LENGTH * cos(prefRad).toFloat()
            val prefY = aircraftY + PREFERRED_LEADER_LENGTH * sin(prefRad).toFloat()
            val posDiff = hypot((sx - prefX).toDouble(), (sy - prefY).toDouble()).toFloat()

            // Accept previous if it's still valid OR within hysteresis window (prefer stability)
            if ((angleDiff < ANGLE_HYSTERESIS && lengthDiff < LENGTH_HYSTERESIS && posDiff < POSITION_HYSTERESIS) &&
                isValidSingle(sx, sy, w, h, aircraftX, aircraftY, occupiedAreas,
                    otherAircraftPositions, existingLeaderLines, velocityVectorTip, canvasWidth, canvasHeight, visualHeading, last.angleDegrees)
            ) {
                return last.copy(x = sx, y = sy)
            }
        }

        /* ---------- phase 2 – candidate discovery (score-based) ---------- */
        val baseCandidate = findBestCandidateScored(
            ax = aircraftX, ay = aircraftY, visualHeading = visualHeading,
            fullWidth = fullWidth, fullHeight = fullHeight, reducedWidth = reducedWidth, reducedHeight = reducedHeight,
            occupied = occupiedAreas, otherAcs = otherAircraftPositions, staticLines = existingLeaderLines,
            vTip = velocityVectorTip, cw = canvasWidth, ch = canvasHeight
        )

        /* ---------- optional pro-mode refinement (deterministic local search) ---------- */
        val refined = if (proMode) {
            refineCandidateDeterministic(baseCandidate, aircraftX, aircraftY, visualHeading,
                fullWidth, fullHeight, reducedWidth, reducedHeight,
                occupiedAreas, otherAircraftPositions, existingLeaderLines, velocityVectorTip, canvasWidth, canvasHeight)
        } else baseCandidate

        /* ---------- phase 3 – iterative nudge to remove small overlaps (only if dynamic neighbours exist) ---------- */
        return if (otherAircraftPositions.size > 1)
            nudgeIfOverlaps(refined, aircraftX, aircraftY, visualHeading,
                fullWidth, fullHeight, reducedWidth, reducedHeight,
                occupiedAreas, otherAircraftPositions, existingLeaderLines,
                velocityVectorTip, canvasWidth, canvasHeight)
        else refined
    }

    /* ========== Candidate discovery with scoring ========== */

    private data class ScoredCandidate(
        val x: Float,
        val y: Float,
        val isReduced: Boolean,
        val length: Float,
        val angleDeg: Double,
        val penalty: Int,
        val attempt: OptimizationAttempt
    )

    private fun findBestCandidateScored(
        ax: Float, ay: Float, visualHeading: Double,
        fullWidth: Float, fullHeight: Float,
        reducedWidth: Float, reducedHeight: Float,
        occupied: List<FloatArray>, otherAcs: List<Pair<Float, Float>>,
        staticLines: List<FloatArray>, vTip: Pair<Float, Float>?,
        cw: Int, ch: Int
    ): OptimizationResult {

        val attempts = arrayOf(
            false to floatArrayOf(PREFERRED_LEADER_LENGTH),
            false to floatArrayOf(MAX_LEADER_LENGTH, MIN_LEADER_LENGTH, (PREFERRED_LEADER_LENGTH * 0.75f)),
            true  to floatArrayOf(PREFERRED_LEADER_LENGTH),
            true  to floatArrayOf(MAX_LEADER_LENGTH, MIN_LEADER_LENGTH, (PREFERRED_LEADER_LENGTH * 0.75f))
        )

        val scored = mutableListOf<ScoredCandidate>()

        for ((reduced, lens) in attempts) {
            val w = if (reduced) reducedWidth else fullWidth
            val h = if (reduced) reducedHeight else fullHeight

            for (a in STANDARD_ANGLES) {
                for (len in lens) {
                    val pos = calculatePosition(ax, ay, visualHeading, a, len)
                    // quick canvas + static checks (reject early)
                    if (!isWithinCanvas(pos.first, pos.second, w, h, cw, ch)) continue
                    if (intersectsOccupied(pos.first, pos.second, w, h, occupied)) continue
                    // allow but penalize intersections with static lines or forward sector
                    var penalty = 0
                    if (isInForwardSector(ax, ay, pos.first, pos.second, visualHeading)) penalty += PENALTY_FORWARD_SECTOR
                    penalty += crossingPenalty(ax, ay, pos.first, pos.second, staticLines)
                    penalty += neighbourPenalty(pos.first, pos.second, otherAcs)

                    // leader length preference (closer to preferred is better)
                    val lenDiff = abs(len - PREFERRED_LEADER_LENGTH)
                    penalty += (lenDiff * WEIGHT_LEADER_LENGTH_DIFF).toInt()

                    scored += ScoredCandidate(
                        x = pos.first, y = pos.second, isReduced = reduced,
                        length = len, angleDeg = visualHeading + a, penalty = penalty,
                        attempt = when {
                            !reduced && lens.size == 1 && lens[0] == PREFERRED_LEADER_LENGTH -> OptimizationAttempt.FULL_PREFERRED
                            !reduced -> OptimizationAttempt.FULL_VARIABLE_LENGTH
                            reduced && lens.size == 1 && lens[0] == PREFERRED_LEADER_LENGTH -> OptimizationAttempt.REDUCED_PREFERRED
                            else -> OptimizationAttempt.REDUCED_VARIABLE_LENGTH
                        }
                    )
                }
            }
        }

        // If we found any scored candidates pick the one with the best (lowest) penalty,
        // but break ties by preferring shorter leader distance to preferred and center bias.
        if (scored.isNotEmpty()) {
            val best = scored.minWithOrNull(
                compareBy<ScoredCandidate> { it.penalty }
                    .thenBy { abs(it.length - PREFERRED_LEADER_LENGTH) }
                    .thenBy { hypot((it.x - ax).toDouble(), (it.y - ay).toDouble()) }
            )!!
            return OptimizationResult(best.x, best.y, best.isReduced, best.length, best.angleDeg, best.attempt)
        }

        // fallback – rest pos reduced
        val f = Math.toRadians(visualHeading + ANGLE_REST - 90)
        return OptimizationResult(
            ax + PREFERRED_LEADER_LENGTH * cos(f).toFloat(),
            ay + PREFERRED_LEADER_LENGTH * sin(f).toFloat(),
            true, PREFERRED_LEADER_LENGTH, visualHeading + ANGLE_REST,
            OptimizationAttempt.REDUCED_VARIABLE_LENGTH)
    }

    /* ========== Pro-mode refinement (Deterministic Local Search) ========== */

    private fun refineCandidateDeterministic(
        base: OptimizationResult,
        ax: Float, ay: Float, visualHeading: Double,
        fullWidth: Float, fullHeight: Float, reducedWidth: Float, reducedHeight: Float,
        occupied: List<FloatArray>, otherAcs: List<Pair<Float, Float>>,
        staticLines: List<FloatArray>, vTip: Pair<Float, Float>?,
        cw: Int, ch: Int
    ): OptimizationResult {

        var best = base
        var bestScore = scoreCandidate(best, ax, ay, visualHeading, fullWidth, fullHeight, reducedWidth, reducedHeight, occupied, otherAcs, staticLines, vTip, cw, ch)

        // Define the structured neighborhood steps
        val angleSteps = doubleArrayOf(0.0, LOCAL_SEARCH_ANGLE_STEP, -LOCAL_SEARCH_ANGLE_STEP)
        val lengthSteps = floatArrayOf(0f, LOCAL_SEARCH_LENGTH_STEP, -LOCAL_SEARCH_LENGTH_STEP)
        val reducedToggle = booleanArrayOf(best.isReducedContent, !best.isReducedContent)

        // Iterate for a fixed number of passes (Local Search)
        repeat(LOCAL_REFINEMENT_ITER) {
            var improved = false
            var currentBestInIter = best
            var currentBestScoreInIter = bestScore

            // Explore the neighborhood of the current best solution
            for (da in angleSteps) {
                for (dl in lengthSteps) {
                    for (tryReduced in reducedToggle) {

                        // Skip the current best position
                        if (da == 0.0 && dl == 0f && tryReduced == best.isReducedContent) continue

                        val trialAngle = (best.angleDegrees + da + 3600.0) % 360.0
                        val trialLen = (best.leaderLineLength + dl).coerceIn(MIN_LEADER_LENGTH, MAX_LEADER_LENGTH)

                        val pos = calculatePosition(ax, ay, visualHeading, trialAngle - visualHeading, trialLen)
                        val w = if (tryReduced) reducedWidth else fullWidth
                        val h = if (tryReduced) reducedHeight else fullHeight

                        // Quick rejection if off-canvas or intersecting static occupied rects
                        if (!isWithinCanvas(pos.first, pos.second, w, h, cw, ch) ||
                            intersectsOccupied(pos.first, pos.second, w, h, occupied)) continue

                        val trial = OptimizationResult(pos.first, pos.second, tryReduced, trialLen, trialAngle, OptimizationAttempt.PRO_REFINED)
                        val trialScore = scoreCandidate(trial, ax, ay, visualHeading, fullWidth, fullHeight, reducedWidth, reducedHeight, occupied, otherAcs, staticLines, vTip, cw, ch)

                        if (trialScore < currentBestScoreInIter) {
                            currentBestScoreInIter = trialScore
                            currentBestInIter = trial
                            improved = true
                        }
                    }
                }
            }

            if (improved) {
                best = currentBestInIter
                bestScore = currentBestScoreInIter
            } else {
                // No improvement in the neighborhood, stop searching
            }
        }

        return best
    }


    /* Score: lower is better */
    private fun scoreCandidate(
        cand: OptimizationResult,
        ax: Float, ay: Float, visualHeading: Double,
        fullWidth: Float, fullHeight: Float, reducedWidth: Float, reducedHeight: Float,
        occupied: List<FloatArray>, otherAcs: List<Pair<Float, Float>>,
        staticLines: List<FloatArray>, vTip: Pair<Float, Float>?,
        cw: Int, ch: Int
    ): Double {
        val w = if (cand.isReducedContent) reducedWidth else fullWidth
        val h = if (cand.isReducedContent) reducedHeight else fullHeight

        // heavy penalty: off-canvas or intersects occupied area
        if (!isWithinCanvas(cand.x, cand.y, w, h, cw, ch)) return PENALTY_OFFCANVAS.toDouble()
        if (intersectsOccupied(cand.x, cand.y, w, h, occupied)) return PENALTY_OFFCANVAS.toDouble()

        var score = 0.0

        // crossing penalty (higher if crossing many)
        score += crossingPenalty(ax, ay, cand.x, cand.y, staticLines).toDouble()

        // neighbour proximity penalty (quadratic-ish)
        score += neighbourPenaltySquared(cand.x, cand.y, otherAcs).toDouble()

        // forward sector heavy penalty
        if (isInForwardSector(ax, ay, cand.x, cand.y, visualHeading)) score += PENALTY_FORWARD_SECTOR.toDouble()

        // distance from preferred leader length
        score += WEIGHT_DISTANCE_TO_PREFERRED * abs(cand.leaderLineLength - PREFERRED_LEADER_LENGTH).toDouble()

        // slight bias towards smaller leader lines (prefer compact)
        score += (cand.leaderLineLength / MAX_LEADER_LENGTH).toDouble() * 2.0

        // slight bias to stay near previous angle / length could be added here if needed

        return score
    }

    /* ========== Ultra-light iterative nudge (multiple small passes) ========== */

    private fun nudgeIfOverlaps(
        original: OptimizationResult,
        ax: Float, ay: Float, visualHeading: Double,
        fullWidth: Float, fullHeight: Float,
        reducedWidth: Float, reducedHeight: Float,
        occupied: List<FloatArray>, otherAcs: List<Pair<Float, Float>>,
        staticLines: List<FloatArray>, vTip: Pair<Float, Float>?,
        cw: Int, ch: Int
    ): OptimizationResult {

        val w = if (original.isReducedContent) reducedWidth else fullWidth
        val h = if (original.isReducedContent) reducedHeight else fullHeight

        var x = original.x
        var y = original.y

        // iterate a few times with decreasing push strength
        var strength = 0.6f
        repeat(3) { iter ->
            for ((ox, oy) in otherAcs) {
                val dx = x - ox
                val dy = y - oy
                val d2 = dx * dx + dy * dy
                if (d2 < MIN_DIST_SQ) {
                    val d = sqrt(d2.toDouble()).toFloat()
                    val push = ((MIN_DIST_TO_AIRCRAFT - d) * strength).coerceAtLeast(0.25f)
                    if (d > 0.01f) {
                        x += (dx / d) * push
                        y += (dy / d) * push
                    } else {
                        // rare exact overlap: push in a deterministic direction based on iter index
                        val angle = Math.toRadians(45.0 * (iter + 1))
                        x += (cos(angle) * push).toFloat()
                        y += (sin(angle) * push).toFloat()
                    }
                }
            }

            // keep on canvas
            val halfW = w * 0.5f; val halfH = h * 0.5f
            x = x.coerceIn(halfW, cw - halfW)
            y = y.coerceIn(halfH, ch - halfH)

            strength *= 0.55f // reduce push strength next iteration
        }

        // final validity check: if still invalid, return original (avoid jitter)
        return if (isValidSingle(x, y, w, h, ax, ay, occupied, otherAcs, staticLines,
                vTip, cw, ch, visualHeading, original.angleDegrees))
            original.copy(x = x, y = y)
        else original
    }

    /* ========== Geometry helpers (adapted) ========== */

    private fun calculatePosition(ax: Float, ay: Float, baseHeading: Double, relAngle: Double, len: Float)
            : Pair<Float, Float> {
        val rad = Math.toRadians(baseHeading + relAngle - 90.0)
        return (ax + len * cos(rad).toFloat()) to (ay + len * sin(rad).toFloat())
    }

    private fun calculateVisualHeading(heading: Double, userHeading: Double, northUp: Boolean): Double =
        if (northUp) heading else ((heading - userHeading) % 360.0 + 360.0) % 360.0

    private fun isWithinCanvas(cx: Float, cy: Float, w: Float, h: Float, cw: Int, ch: Int): Boolean {
        val halfW = w * 0.5f; val halfH = h * 0.5f
        return !(cx - halfW < 0f || cy - halfH < 0f || cx + halfW > cw.toFloat() || cy + halfH > ch.toFloat())
    }

    private fun intersectsOccupied(cx: Float, cy: Float, w: Float, h: Float, occupied: List<FloatArray>): Boolean {
        val l = cx - w / 2f; val t = cy - h / 2f; val r = cx + w / 2f; val b = cy + h / 2f
        for (rect in occupied) {
            if (l < rect[2] && r > rect[0] && t < rect[3] && b > rect[1]) return true
        }
        return false
    }

    /* ---------- single validity check (including static penalties) ---------- */
    private fun isValidSingle(
        cx: Float, cy: Float, w: Float, h: Float,
        ax: Float, ay: Float,
        occupied: List<FloatArray>,
        otherAcs: List<Pair<Float, Float>>,
        staticLines: List<FloatArray>,
        vTip: Pair<Float, Float>?,
        cw: Int, ch: Int,
        visualHeading: Double,
        totalAngle: Double?
    ): Boolean {

        val halfW = w * 0.5f
        val halfH = h * 0.5f
        /* canvas */
        if (cx - halfW < 0f || cy - halfH < 0f || cx + halfW > cw.toFloat() || cy + halfH > ch.toFloat()) return false
        /* static map objects */
        val l = cx - halfW; val t = cy - halfH; val r = cx + halfW; val b = cy + halfH
        for (rect in occupied) {
            if (l < rect[2] && r > rect[0] && t < rect[3] && b > rect[1]) return false
        }
        /* other aircraft symbols (point vs box) */
        for ((ox, oy) in otherAcs) {
            if (abs(cx - ox) < MIN_DIST_TO_AIRCRAFT && abs(cy - oy) < MIN_DIST_TO_AIRCRAFT) return false
        }
        /* leader line vs static lines (reject if crossing) */
        for (line in staticLines) {
            if (segmentsIntersect(ax, ay, cx, cy, line[0], line[1], line[2], line[3])) return false
        }
        /* velocity vector */
        vTip?.let { (vx, vy) ->
            val vl = min(ax, vx) - 10f; val vt = min(ay, vy) - 10f
            val vr = max(ax, vx) + 10f; val vb = max(ay, vy) + 10f
            if (l < vr && r > vl && t < vb && b > vt) return false
        }
        /* forward sector (avoid putting label in nose sector) */
        if (totalAngle != null && isInForwardSector(ax, ay, cx, cy, visualHeading)) return false
        return true
    }

    private fun segmentsIntersect(
        a1x: Float, a1y: Float, a2x: Float, a2y: Float,
        b1x: Float, b1y: Float, b2x: Float, b2y: Float
    ): Boolean {
        val d1x = a2x - a1x; val d1y = a2y - a1y
        val d2x = b2x - b1x; val d2y = b2y - b1y
        val den = d1x * d2y - d1y * d2x
        if (abs(den) < 0.0001f) return false
        val u = ((b1x - a1x) * d2y - (b1y - a1y) * d2x) / den
        val v = ((b1x - a1x) * d1y - (b1y - a1y) * d1x) / den
        return u in 0f..1f && v in 0f..1f
    }

    private fun isInForwardSector(ax: Float, ay: Float, lx: Float, ly: Float, heading: Double): Boolean {
        // Compute angle from aircraft to label and compare to heading +/- 90 deg
        val angle = Math.toDegrees(atan2((ly - ay).toDouble(), (lx - ax).toDouble()))
        var diff = (angle - heading + 540.0) % 360.0 - 180.0
        return abs(diff) < 90.0
    }

    /* ---------- Penalty helpers ---------- */

    private fun crossingPenalty(ax: Float, ay: Float, cx: Float, cy: Float, staticLines: List<FloatArray>): Int {
        var penalty = 0
        for (line in staticLines) {
            if (segmentsIntersect(ax, ay, cx, cy, line[0], line[1], line[2], line[3])) {
                penalty += PENALTY_CROSSING
            }
        }
        return penalty
    }

    private fun neighbourPenalty(cx: Float, cy: Float, others: List<Pair<Float, Float>>): Int {
        var p = 0
        for ((ox, oy) in others) {
            val dx = cx - ox; val dy = cy - oy
            val d2 = dx * dx + dy * dy
            if (d2 < MIN_DIST_SQ) p += PENALTY_NEAR_LABEL
        }
        return p
    }

    private fun neighbourPenaltySquared(cx: Float, cy: Float, others: List<Pair<Float, Float>>): Int {
        // stronger penalty for very near neighbours
        var p = 0
        for ((ox, oy) in others) {
            val dx = cx - ox; val dy = cy - oy
            val d2 = dx * dx + dy * dy
            if (d2 < MIN_DIST_SQ) {
                val d = sqrt(d2.toDouble())
                val extra = ((MIN_DIST_TO_AIRCRAFT - d.toFloat()) * (MIN_DIST_TO_AIRCRAFT - d.toFloat()) * 0.1f).toInt()
                p += PENALTY_NEAR_LABEL + extra
            }
        }
        return p
    }

    /* ---------- utilities ---------- */

    private fun angularDifferenceDegrees(a: Double, b: Double): Double {
        val diff = (a - b + 540.0) % 360.0 - 180.0
        return abs(diff)
    }
}