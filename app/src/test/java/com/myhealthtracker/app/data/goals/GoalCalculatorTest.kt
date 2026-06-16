package com.myhealthtracker.app.data.goals

import com.myhealthtracker.app.data.profile.GoalOverrides
import com.myhealthtracker.app.data.profile.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    private val currentYear = 2026

    // ── BMR / TDEE for known examples ─────────────────────────────────────────

    @Test
    fun maleModerate_computesMifflinTdeeAndMaintenanceCalories() {
        // Mifflin male: 10*80 + 6.25*180 - 5*36 + 5 = 1750 BMR; ×1.55 (moderate) = 2712.5 → 2713
        val profile = UserProfile(
            birthYear = 1990, weightKg = 80.0, heightCm = 180.0, gender = "male",
            primaryGoal = "maintain", activityLevel = "moderate"
        )
        val goals = GoalCalculator.compute(profile, currentYear)

        assertFalse(goals.isGeneric)
        assertEquals(2713, goals.tdee)
        assertEquals(2713, goals.caloriesKcal) // maintain == TDEE
        assertEquals(112, goals.proteinG)       // 1.4 * 80
        assertEquals(72, goals.fatG)            // 0.9 * 80
        assertEquals(404, goals.carbsG)         // (2713 - 448 - 648) / 4
        assertEquals(3000, goals.waterMl)       // male
        assertEquals(7, goals.sleepHoursMin)
        assertEquals(9, goals.sleepHoursMax)
        assertFalse(goals.extremeAdjustmentWarning)
    }

    @Test
    fun loseGoal_appliesDeficitOf500() {
        val profile = UserProfile(
            birthYear = 1990, weightKg = 80.0, heightCm = 180.0, gender = "male",
            primaryGoal = "lose", activityLevel = "moderate"
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertEquals(2213, goals.caloriesKcal) // 2713 - 500
        assertFalse(goals.extremeAdjustmentWarning)
    }

    @Test
    fun gainGoal_appliesModerateSurplus() {
        val profile = UserProfile(
            birthYear = 1990, weightKg = 80.0, heightCm = 180.0, gender = "male",
            primaryGoal = "gain", activityLevel = "moderate"
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertTrue(goals.caloriesKcal > goals.tdee)
    }

    @Test
    fun female65Plus_usesSleep7to8() {
        // birthYear 1955 → age 71
        val profile = UserProfile(
            birthYear = 1955, weightKg = 65.0, heightCm = 165.0, gender = "female",
            primaryGoal = "maintain", activityLevel = "sedentary"
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertEquals(7, goals.sleepHoursMin)
        assertEquals(8, goals.sleepHoursMax)
        assertEquals(2100, goals.waterMl) // female
    }

    @Test
    fun smallPersonLargeDeficit_flagsExtremeWarning() {
        // Tiny TDEE so a 500 kcal deficit exceeds 35%.
        val profile = UserProfile(
            birthYear = 1956, weightKg = 45.0, heightCm = 150.0, gender = "female",
            primaryGoal = "lose", activityLevel = "sedentary"
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertTrue(goals.extremeAdjustmentWarning)
    }

    // ── goalOverrides win over computed values ────────────────────────────────

    @Test
    fun overrides_takePrecedenceOverComputedValues() {
        val profile = UserProfile(
            birthYear = 1990, weightKg = 80.0, heightCm = 180.0, gender = "male",
            primaryGoal = "maintain", activityLevel = "moderate",
            goalOverrides = GoalOverrides(caloriesKcal = 1800, steps = 12000, proteinG = 150, waterMl = 3500, sleepHours = 8)
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertEquals(1800, goals.caloriesKcal)
        assertEquals(12000, goals.steps)
        assertEquals(150, goals.proteinG)
        assertEquals(3500, goals.waterMl)
        assertEquals(8, goals.sleepHoursMin)
        assertEquals(8, goals.sleepHoursMax)
        // carbs recomputed from overridden calories/protein
        assertEquals((1800 - 150 * 4 - goals.fatG * 9) / 4, goals.carbsG)
    }

    // ── Edge cases: missing data → safe generic fallback, never crash ─────────

    @Test
    fun missingWeight_fallsBackToGenericDefaults() {
        val profile = UserProfile(
            birthYear = 1990, weightKg = 0.0, heightCm = 180.0, gender = "male",
            primaryGoal = "lose", activityLevel = "moderate"
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertTrue(goals.isGeneric)
        assertEquals(2000, goals.caloriesKcal)
        assertEquals(8000, goals.steps)
        assertFalse(goals.extremeAdjustmentWarning)
    }

    @Test
    fun emptyProfile_doesNotCrashAndIsGeneric() {
        val goals = GoalCalculator.compute(UserProfile(), currentYear)
        assertTrue(goals.isGeneric)
        assertEquals(2000, goals.caloriesKcal)
        assertEquals(8000, goals.steps)
        assertEquals(7, goals.sleepHoursMin)
        assertEquals(9, goals.sleepHoursMax)
    }

    @Test
    fun invalidActivityLevel_fallsBackToGeneric() {
        val profile = UserProfile(
            birthYear = 1990, weightKg = 80.0, heightCm = 180.0, gender = "male",
            primaryGoal = "maintain", activityLevel = "unknown"
        )
        val goals = GoalCalculator.compute(profile, currentYear)
        assertTrue(goals.isGeneric)
    }
}
