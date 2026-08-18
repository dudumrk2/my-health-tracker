package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import kotlinx.coroutines.flow.StateFlow

interface MealRepository {
    val meals: StateFlow<List<MealEntry>>

    /** Client-side document id, available offline, so the caller can name the image file and enqueue work. */
    fun newMealId(): String

    /** Writes a doc with status="analyzing", seen=false, empty items/totals. */
    fun createPendingMeal(
        mealId: String, date: String, inputType: String,
        description: String, note: String?, localImagePath: String?
    )

    fun completeMeal(
        mealId: String, items: List<MealItem>, totals: MealTotals,
        recommendation: String?, quality: MealQuality?
    )

    fun failMeal(mealId: String, reason: String)

    /** Flip a failed meal back to analyzing (clears the failure reason) before a manual retry. */
    fun retryMeal(mealId: String)

    fun markMealSeen(mealId: String)

    fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals)

    /** Direct save for manual meals: status="complete", seen=true. */
    fun addMeal(
        date: String, inputType: String, description: String,
        items: List<MealItem>, totals: MealTotals,
        recommendation: String? = null, quality: MealQuality? = null
    )

    fun deleteMeal(mealId: String)
}
