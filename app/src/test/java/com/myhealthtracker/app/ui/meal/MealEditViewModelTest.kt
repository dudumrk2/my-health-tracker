package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.InMemoryCelebrationStore
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealStatus
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        val updated = mutableListOf<Triple<String, List<MealItem>, MealTotals>>()
        val seen = mutableListOf<String>()
        override fun newMealId() = "x"
        override fun createPendingMeal(mealId: String, date: String, inputType: String, description: String, note: String?, localImagePath: String?) {}
        override fun completeMeal(mealId: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun failMeal(mealId: String, reason: String) {}
        override fun retryMeal(mealId: String) {}
        override fun markMealSeen(mealId: String) { seen.add(mealId) }
        override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) { updated.add(Triple(mealId, items, totals)) }
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?) {}
        override fun deleteMeal(mealId: String) {}
    }

    private fun meal() = MealEntry(
        "m1", "2026-06-25", java.time.Instant.now(), "image", "salad",
        listOf(MealItem("A", "1", 100, 10, 5, 2), MealItem("B", "1", 250, 20, 30, 8)),
        MealTotals(350, 30, 35, 10), status = MealStatus.COMPLETE, seen = false,
        quality = MealQuality(processedScore = 1, insulinImpact = "low")
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load marks the meal seen and seeds items`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = MealEditViewModel(repo, CelebrationController(InMemoryCelebrationStore()))
        vm.load(meal()); advanceUntilIdle()
        assertEquals(listOf("m1"), repo.seen)
        assertEquals(2, vm.recognizedItems.value.size)
    }

    @Test
    fun `save excludes removed items, recomputes totals, and updates the meal`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = MealEditViewModel(repo, CelebrationController(InMemoryCelebrationStore()))
        vm.load(meal()); advanceUntilIdle()
        vm.toggleItemRemoved(1) // drop B
        vm.save(); advanceUntilIdle()
        assertEquals(1, repo.updated.size)
        val (id, items, totals) = repo.updated.first()
        assertEquals("m1", id); assertEquals(1, items.size); assertEquals(100, totals.calories)
        assertTrue(vm.saved.value)
    }
}
