package com.ofit.protocol

/**
 * A sink that records what the engine asked the machine to do.
 *
 * The engine is verified entirely through this: the calls it makes, in order, are the contract.
 */
class RecordingSink : WorkoutCommandSink {

    /** Everything the engine has asked for, in order. */
    val calls: MutableList<SinkCall> = mutableListOf()

    /** What [startWorkout] reports back. */
    var startResult: Boolean = true

    /** What [applyStep] reports back. */
    var applyResult: Boolean = true

    /** What [stopWorkout] reports back. */
    var stopResult: Boolean = true

    override suspend fun startWorkout(speedKph: Double, inclinePercent: Double): Boolean {
        calls += SinkCall.Start(speedKph, inclinePercent)
        return startResult
    }

    override suspend fun applyStep(speedKph: Double, inclinePercent: Double): Boolean {
        calls += SinkCall.Apply(speedKph, inclinePercent)
        return applyResult
    }

    override suspend fun stopWorkout(): Boolean {
        calls += SinkCall.Stop
        return stopResult
    }
}

/** One call the engine made. */
sealed interface SinkCall {
    /** Start the machine. */
    data class Start(val speedKph: Double, val inclinePercent: Double) : SinkCall

    /** Change speed and incline together. */
    data class Apply(val speedKph: Double, val inclinePercent: Double) : SinkCall

    /** Stop the machine. */
    data object Stop : SinkCall
}
