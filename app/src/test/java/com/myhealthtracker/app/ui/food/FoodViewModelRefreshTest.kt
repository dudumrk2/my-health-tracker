package com.myhealthtracker.app.ui.food

import com.myhealthtracker.app.data.insights.InsightRefreshResult
import com.myhealthtracker.app.data.insights.InsightsRefreshException
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.insights.model.DailyInsights
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.water.WaterRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelRefreshTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeMealRepo : MealRepository {
        override val meals: StateFlow<List<MealEntry>> = MutableStateFlow(emptyList())
        override fun addMeal(date: String, inputType: String, description: String, items: List<MealItem>, totals: MealTotals) {}
        override fun deleteMeal(mealId: String) {}
    }

    private class FakeWaterRepo : WaterRepository {
        override val waterLog: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())
        override fun addWater(date: String, amountMl: Int) {}
    }

    private class FakeInsightsRepo : InsightsRepository {
        override val insights: StateFlow<DailyInsights?> = MutableStateFlow(null)
    }

    private class FakeRefresher : InsightsRefresher {
        var calls = 0
        var error: String? = null
        var stateProvider: (() -> Boolean)? = null
        var observedRefreshingDuringCall: Boolean? = null
        override suspend fun refresh(): InsightRefreshResult {
            calls++
            observedRefreshingDuringCall = stateProvider?.invoke()
            error?.let { throw InsightsRefreshException(it) }
            return InsightRefreshResult("written")
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `refreshAdvice sets refreshing during the call and clears it after`() = runTest(dispatcher) {
        val refresher = FakeRefresher()
        val vm = FoodViewModel(FakeMealRepo(), FakeWaterRepo(), FakeInsightsRepo(), refresher)
        refresher.stateProvider = { vm.isRefreshing.value }

        assertEquals(false, vm.isRefreshing.value)
        vm.refreshAdvice()
        advanceUntilIdle()

        assertEquals(1, refresher.calls)
        assertEquals(true, refresher.observedRefreshingDuringCall) // loading was set before the call
        assertEquals(false, vm.isRefreshing.value)                 // and cleared afterwards
    }

    @Test
    fun `refreshAdvice clears refreshing even when the refresher fails`() = runTest(dispatcher) {
        val refresher = FakeRefresher().apply { error = "boom" }
        val vm = FoodViewModel(FakeMealRepo(), FakeWaterRepo(), FakeInsightsRepo(), refresher)

        vm.refreshAdvice()
        advanceUntilIdle()

        assertEquals(1, refresher.calls)
        assertEquals(false, vm.isRefreshing.value)
    }
}
