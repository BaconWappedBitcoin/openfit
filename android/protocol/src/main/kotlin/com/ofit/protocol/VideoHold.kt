package com.ofit.protocol

/**
 * Hold the workout while its video is not moving (user request: "the treadmill should make no changes
 * to the running program while an ad is running").
 *
 * YouTube's player does not say when an ad is playing, but it does report the main video's position.
 * When the video is meant to be playing and that position has not moved for [stallMillis], it is
 * showing an ad (or buffering, or stuck): the workout's clock stops, so speed and incline stay as they
 * are and no step changes. When the video moves again, the workout carries on from the same point,
 * still in step with the picture. A hold never lasts longer than [maxHoldMillis], so a video that never
 * comes back cannot freeze the workout for ever.
 */
class VideoHold(
    private val stallMillis: Long = 2_000,
    private val maxHoldMillis: Long = 120_000,
    /** Movement smaller than this is jitter, not playback. */
    private val minAdvanceSeconds: Double = 0.25,
) {
    private var lastPosition: Double? = null
    private var lastMovedAt: Long = 0
    private var heldSince: Long? = null
    private var gaveUp = false

    /** Whether the workout is being held now. */
    var holding: Boolean = false
        private set

    /** Feed the video's position (seconds) and whether it should be playing. Returns [holding]. */
    fun update(nowMillis: Long, positionSeconds: Double, shouldPlay: Boolean): Boolean {
        val last = lastPosition
        val moved = last == null || positionSeconds - last >= minAdvanceSeconds || positionSeconds < last - 1.0
        if (moved) {
            lastPosition = positionSeconds
            lastMovedAt = nowMillis
            gaveUp = false
        }
        if (!shouldPlay) {
            // Paused on purpose: nothing to hold, and the clock restarts when play resumes.
            lastMovedAt = nowMillis
            release()
            return false
        }
        if (moved) {
            release()
            return false
        }
        if (gaveUp) return false
        if (nowMillis - lastMovedAt >= stallMillis) {
            val since = heldSince ?: nowMillis.also { heldSince = it }
            if (nowMillis - since >= maxHoldMillis) {
                gaveUp = true
                release()
                return false
            }
            holding = true
        }
        return holding
    }

    /** Forget everything (a new workout or a new video). */
    fun reset() {
        lastPosition = null
        gaveUp = false
        release()
    }

    private fun release() {
        holding = false
        heldSince = null
    }
}
