package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
import com.myhealthtracker.app.data.meal.MealAnalysisInput
import com.myhealthtracker.app.data.meal.MealAnalysisLauncher
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealTotals
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMealViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        var nextId = "mid-1"
        val pending = mutableListOf<Triple<String, String, String?>>() // id, inputType, localImagePath
        val added = mutableListOf<MealEntry>()
        override fun newMealId() = nextId
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {
            pending.add(Triple(mealId, inputType, localImagePath))
        }
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun failMeal(mealId: String, reason: String) {}
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) {}
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {}
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {
            added.add(MealEntry("aid", date, java.time.Instant.now(), inputType, description, items, totals))
        }
        override fun deleteMeal(mealId: String) {}
    }

    private class FakeLauncher : MealAnalysisLauncher {
        val launched = mutableListOf<MealAnalysisInput>()
        override fun launch(input: MealAnalysisInput) { launched.add(input) }
    }

    private fun vm(repo: MealRepository = FakeRepo(), launcher: MealAnalysisLauncher = FakeLauncher()) =
        AddMealViewModel(repo, launcher, CelebrationController(InMemoryCelebrationStore()))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `analyzeText creates a pending meal, enqueues analysis, and requests close`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.onDescriptionChange("2 eggs"); vm.analyzeText()
        assertEquals(1, repo.pending.size); assertEquals("text", repo.pending.first().second)
        assertEquals(1, launcher.launched.size); assertEquals("2 eggs", launcher.launched.first().text)
        assertTrue(vm.closeScreen.value)
    }

    @Test
    fun `empty text is rejected and nothing is queued`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.onDescriptionChange("   "); vm.analyzeText()
        assertEquals(0, repo.pending.size); assertEquals(0, launcher.launched.size)
        assertFalse(vm.closeScreen.value); assertTrue(vm.errorMessage.value != null)
    }

    @Test
    fun `sendImageForAnalysis queues an image meal with note as text and the local path`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.seedPendingImagePathForTest("/data/x/p.jpg")
        vm.onImageNoteChange("תפוח אורגני"); vm.sendImageForAnalysis()
        assertEquals("image", repo.pending.first().second)
        assertEquals("/data/x/p.jpg", repo.pending.first().third)
        assertEquals("תפוח אורגני", launcher.launched.first().text)
        assertEquals("/data/x/p.jpg", launcher.launched.first().localImagePath)
        assertTrue(vm.closeScreen.value)
    }

    @Test
    fun `sendImageForAnalysis with no pending path is a no-op`() = runTest(dispatcher) {
        val repo = FakeRepo(); val launcher = FakeLauncher(); val vm = vm(repo, launcher)
        vm.sendImageForAnalysis()
        assertEquals(0, repo.pending.size); assertEquals(0, launcher.launched.size)
    }

    @Test
    fun `manual save writes a complete meal directly and requests close`() = runTest(dispatcher) {
        val repo = FakeRepo(); val vm = vm(repo, FakeLauncher())
        vm.switchToManualFallback(); vm.onManualCalChange("500"); vm.saveManualMeal(); advanceUntilIdle()
        assertEquals(1, repo.added.size); assertEquals(500, repo.added.first().totals.calories)
        assertTrue(vm.closeScreen.value)
    }

    @Test
    fun `manual save rejects non-positive calories`() = runTest(dispatcher) {
        val repo = FakeRepo(); val vm = vm(repo, FakeLauncher())
        vm.switchToManualFallback(); vm.onManualCalChange("0"); vm.saveManualMeal(); advanceUntilIdle()
        assertEquals(0, repo.added.size); assertEquals("הקלוריות חייבות להיות גדולות מ-0", vm.errorMessage.value)
    }

    @Test
    fun `reset clears close flag and pending state`() = runTest(dispatcher) {
        val vm = vm()
        vm.onDescriptionChange("x"); vm.analyzeText(); assertTrue(vm.closeScreen.value)
        vm.reset()
        assertFalse(vm.closeScreen.value); assertEquals("", vm.mealDescription.value)
        assertEquals(AddMealStep.InputSelection, vm.step.value)
    }
}
