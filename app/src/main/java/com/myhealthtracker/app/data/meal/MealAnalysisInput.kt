package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealEntry

/** Everything WorkManager needs to (re)run one meal analysis. */
data class MealAnalysisInput(
    val mealId: String,
    val inputType: String, // "text" | "image"
    val text: String?,
    val localImagePath: String?,
    val date: String
)

/** Rebuild the input from a stored meal (used for manual "try again"). */
fun MealEntry.toAnalysisInput(): MealAnalysisInput = MealAnalysisInput(
    mealId = mealId,
    inputType = inputType,
    text = if (inputType == "image") note else description,
    localImagePath = localImagePath,
    date = date
)
