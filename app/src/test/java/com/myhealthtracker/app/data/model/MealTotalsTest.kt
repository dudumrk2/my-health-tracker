package com.myhealthtracker.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MealTotalsTest {
    @Test
    fun `fromItems sums each macro across items`() {
        val items = listOf(
            MealItem("A", "1", 100, 10, 5, 2),
            MealItem("B", "1", 250, 20, 30, 8)
        )
        val totals = MealTotals.fromItems(items)
        assertEquals(350, totals.calories)
        assertEquals(30, totals.proteinG)
        assertEquals(35, totals.carbsG)
        assertEquals(10, totals.fatG)
    }

    @Test
    fun `fromItems on empty list is all zeros`() {
        val totals = MealTotals.fromItems(emptyList())
        assertEquals(0, totals.calories)
        assertEquals(0, totals.proteinG)
    }

    @Test
    fun `meal entry defaults to complete and seen for backward compatibility`() {
        val entry = MealEntry(
            mealId = "1", date = "2026-06-25", loggedAt = java.time.Instant.now(),
            inputType = "text", description = "x",
            items = emptyList(), totals = MealTotals(0, 0, 0, 0)
        )
        assertEquals(MealStatus.COMPLETE, entry.status)
        assertEquals(true, entry.seen)
        assertEquals(null, entry.localImagePath)
    }
}
