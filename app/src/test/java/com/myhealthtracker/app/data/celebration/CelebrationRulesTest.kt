package com.myhealthtracker.app.data.celebration

import com.myhealthtracker.app.data.model.MealQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CelebrationRulesTest {

    // ── Week anchoring (Sunday-start) ─────────────────────────────────────────
    @Test
    fun startOfWeekSunday_returnsSameDay_forSunday() {
        // 2026-06-21 is a Sunday
        val sunday = LocalDate.of(2026, 6, 21)
        assertEquals(sunday, CelebrationRules.startOfWeekSunday(sunday))
    }

    @Test
    fun startOfWeekSunday_returnsPrecedingSunday_forSaturday() {
        // 2026-06-27 is a Saturday → week started 2026-06-21
        assertEquals(LocalDate.of(2026, 6, 21), CelebrationRules.startOfWeekSunday(LocalDate.of(2026, 6, 27)))
    }

    @Test
    fun weekId_isTheSundayIsoDate() {
        assertEquals("2026-06-21", CelebrationRules.weekId(LocalDate.of(2026, 6, 24)))
    }

    // ── Step goal ─────────────────────────────────────────────────────────────
    @Test
    fun stepGoal_firesWhenStepsMeetGoal() {
        val e = CelebrationRules.stepGoal(8000, 8000, "2026-06-21")
        assertEquals(CelebrationType.STEP_GOAL, e?.type)
        assertEquals("steps-2026-06-21", e?.dedupKey)
    }

    @Test
    fun stepGoal_nullWhenBelowGoal() {
        assertNull(CelebrationRules.stepGoal(7999, 8000, "2026-06-21"))
    }

    @Test
    fun stepGoal_nullWhenGoalIsZero() {
        assertNull(CelebrationRules.stepGoal(5000, 0, "2026-06-21"))
    }

    // ── Workout milestones ─────────────────────────────────────────────────────
    @Test
    fun workoutMilestones_noneBelowTwo() {
        assertEquals(emptyList<CelebrationEvent>(), CelebrationRules.workoutMilestones(1, "2026-06-21"))
    }

    @Test
    fun workoutMilestones_secondOnlyAtTwoAndThree() {
        val two = CelebrationRules.workoutMilestones(2, "2026-06-21")
        assertEquals(listOf(CelebrationType.SECOND_WORKOUT), two.map { it.type })
        assertEquals("workout2-2026-06-21", two.first().dedupKey)
        assertEquals(listOf(CelebrationType.SECOND_WORKOUT), CelebrationRules.workoutMilestones(3, "2026-06-21").map { it.type })
    }

    @Test
    fun workoutMilestones_secondAndFourthAtFourPlus() {
        val four = CelebrationRules.workoutMilestones(4, "2026-06-21")
        assertEquals(listOf(CelebrationType.SECOND_WORKOUT, CelebrationType.FOURTH_WORKOUT), four.map { it.type })
        assertEquals("workout4-2026-06-21", four[1].dedupKey)
    }

    // ── Meal quality (great vs good precedence) ────────────────────────────────
    @Test
    fun mealQuality_greatWhenScoreOne() {
        val e = CelebrationRules.mealQuality(MealQuality(processedScore = 1, insulinImpact = "high"), "m1")
        assertEquals(CelebrationType.GREAT_MEAL, e?.type)
        assertEquals("meal-great-m1", e?.dedupKey)
    }

    @Test
    fun mealQuality_goodWhenScoreTwoAndLowInsulin() {
        val e = CelebrationRules.mealQuality(MealQuality(processedScore = 2, insulinImpact = "low"), "m2")
        assertEquals(CelebrationType.GOOD_MEAL, e?.type)
        assertEquals("meal-good-m2", e?.dedupKey)
    }

    @Test
    fun mealQuality_nullWhenScoreTwoButInsulinNotLow() {
        assertNull(CelebrationRules.mealQuality(MealQuality(processedScore = 2, insulinImpact = "medium"), "m3"))
    }

    @Test
    fun mealQuality_nullWhenProcessedHigh() {
        assertNull(CelebrationRules.mealQuality(MealQuality(processedScore = 3, insulinImpact = "low"), "m4"))
    }

    @Test
    fun mealQuality_nullWhenQualityMissing() {
        assertNull(CelebrationRules.mealQuality(null, "m5"))
    }

    // ── Calorie goal yesterday (±10%, >= 3 meals) ──────────────────────────────
    @Test
    fun calorieGoal_firesWithinBandAndEnoughMeals() {
        val e = CelebrationRules.calorieGoalYesterday(3, 2100, 2000, "2026-06-20")
        assertEquals(CelebrationType.CALORIE_GOAL, e?.type)
        assertEquals("calorie-2026-06-20", e?.dedupKey)
    }

    @Test
    fun calorieGoal_firesAtExactLowerBoundary() {
        // 1800 is exactly -10% of 2000
        assertEquals(CelebrationType.CALORIE_GOAL, CelebrationRules.calorieGoalYesterday(3, 1800, 2000, "2026-06-20")?.type)
    }

    @Test
    fun calorieGoal_nullJustOutsideUpperBoundary() {
        // 2201 = +10.05%
        assertNull(CelebrationRules.calorieGoalYesterday(3, 2201, 2000, "2026-06-20"))
    }

    @Test
    fun calorieGoal_nullWhenFewerThanThreeMeals() {
        assertNull(CelebrationRules.calorieGoalYesterday(2, 2000, 2000, "2026-06-20"))
    }

    @Test
    fun calorieGoal_nullWhenGoalIsZero() {
        assertNull(CelebrationRules.calorieGoalYesterday(3, 0, 0, "2026-06-20"))
    }
}
