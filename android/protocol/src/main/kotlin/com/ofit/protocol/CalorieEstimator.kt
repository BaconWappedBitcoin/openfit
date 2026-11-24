package com.ofit.protocol

/** Sex, as the calorie formulas use it. Null (not given) uses a formula that does not need it. */
enum class Sex { MALE, FEMALE }

/** What the calorie estimate knows about the runner. Any of it may be missing. */
data class BodyProfile(
    val ageYears: Int? = null,
    val sex: Sex? = null,
    val weightKg: Double? = null,
) {
    /** The weight used when none is given. */
    val weightOrDefault: Double get() = weightKg ?: DEFAULT_WEIGHT_KG

    companion object {
        const val DEFAULT_WEIGHT_KG = 70.0
    }
}

/**
 * An estimate of calories burned (gross kcal), worked out by OpenFit rather than read from a
 * console, so it also works with no treadmill and takes the runner into account.
 *
 * - With a heart rate (90 bpm or more) and age, sex and weight all known: Keytel et al. (2005),
 *   "Prediction of energy expenditure from heart rate monitoring during submaximal exercise",
 *   J Sports Sci 23(3), the formula without VO2max.
 * - Otherwise: the ACSM metabolic equations for walking and running on a treadmill, from speed,
 *   incline and weight (ACSM's Guidelines for Exercise Testing and Prescription). Downhill counts
 *   as flat, since the equations are not made for negative grades.
 *
 * Neither is exact: the screens say "est".
 */
object CalorieEstimator {

    /** Below this speed the walking equation is used, at or above it the running one. */
    const val RUN_FROM_KPH = 6.4

    /** Heart rates below this are too low for the heart-rate formula to mean much. */
    const val MIN_HR_FOR_FORMULA = 90

    /** kcal per minute at this moment. */
    fun kcalPerMinute(speedKph: Double, inclinePercent: Double, heartRateBpm: Int?, body: BodyProfile): Double {
        val weight = body.weightOrDefault
        val resting = 3.5 * weight / 1000.0 * 5.0
        val age = body.ageYears
        val sex = body.sex
        val bodyWeight = body.weightKg
        if (heartRateBpm != null && heartRateBpm >= MIN_HR_FOR_FORMULA && age != null && sex != null && bodyWeight != null) {
            val kj = when (sex) {
                Sex.MALE -> -55.0969 + 0.6309 * heartRateBpm + 0.1988 * bodyWeight + 0.2017 * age
                Sex.FEMALE -> -20.4022 + 0.4472 * heartRateBpm - 0.1263 * bodyWeight + 0.074 * age
            }
            return (kj / 4.184).coerceAtLeast(resting)
        }
        return acsmKcalPerMinute(speedKph, inclinePercent, weight)
    }

    /** The ACSM treadmill equations: VO2 (ml/kg/min) from speed and grade, then 5 kcal per litre of O2. */
    fun acsmKcalPerMinute(speedKph: Double, inclinePercent: Double, weightKg: Double): Double {
        val metresPerMinute = speedKph.coerceAtLeast(0.0) * 1000.0 / 60.0
        val grade = (inclinePercent / 100.0).coerceAtLeast(0.0)
        val vo2 = if (speedKph < RUN_FROM_KPH) {
            3.5 + 0.1 * metresPerMinute + 1.8 * metresPerMinute * grade
        } else {
            3.5 + 0.2 * metresPerMinute + 0.9 * metresPerMinute * grade
        }
        return vo2 * weightKg / 1000.0 * 5.0
    }
}

/** Adds up the estimate over a workout, a slice of time at a time. */
class CalorieCounter {
    var total: Double = 0.0
        private set

    /** Count [elapsedMillis] spent at this speed, incline and heart rate. */
    fun add(elapsedMillis: Long, speedKph: Double, inclinePercent: Double, heartRateBpm: Int?, body: BodyProfile) {
        if (elapsedMillis <= 0) return
        total += CalorieEstimator.kcalPerMinute(speedKph, inclinePercent, heartRateBpm, body) * elapsedMillis / 60_000.0
    }

    fun reset() {
        total = 0.0
    }
}
