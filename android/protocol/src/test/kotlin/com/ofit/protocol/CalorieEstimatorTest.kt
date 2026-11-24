package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalorieEstimatorTest {

    @Test
    fun `walking 5 kph flat at 70 kg is about 4 kcal a minute (ACSM)`() {
        // VO2 = 3.5 + 0.1 * 83.3 = 11.83 ml/kg/min -> 11.83 * 70 / 1000 * 5 = 4.14
        assertEquals(4.14, CalorieEstimator.acsmKcalPerMinute(5.0, 0.0, 70.0), 0.01)
    }

    @Test
    fun `running 10 kph at 2 percent at 80 kg (ACSM)`() {
        // m/min 166.7; VO2 = 3.5 + 33.33 + 0.9*166.7*0.02 = 39.83 -> 39.83 * 80 / 1000 * 5 = 15.93
        assertEquals(15.93, CalorieEstimator.acsmKcalPerMinute(10.0, 2.0, 80.0), 0.01)
    }

    @Test
    fun `incline costs more and downhill counts as flat`() {
        val flat = CalorieEstimator.acsmKcalPerMinute(5.0, 0.0, 70.0)
        assertTrue(CalorieEstimator.acsmKcalPerMinute(5.0, 5.0, 70.0) > flat)
        assertEquals(flat, CalorieEstimator.acsmKcalPerMinute(5.0, -3.0, 70.0), 1e-9)
    }

    @Test
    fun `heart rate formula for a man (Keytel)`() {
        val body = BodyProfile(ageYears = 40, sex = Sex.MALE, weightKg = 80.0)
        // (-55.0969 + 0.6309*140 + 0.1988*80 + 0.2017*40) / 4.184 = 13.67
        assertEquals(13.67, CalorieEstimator.kcalPerMinute(8.0, 0.0, 140, body), 0.01)
    }

    @Test
    fun `heart rate formula for a woman (Keytel)`() {
        val body = BodyProfile(ageYears = 30, sex = Sex.FEMALE, weightKg = 60.0)
        // (-20.4022 + 0.4472*140 - 0.1263*60 + 0.074*30) / 4.184 = 8.81
        assertEquals(8.81, CalorieEstimator.kcalPerMinute(8.0, 0.0, 140, body), 0.01)
    }

    @Test
    fun `without sex, age or a high enough heart rate it falls back to speed and incline`() {
        val acsm = CalorieEstimator.acsmKcalPerMinute(5.0, 1.0, 75.0)
        assertEquals(acsm, CalorieEstimator.kcalPerMinute(5.0, 1.0, 140, BodyProfile(ageYears = 40, weightKg = 75.0)), 1e-9)
        assertEquals(acsm, CalorieEstimator.kcalPerMinute(5.0, 1.0, 80, BodyProfile(40, Sex.MALE, 75.0)), 1e-9)
        assertEquals(acsm, CalorieEstimator.kcalPerMinute(5.0, 1.0, null, BodyProfile(40, Sex.MALE, 75.0)), 1e-9)
    }

    @Test
    fun `no weight uses 70 kg`() {
        assertEquals(
            CalorieEstimator.acsmKcalPerMinute(5.0, 0.0, 70.0),
            CalorieEstimator.kcalPerMinute(5.0, 0.0, null, BodyProfile()),
            1e-9,
        )
    }

    @Test
    fun `the counter adds up over time`() {
        val counter = CalorieCounter()
        repeat(60) { counter.add(1_000, 5.0, 0.0, null, BodyProfile(weightKg = 70.0)) }
        assertEquals(4.14, counter.total, 0.01)
        counter.add(0, 5.0, 0.0, null, BodyProfile())
        assertEquals(4.14, counter.total, 0.01)
        counter.reset()
        assertEquals(0.0, counter.total)
    }
}
