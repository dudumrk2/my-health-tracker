package com.myhealthtracker.app.data.goals

import com.myhealthtracker.app.data.profile.GoalOverrides
import com.myhealthtracker.app.data.profile.UserProfile
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Daily goals derived from the profile. All values are general-population estimates,
 * never medical advice (see the fixed disclaimer in the goals UI).
 *
 * @property tdee Total Daily Energy Expenditure used for the calorie target (0 when [isGeneric]).
 * @property isGeneric true when a required profile field was missing and we fell back to defaults.
 * @property extremeAdjustmentWarning true when the requested deficit/surplus exceeds 35% of TDEE.
 */
data class HealthGoals(
    val caloriesKcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val steps: Int,
    val sleepHoursMin: Int,
    val sleepHoursMax: Int,
    val waterMl: Int,
    val tdee: Int,
    val isGeneric: Boolean,
    val extremeAdjustmentWarning: Boolean
)

/**
 * Computes daily goals from a [UserProfile] using the formulas in
 * `docs/goal-setting-research.md` (Mifflin-St Jeor → TDEE → goal adjustment, plus
 * macro/step/sleep/water defaults). Manual [GoalOverrides] always win over computed values.
 *
 * Safety: any missing/invalid profile field triggers a safe fallback to generic
 * defaults instead of crashing or computing on bad data.
 */
object GoalCalculator {

    private val ACTIVITY_FACTORS = mapOf(
        "sedentary" to 1.2,
        "light" to 1.375,
        "moderate" to 1.55,
        "very" to 1.725,
        "extra" to 1.9
    )

    private const val LOSE_DEFICIT_KCAL = 500
    private const val GAIN_SURPLUS_KCAL = 300
    private const val EXTREME_ADJUSTMENT_RATIO = 0.35

    const val DEFAULT_CALORIES = 2000
    const val DEFAULT_STEPS = 8000
    const val DEFAULT_WATER_ML = 2250
    private const val MALE_WATER_ML = 3000
    private const val FEMALE_WATER_ML = 2100

    fun compute(
        profile: UserProfile,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): HealthGoals {
        val age = if (profile.birthYear in 1900..currentYear) currentYear - profile.birthYear else null
        val factor = ACTIVITY_FACTORS[profile.activityLevel]
        val genderValid = profile.gender == "male" || profile.gender == "female"
        val weightValid = profile.weightKg in 30.0..300.0
        val heightValid = profile.heightCm in 100.0..250.0

        val base = if (age != null && factor != null && genderValid && weightValid && heightValid) {
            computeFromProfile(profile, age, factor)
        } else {
            genericGoals(profile)
        }

        return applyOverrides(base, profile.goalOverrides)
    }

    private fun computeFromProfile(profile: UserProfile, age: Int, factor: Double): HealthGoals {
        val w = profile.weightKg
        val h = profile.heightCm
        // Mifflin-St Jeor BMR.
        val bmr = 10 * w + 6.25 * h - 5 * age + if (profile.gender == "male") 5 else -161
        val tdee = (bmr * factor).roundToInt()

        val calories = when (profile.primaryGoal) {
            "lose" -> tdee - LOSE_DEFICIT_KCAL
            "gain" -> tdee + GAIN_SURPLUS_KCAL
            else -> tdee // maintain
        }
        val extreme = tdee > 0 && abs(calories - tdee).toDouble() / tdee > EXTREME_ADJUSTMENT_RATIO

        val protein = (1.4 * w).roundToInt()
        val fat = (0.9 * w).roundToInt()
        val carbs = remainingCarbs(calories, protein, fat)

        val (sleepMin, sleepMax) = if (age >= 65) 7 to 8 else 7 to 9
        val water = if (profile.gender == "male") MALE_WATER_ML else FEMALE_WATER_ML

        return HealthGoals(
            caloriesKcal = calories,
            proteinG = protein,
            fatG = fat,
            carbsG = carbs,
            steps = DEFAULT_STEPS,
            sleepHoursMin = sleepMin,
            sleepHoursMax = sleepMax,
            waterMl = water,
            tdee = tdee,
            isGeneric = false,
            extremeAdjustmentWarning = extreme
        )
    }

    /** Safe defaults when the profile is incomplete. Uses gender for water when available. */
    private fun genericGoals(profile: UserProfile): HealthGoals {
        val protein = (DEFAULT_CALORIES * 0.20 / 4).roundToInt()
        val fat = (DEFAULT_CALORIES * 0.30 / 9).roundToInt()
        val water = when (profile.gender) {
            "male" -> MALE_WATER_ML
            "female" -> FEMALE_WATER_ML
            else -> DEFAULT_WATER_ML
        }
        return HealthGoals(
            caloriesKcal = DEFAULT_CALORIES,
            proteinG = protein,
            fatG = fat,
            carbsG = remainingCarbs(DEFAULT_CALORIES, protein, fat),
            steps = DEFAULT_STEPS,
            sleepHoursMin = 7,
            sleepHoursMax = 9,
            waterMl = water,
            tdee = 0,
            isGeneric = true,
            extremeAdjustmentWarning = false
        )
    }

    private fun remainingCarbs(calories: Int, proteinG: Int, fatG: Int): Int =
        max(0, ((calories - proteinG * 4 - fatG * 9) / 4.0).roundToInt())

    private fun applyOverrides(base: HealthGoals, overrides: GoalOverrides?): HealthGoals {
        if (overrides == null) return base
        val calories = overrides.caloriesKcal ?: base.caloriesKcal
        val protein = overrides.proteinG ?: base.proteinG
        val sleep = overrides.sleepHours
        return base.copy(
            caloriesKcal = calories,
            proteinG = protein,
            carbsG = remainingCarbs(calories, protein, base.fatG),
            steps = overrides.steps ?: base.steps,
            waterMl = overrides.waterMl ?: base.waterMl,
            sleepHoursMin = sleep ?: base.sleepHoursMin,
            sleepHoursMax = sleep ?: base.sleepHoursMax
        )
    }
}
