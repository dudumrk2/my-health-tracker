package com.myhealthtracker.app.data.celebration

import com.myhealthtracker.app.data.model.MealQuality
import java.time.LocalDate
import kotlin.math.abs

/**
 * Pure decision logic for celebrations. Each function takes already-extracted
 * primitives and returns the event(s) to celebrate, or null/empty when no
 * milestone is met. No Android dependencies — fully unit-testable.
 */
object CelebrationRules {

    private const val CALORIE_TOLERANCE = 0.10

    /** Previous-or-same Sunday for [date] (calendar week starts Sunday). */
    fun startOfWeekSunday(date: LocalDate): LocalDate {
        // DayOfWeek.value: MON=1..SUN=7 → days since Sunday: SUN=0, MON=1, ... SAT=6.
        val daysSinceSunday = date.dayOfWeek.value % 7
        return date.minusDays(daysSinceSunday.toLong())
    }

    /** Stable id for the calendar week containing [date] — the Sunday's ISO date. */
    fun weekId(date: LocalDate): String = startOfWeekSunday(date).toString()

    fun stepGoal(steps: Long, goalSteps: Int, date: String): CelebrationEvent? =
        if (goalSteps > 0 && steps >= goalSteps) {
            CelebrationEvent(CelebrationType.STEP_GOAL, "steps-$date")
        } else null

    /** Emits the 2nd- and/or 4th-workout milestones reached this week. */
    fun workoutMilestones(weeklyWorkoutCount: Int, weekId: String): List<CelebrationEvent> {
        val events = mutableListOf<CelebrationEvent>()
        if (weeklyWorkoutCount >= 2) events += CelebrationEvent(CelebrationType.SECOND_WORKOUT, "workout2-$weekId")
        if (weeklyWorkoutCount >= 4) events += CelebrationEvent(CelebrationType.FOURTH_WORKOUT, "workout4-$weekId")
        return events
    }

    /** Great (score == 1) takes precedence over good (score <= 2 && low insulin). */
    fun mealQuality(quality: MealQuality?, mealKey: String): CelebrationEvent? {
        if (quality == null) return null
        return when {
            quality.processedScore == 1 ->
                CelebrationEvent(CelebrationType.GREAT_MEAL, "meal-great-$mealKey")
            quality.processedScore <= 2 && quality.insulinImpact == "low" ->
                CelebrationEvent(CelebrationType.GOOD_MEAL, "meal-good-$mealKey")
            else -> null
        }
    }

    /** Celebrates only when yesterday had >= 3 meals and stayed within ±10% of the goal. */
    fun calorieGoalYesterday(
        yesterdayMealCount: Int,
        yesterdayCalories: Int,
        goalCalories: Int,
        yesterday: String
    ): CelebrationEvent? {
        if (yesterdayMealCount < 3 || goalCalories <= 0) return null
        val withinBand = abs(yesterdayCalories - goalCalories).toDouble() / goalCalories <= CALORIE_TOLERANCE
        return if (withinBand) CelebrationEvent(CelebrationType.CALORIE_GOAL, "calorie-$yesterday") else null
    }
}
