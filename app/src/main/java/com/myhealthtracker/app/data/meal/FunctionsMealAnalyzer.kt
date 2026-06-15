package com.myhealthtracker.app.data.meal

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

private fun anyToInt(v: Any?): Int = when (v) {
    is Number -> v.toDouble().roundToInt() // round to match server-side Math.round
    else -> 0
}

private fun anyToString(v: Any?): String = v as? String ?: ""

/** Pure mapping of the callable's raw response map to a typed result. */
fun mapAnalyzeResponse(raw: Map<*, *>): MealAnalysisResult {
    val itemsRaw = raw["items"] as? List<*>
        ?: throw MealAnalysisException("Response missing items.")
    val items = itemsRaw.map { entry ->
        val m = entry as? Map<*, *> ?: emptyMap<Any, Any>()
        MealItem(
            name = anyToString(m["name"]),
            quantity = anyToString(m["quantity"]),
            calories = anyToInt(m["calories"]),
            proteinG = anyToInt(m["proteinG"]),
            carbsG = anyToInt(m["carbsG"]),
            fatG = anyToInt(m["fatG"])
        )
    }
    val totals = MealTotals(
        calories = items.sumOf { it.calories },
        proteinG = items.sumOf { it.proteinG },
        carbsG = items.sumOf { it.carbsG },
        fatG = items.sumOf { it.fatG }
    )
    val rec = raw["recommendation"] as? String
    val qRaw = raw["quality"] as? Map<*, *>
    val quality = qRaw?.let {
        MealQuality(
            processedScore = anyToInt(it["processedScore"]),
            hasComplexCarbs = it["hasComplexCarbs"] == true,
            hasSimpleCarbs = it["hasSimpleCarbs"] == true,
            hasHealthyFats = it["hasHealthyFats"] == true,
            insulinImpact = anyToString(it["insulinImpact"])
        )
    }
    return MealAnalysisResult(
        items = items,
        totals = totals,
        lowConfidence = raw["lowConfidence"] == true,
        recommendation = rec,
        quality = quality
    )
}

class FunctionsMealAnalyzer(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : MealAnalyzer {

    override suspend fun analyze(
        inputType: String, text: String?, imageBase64: String?, date: String
    ): MealAnalysisResult {
        val payload = buildMap<String, Any> {
            put("inputType", inputType)
            put("date", date)
            if (text != null) put("text", text)
            if (imageBase64 != null) put("imageBase64", imageBase64)
        }
        try {
            val response = functions.getHttpsCallable("analyzeMeal").call(payload).await()
            val raw = response.getData() as? Map<*, *>
                ?: throw MealAnalysisException("Unexpected response.")
            return mapAnalyzeResponse(raw)
        } catch (e: FirebaseFunctionsException) {
            throw MealAnalysisException(
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                        "שירות ה-AI עמוס כרגע. נסה שוב בעוד רגע."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        "נדרשת התחברות מחדש."
                    else -> "לא ניתן לנתח את הארוחה. אפשר להזין ידנית."
                }
            )
        }
    }
}
