package com.myhealthtracker.app.ui.meal

import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealStatus
import com.myhealthtracker.app.data.model.MealTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UnseenMealRouterTest {
    private fun meal(id: String, status: String, seen: Boolean, at: Instant, cal: Int = 0) =
        MealEntry(id, "2026-06-25", at, "text", "x", emptyList(), MealTotals(cal, 0, 0, 0), status = status, seen = seen)

    @Test
    fun `picks the most recent complete unseen meal`() {
        val older = meal("a", MealStatus.COMPLETE, false, Instant.ofEpochSecond(100))
        val newer = meal("b", MealStatus.COMPLETE, false, Instant.ofEpochSecond(200))
        assertEquals("b", pickUnseenMealToShow(listOf(older, newer))?.mealId)
    }

    @Test
    fun `ignores seen, analyzing, and failed meals`() {
        val meals = listOf(
            meal("a", MealStatus.COMPLETE, true, Instant.ofEpochSecond(300)),
            meal("b", MealStatus.ANALYZING, false, Instant.ofEpochSecond(400)),
            meal("c", MealStatus.FAILED, false, Instant.ofEpochSecond(500))
        )
        assertNull(pickUnseenMealToShow(meals))
    }

    @Test
    fun `complete-only totals exclude analyzing and failed`() {
        val meals = listOf(
            meal("a", MealStatus.COMPLETE, true, Instant.ofEpochSecond(1), cal = 200),
            meal("b", MealStatus.ANALYZING, false, Instant.ofEpochSecond(2), cal = 999),
            meal("c", MealStatus.FAILED, false, Instant.ofEpochSecond(3), cal = 999)
        )
        assertEquals(200, completeMealTotals(meals).calories)
    }

    @Test
    fun `failed count counts only failed meals`() {
        val meals = listOf(
            meal("a", MealStatus.COMPLETE, true, Instant.ofEpochSecond(1)),
            meal("b", MealStatus.FAILED, false, Instant.ofEpochSecond(2)),
            meal("c", MealStatus.FAILED, false, Instant.ofEpochSecond(3))
        )
        assertEquals(2, failedMealCount(meals))
    }
}
