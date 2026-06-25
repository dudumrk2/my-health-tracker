package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealAnalysisRunnerTest {

    private class FakeAnalyzer(var result: MealAnalysisResult? = null, var error: String? = null) : MealAnalyzer {
        var callCount = 0
        override suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult {
            callCount++
            error?.let { throw MealAnalysisException(it) }
            return result!!
        }
    }

    private class RecordingRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        var completedId: String? = null
        var failedId: String? = null
        var failReason: String? = null
        override fun newMealId() = "x"
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {}
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) { completedId = mealId }
        override fun failMeal(mealId: String, reason: String) { failedId = mealId; failReason = reason }
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) {}
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {}
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun deleteMeal(mealId: String) {}
    }

    private val textInput = MealAnalysisInput("m1", "text", "2 eggs", null, "2026-06-25")
    private val imageInput = MealAnalysisInput("m2", "image", null, "/x/p.jpg", "2026-06-25")

    @Test
    fun `success completes meal and reports calories`() = runTest {
        val repo = RecordingRepo()
        val analyzer = FakeAnalyzer(MealAnalysisResult(listOf(MealItem("Egg", "2", 140, 12, 1, 10)), MealTotals(140, 12, 1, 10), false))
        val r = MealAnalysisRunner(analyzer, repo) { "BASE64" }.run(textInput, 0, 4)
        assertEquals(MealAnalysisRunner.Outcome.SUCCESS, r.outcome)
        assertEquals(140, r.calories)
        assertEquals("m1", repo.completedId)
    }

    @Test
    fun `transient failure under max attempts retries without failing the meal`() = runTest {
        val repo = RecordingRepo()
        val r = MealAnalysisRunner(FakeAnalyzer(error = "boom"), repo) { "BASE64" }.run(textInput, 0, 4)
        assertEquals(MealAnalysisRunner.Outcome.RETRY, r.outcome)
        assertNull(repo.failedId)
    }

    @Test
    fun `failure on last attempt marks the meal failed`() = runTest {
        val repo = RecordingRepo()
        val r = MealAnalysisRunner(FakeAnalyzer(error = "boom"), repo) { "BASE64" }.run(textInput, 3, 4)
        assertEquals(MealAnalysisRunner.Outcome.FAILED, r.outcome)
        assertEquals("m1", repo.failedId)
        assertEquals("boom", repo.failReason)
    }

    @Test
    fun `missing image file fails terminally without calling the analyzer`() = runTest {
        val repo = RecordingRepo()
        val analyzer = FakeAnalyzer()
        val r = MealAnalysisRunner(analyzer, repo) { null }.run(imageInput, 0, 4)
        assertEquals(MealAnalysisRunner.Outcome.FAILED, r.outcome)
        assertEquals("m2", repo.failedId)
        assertEquals(0, analyzer.callCount)
    }

    @Test
    fun `toAnalysisInput uses note as text for image meals`() {
        val entry = MealEntry(
            "m3", "2026-06-25", java.time.Instant.now(), "image", "ארוחה מנותחת AI",
            emptyList(), MealTotals(0, 0, 0, 0), localImagePath = "/x/p.jpg", note = "no sauce"
        )
        val input = entry.toAnalysisInput()
        assertEquals("no sauce", input.text)
        assertEquals("/x/p.jpg", input.localImagePath)
        assertEquals("image", input.inputType)
    }
}
