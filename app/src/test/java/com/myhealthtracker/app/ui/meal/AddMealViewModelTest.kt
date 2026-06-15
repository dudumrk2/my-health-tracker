package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.meal.MealAnalysisException
import com.myhealthtracker.app.data.meal.MealAnalysisResult
import com.myhealthtracker.app.data.meal.MealAnalyzer
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMealViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeAnalyzer(
        var result: MealAnalysisResult? = null,
        var error: String? = null
    ) : MealAnalyzer {
        override suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult {
            error?.let { throw MealAnalysisException(it) }
            return result!!
        }
    }

    private class FakeMealRepo : MealRepository {
        val saved = mutableListOf<MealEntry>()
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        override fun addMeal(
            date: String,
            inputType: String,
            description: String,
            items: List<MealItem>,
            totals: MealTotals,
            recommendation: String?,
            quality: MealQuality?
        ) {
            saved.add(
                MealEntry(
                    mealId = "id",
                    date = date,
                    loggedAt = java.time.Instant.now(),
                    inputType = inputType,
                    description = description,
                    items = items,
                    totals = totals,
                    recommendation = recommendation,
                    quality = quality
                )
            )
        }
        override fun deleteMeal(mealId: String) {}
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful text analysis moves to result with items and lowConfidence`() = runTest(dispatcher) {
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Egg", "2", 140, 12, 1, 10)),
                totals = MealTotals(140, 12, 1, 10),
                lowConfidence = true
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer)
        vm.onDescriptionChange("2 eggs")
        vm.analyzeText()
        advanceUntilIdle()
        assertEquals(AddMealStep.ResultState, vm.step.value)
        assertEquals(1, vm.recognizedItems.value.size)
        assertTrue(vm.lowConfidence.value)
    }

    @Test
    fun `empty text is rejected before analysis`() = runTest(dispatcher) {
        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer())
        vm.onDescriptionChange("   ")
        vm.analyzeText()
        advanceUntilIdle()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertTrue(vm.errorMessage.value != null)
    }

    @Test
    fun `analysis error returns to input with friendly message`() = runTest(dispatcher) {
        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer(error = "boom"))
        vm.onDescriptionChange("2 eggs")
        vm.analyzeText()
        advanceUntilIdle()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertEquals("boom", vm.errorMessage.value)
    }

    @Test
    fun `saving recognized items writes to repository`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val quality = MealQuality(
            processedScore = 2,
            hasComplexCarbs = true,
            hasSimpleCarbs = false,
            hasHealthyFats = true,
            insulinImpact = "low"
        )
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Egg", "2", 140, 12, 1, 10)),
                totals = MealTotals(140, 12, 1, 10),
                lowConfidence = false,
                recommendation = "שדרג עם תרד",
                quality = quality
            )
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.onDescriptionChange("2 eggs")
        vm.analyzeText()
        advanceUntilIdle()
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals(1, repo.saved.size)
        assertEquals(140, repo.saved.first().totals.calories)
        assertEquals("שדרג עם תרד", repo.saved.first().recommendation)
        assertEquals(quality, repo.saved.first().quality)
        assertTrue(vm.isSaved.value)
    }

    @Test
    fun `successful image analysis moves to result with items`() = runTest(dispatcher) {
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Apple", "1", 95, 0, 25, 0)),
                totals = MealTotals(95, 0, 25, 0),
                lowConfidence = false
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer)
        vm.analyzeImage("dummy_base64_data")
        advanceUntilIdle()
        assertEquals(AddMealStep.ResultState, vm.step.value)
        assertEquals(1, vm.recognizedItems.value.size)
        assertEquals("Apple", vm.recognizedItems.value.first().name)
    }
}
