package com.myhealthtracker.app.data.meal

/**
 * Runs a single meal-analysis attempt and updates the repository. Framework-free so it is
 * fully unit-testable; the WorkManager worker supplies the attempt counter and turns
 * [RunResult] into a WorkManager Result.
 */
class MealAnalysisRunner(
    private val analyzer: MealAnalyzer,
    private val repository: MealRepository,
    private val imageToBase64: (String) -> String?
) {
    enum class Outcome { SUCCESS, RETRY, FAILED }
    data class RunResult(val outcome: Outcome, val calories: Int? = null)

    suspend fun run(input: MealAnalysisInput, attempt: Int, maxAttempts: Int): RunResult {
        val base64: String? = if (input.inputType == "image") {
            val encoded = input.localImagePath?.let(imageToBase64)
            if (encoded == null) {
                repository.failMeal(input.mealId, "התמונה אינה זמינה")
                return RunResult(Outcome.FAILED)
            }
            encoded
        } else null

        return try {
            val result = analyzer.analyze(input.inputType, input.text, base64, input.date)
            repository.completeMeal(input.mealId, result.items, result.totals, result.recommendation, result.quality)
            RunResult(Outcome.SUCCESS, result.totals.calories)
        } catch (e: MealAnalysisException) {
            if (attempt + 1 < maxAttempts) {
                RunResult(Outcome.RETRY)
            } else {
                repository.failMeal(input.mealId, e.message ?: "הניתוח נכשל")
                RunResult(Outcome.FAILED)
            }
        }
    }

    companion object { const val MAX_ATTEMPTS = 4 }
}
