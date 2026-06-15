package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import kotlinx.coroutines.flow.StateFlow

interface MealRepository {
    val meals: StateFlow<List<MealEntry>>
    fun addMeal(
        date: String,
        inputType: String,
        description: String,
        items: List<MealItem>,
        totals: MealTotals,
        recommendation: String? = null,
        quality: MealQuality? = null
    )
    fun deleteMeal(mealId: String)
}
