package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality

data class MealAnalysisResult(
    val items: List<MealItem>,
    val totals: MealTotals,
    val lowConfidence: Boolean,
    val recommendation: String? = null,
    val quality: MealQuality? = null
)

class MealAnalysisException(message: String) : Exception(message)

interface MealAnalyzer {
    /** Analyze a meal from text or image (base64). Throws MealAnalysisException on failure. */
    suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult
}
