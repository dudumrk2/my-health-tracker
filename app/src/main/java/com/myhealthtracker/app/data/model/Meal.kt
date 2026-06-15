package com.myhealthtracker.app.data.model

import java.time.Instant

data class MealItem(
    val name: String,
    val quantity: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

data class MealTotals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

data class MealQuality(
    val processedScore: Int = 1,
    val hasComplexCarbs: Boolean = false,
    val hasSimpleCarbs: Boolean = false,
    val hasHealthyFats: Boolean = false,
    val insulinImpact: String = "low"
)

data class MealEntry(
    val mealId: String,
    val date: String, // yyyy-MM-dd
    val loggedAt: Instant,
    val inputType: String, // "text" | "image"
    val description: String,
    val items: List<MealItem>,
    val totals: MealTotals,
    val recommendation: String? = null,
    val quality: MealQuality? = null
)
