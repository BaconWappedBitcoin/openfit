package com.ofit.protocol

/**
 * Starting points for a new workout, opened in the editor to change as the runner likes. Each has a
 * 5-minute warm-up and cool-down. Speeds suit a brisk walker to an easy runner; inclines stay at 8 %
 * or less so they fit common home treadmills.
 */
object WorkoutTemplates {

    data class Template(val name: String, val description: String, val steps: List<WorkoutStep>)

    private fun step(minutes: Double, kph: Double, incline: Double = 0.0) =
        WorkoutStep(speedKph = kph, inclinePercent = incline, durationSeconds = (minutes * 60).toInt())

    private val warmUp = step(5.0, 4.5, 0.0)
    private val coolDown = step(5.0, 4.5, 0.0)

    val all: List<Template> = listOf(
        Template(
            name = "Steady",
            description = "30 min: 20 min at one comfortable pace",
            steps = listOf(warmUp, step(20.0, 5.6, 1.0), coolDown),
        ),
        Template(
            name = "Intervals",
            description = "30 min: 8 × 1 min fast, 1½ min easy",
            steps = listOf(warmUp) +
                (1..8).flatMap { listOf(step(1.0, 8.0, 1.0), step(1.5, 4.8, 1.0)) } +
                listOf(coolDown),
        ),
        Template(
            name = "Hill climb",
            description = "30 min: walk up to 8 % and back down",
            steps = listOf(warmUp) +
                listOf(2.0, 4.0, 6.0, 8.0, 8.0, 6.0, 4.0, 2.0, 1.0, 0.0).map { step(2.0, 5.0, it) } +
                listOf(coolDown),
        ),
        Template(
            name = "Pyramid",
            description = "30 min: speed builds up, peaks, comes back down",
            steps = listOf(warmUp) +
                listOf(5.6, 6.4, 7.2, 8.0, 7.2, 6.4, 5.6, 5.0).map { step(2.5, it, 1.0) } +
                listOf(coolDown),
        ),
        Template(
            name = "Blank",
            description = "20 min at one speed, to build from scratch",
            steps = listOf(step(20.0, 4.8, 0.0)),
        ),
    )
}
