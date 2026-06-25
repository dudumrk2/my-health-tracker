package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationEvent
import com.myhealthtracker.app.data.celebration.CelebrationType
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        var callCount = 0
        var lastInputType: String? = null
        var lastText: String? = null
        var lastImageBase64: String? = null
        override suspend fun analyze(inputType: String, text: String?, imageBase64: String?, date: String): MealAnalysisResult {
            callCount++
            lastInputType = inputType
            lastText = text
            lastImageBase64 = imageBase64
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
        override fun newMealId() = "id"
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {}
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun failMeal(mealId: String, reason: String) {}
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) {}
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {}
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
    fun `reset clears the saved flag and returns to a clean input state`() = runTest(dispatcher) {
        // ViewModels are not scoped per nav entry, so a stale isSaved=true would make
        // re-opening the screen dismiss instantly. reset() must clear it for the next open.
        val repo = FakeMealRepo()
        val vm = vmInResultState(repo = repo, items = listOf(MealItem("A", "1", 100, 10, 5, 2)))
        advanceUntilIdle()
        vm.saveMeal()
        advanceUntilIdle()
        assertTrue(vm.isSaved.value)
        assertEquals(AddMealStep.ResultState, vm.step.value)

        vm.reset()

        assertEquals(false, vm.isSaved.value)
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertEquals("", vm.mealDescription.value)
        assertTrue(vm.recognizedItems.value.isEmpty())
        assertEquals(null, vm.errorMessage.value)
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

    private fun vmInResultState(
        repo: FakeMealRepo = FakeMealRepo(),
        items: List<MealItem>
    ): AddMealViewModel {
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = items,
                totals = MealTotals(
                    items.sumOf { it.calories },
                    items.sumOf { it.proteinG },
                    items.sumOf { it.carbsG },
                    items.sumOf { it.fatG }
                ),
                lowConfidence = false
            )
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.onDescriptionChange("meal")
        vm.analyzeText()
        return vm
    }

    @Test
    fun `toggleItemRemoved adds then removes index`() = runTest(dispatcher) {
        val vm = vmInResultState(items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
        advanceUntilIdle()
        vm.toggleItemRemoved(0)
        assertEquals(setOf(0), vm.excludedIndices.value)
        vm.toggleItemRemoved(0)
        assertEquals(emptySet<Int>(), vm.excludedIndices.value)
    }

    @Test
    fun `toggleItemRemoved ignores out-of-range index`() = runTest(dispatcher) {
        val vm = vmInResultState(items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
        advanceUntilIdle()
        vm.toggleItemRemoved(5)
        assertEquals(emptySet<Int>(), vm.excludedIndices.value)
    }

    @Test
    fun `addItem appends and returns new index`() = runTest(dispatcher) {
        val vm = vmInResultState(items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
        advanceUntilIdle()
        val newIndex = vm.addItem(MealItem("B", "1", 50, 0, 0, 0))
        assertEquals(1, newIndex)
        assertEquals(2, vm.recognizedItems.value.size)
        assertEquals("B", vm.recognizedItems.value[1].name)
    }

    @Test
    fun `saveMeal skips excluded items and recomputes totals`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val vm = vmInResultState(
            repo = repo,
            items = listOf(
                MealItem("A", "1", 100, 10, 5, 2),
                MealItem("B", "1", 250, 20, 30, 8)
            )
        )
        advanceUntilIdle()
        vm.toggleItemRemoved(1) // remove B
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals(1, repo.saved.size)
        val saved = repo.saved.first()
        assertEquals(1, saved.items.size)
        assertEquals("A", saved.items.first().name)
        assertEquals(100, saved.totals.calories)
        assertEquals(10, saved.totals.proteinG)
    }

    @Test
    fun `saveMeal does not save when all items excluded`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val vm = vmInResultState(repo = repo, items = listOf(MealItem("A", "1", 100, 1, 2, 3)))
        advanceUntilIdle()
        vm.toggleItemRemoved(0)
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals(0, repo.saved.size)
        assertEquals("כל הפריטים הוסרו", vm.errorMessage.value)
    }

    @Test
    fun `sendImageForAnalysis passes the note as text and saves it as description`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Apple", "1", 95, 0, 25, 0)),
                totals = MealTotals(95, 0, 25, 0),
                lowConfidence = false
            )
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.seedPendingImageForTest("base64data")
        vm.onImageNoteChange("תפוח אורגני")
        vm.sendImageForAnalysis()
        advanceUntilIdle()
        assertEquals(AddMealStep.ResultState, vm.step.value)
        assertEquals("image", analyzer.lastInputType)
        assertEquals("תפוח אורגני", analyzer.lastText)
        assertEquals("base64data", analyzer.lastImageBase64)
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals("תפוח אורגני", repo.saved.first().description)
    }

    @Test
    fun `sendImageForAnalysis with blank note passes null text and keeps default description`() = runTest(dispatcher) {
        val repo = FakeMealRepo()
        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Apple", "1", 95, 0, 25, 0)),
                totals = MealTotals(95, 0, 25, 0),
                lowConfidence = false
            )
        )
        val vm = AddMealViewModel(repo, analyzer)
        vm.seedPendingImageForTest("base64data")
        vm.sendImageForAnalysis()
        advanceUntilIdle()
        assertEquals(null, analyzer.lastText)
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals("ארוחה מנותחת AI", repo.saved.first().description)
    }

    @Test
    fun `cancelImagePreview clears note and returns to input`() = runTest(dispatcher) {
        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer())
        vm.seedPendingImageForTest("base64data")
        vm.onImageNoteChange("something")
        vm.cancelImagePreview()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertEquals("", vm.imageNote.value)
    }

    @Test
    fun `image analysis failure clears pending image so a second send is a no-op`() = runTest(dispatcher) {
        val analyzer = FakeAnalyzer(error = "boom")
        val vm = AddMealViewModel(FakeMealRepo(), analyzer)
        vm.seedPendingImageForTest("base64data")
        vm.sendImageForAnalysis()
        advanceUntilIdle()
        assertEquals(AddMealStep.InputSelection, vm.step.value)
        assertEquals(null, vm.pendingImageUri.value)
        assertEquals(1, analyzer.callCount)
        // base64 was cleared on failure, so a second send does nothing
        vm.sendImageForAnalysis()
        advanceUntilIdle()
        assertEquals(1, analyzer.callCount)
    }

    @Test
    fun `saving a great AI meal emits a great-meal celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Salad", "1", 120, 5, 10, 3)),
                totals = MealTotals(120, 5, 10, 3),
                lowConfidence = false,
                quality = MealQuality(processedScore = 1, insulinImpact = "low")
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer, controller)
        vm.onDescriptionChange("salad"); vm.analyzeText(); advanceUntilIdle()
        vm.saveMeal(); advanceUntilIdle()
        runCurrent() // flush background collector loop-back

        assertEquals(1, received.size)
        assertEquals(CelebrationType.GREAT_MEAL, received.first().type)
    }

    @Test
    fun `saving a good AI meal emits a good-meal celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Bowl", "1", 300, 20, 30, 8)),
                totals = MealTotals(300, 20, 30, 8),
                lowConfidence = false,
                quality = MealQuality(processedScore = 2, insulinImpact = "low")
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer, controller)
        vm.onDescriptionChange("bowl"); vm.analyzeText(); advanceUntilIdle()
        vm.saveMeal(); advanceUntilIdle()
        runCurrent() // flush background collector loop-back

        assertEquals(1, received.size)
        assertEquals(CelebrationType.GOOD_MEAL, received.first().type)
    }

    @Test
    fun `saving a processed AI meal emits no celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val analyzer = FakeAnalyzer(
            result = MealAnalysisResult(
                items = listOf(MealItem("Pizza", "1", 800, 25, 90, 35)),
                totals = MealTotals(800, 25, 90, 35),
                lowConfidence = false,
                quality = MealQuality(processedScore = 4, insulinImpact = "high")
            )
        )
        val vm = AddMealViewModel(FakeMealRepo(), analyzer, controller)
        vm.onDescriptionChange("pizza"); vm.analyzeText(); advanceUntilIdle()
        vm.saveMeal(); advanceUntilIdle()

        assertEquals(0, received.size)
    }

    @Test
    fun `manual meal save emits no celebration`() = runTest(dispatcher) {
        val controller = CelebrationController(InMemoryCelebrationStore(), scope = this)
        val received = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { controller.events.collect { received.add(it) } }
        runCurrent()

        val vm = AddMealViewModel(FakeMealRepo(), FakeAnalyzer(), controller)
        vm.switchToManualFallback()
        vm.onManualCalChange("500")
        vm.saveMeal(); advanceUntilIdle()

        assertEquals(0, received.size)
    }
}
