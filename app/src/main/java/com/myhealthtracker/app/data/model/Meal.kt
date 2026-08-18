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

object MealStatus {
    const val ANALYZING = "analyzing"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
}

data class MealTotals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
) {
    companion object {
        fun fromItems(items: List<MealItem>) = MealTotals(
            calories = items.sumOf { it.calories },
            proteinG = items.sumOf { it.proteinG },
            carbsG = items.sumOf { it.carbsG },
            fatG = items.sumOf { it.fatG }
        )
    }
}

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
    val quality: MealQuality? = null,
    // Resilient-analysis pipeline fields. Defaults keep legacy/manual docs "complete" + "seen".
    val status: String = MealStatus.COMPLETE,
    val localImagePath: String? = null,
    val note: String? = null,
    val failureReason: String? = null,
    val seen: Boolean = true
)
