package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.model.MealTotals

/** The most recent completed meal the user has not seen yet, or null. */
fun pickUnseenMealToShow(meals: List<MealEntry>): MealEntry? =
    meals.filter { it.status == MealStatus.COMPLETE && !it.seen }.maxByOrNull { it.loggedAt }

/** Sum macros over completed meals only; in-progress/failed meals contribute nothing. */
fun completeMealTotals(meals: List<MealEntry>): MealTotals {
    val complete = meals.filter { it.status == MealStatus.COMPLETE }
    return MealTotals(
        calories = complete.sumOf { it.totals.calories },
        proteinG = complete.sumOf { it.totals.proteinG },
        carbsG = complete.sumOf { it.totals.carbsG },
        fatG = complete.sumOf { it.totals.fatG }
    )
}

fun failedMealCount(meals: List<MealEntry>): Int = meals.count { it.status == MealStatus.FAILED }
