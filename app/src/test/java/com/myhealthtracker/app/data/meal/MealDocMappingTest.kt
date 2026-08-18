package com.myhealthtracker.app.data.meal

import com.myhealthtracker.app.data.model.MealStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MealDocMappingTest {
    @Test
    fun `legacy doc without status maps to complete and seen`() {
        val entry = mealEntryFromMap(
            "id1",
            mapOf(
                "date" to "2026-06-25", "inputType" to "text", "description" to "legacy",
                "items" to emptyList<Any>(),
                "totals" to mapOf("calories" to 200, "proteinG" to 10, "carbsG" to 20, "fatG" to 5)
            )
        )!!
        assertEquals(MealStatus.COMPLETE, entry.status)
        assertEquals(true, entry.seen)
        assertEquals(200, entry.totals.calories)
    }

    @Test
    fun `analyzing doc maps status path and seen flag`() {
        val entry = mealEntryFromMap(
            "id2",
            mapOf(
                "date" to "2026-06-25", "inputType" to "image", "description" to "pending",
                "status" to "analyzing", "seen" to false,
                "localImagePath" to "/data/x/meal_images/p.jpg", "note" to "no sauce"
            )
        )!!
        assertEquals(MealStatus.ANALYZING, entry.status)
        assertEquals(false, entry.seen)
        assertEquals("/data/x/meal_images/p.jpg", entry.localImagePath)
        assertEquals("no sauce", entry.note)
    }

    @Test
    fun `failed doc carries failureReason`() {
        val entry = mealEntryFromMap(
            "id3",
            mapOf(
                "date" to "2026-06-25", "inputType" to "text", "description" to "x",
                "status" to "failed", "seen" to false, "failureReason" to "שירות ה-AI עמוס"
            )
        )!!
        assertEquals(MealStatus.FAILED, entry.status)
        assertEquals("שירות ה-AI עמוס", entry.failureReason)
    }

    @Test
    fun `missing date returns null`() {
        assertEquals(null, mealEntryFromMap("id4", mapOf("inputType" to "text")))
    }
}
