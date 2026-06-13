package com.myhealthtracker.app.data.meal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionsMealAnalyzerTest {

    @Test
    fun `maps callable map response to result`() {
        val raw = mapOf(
            "items" to listOf(
                mapOf("name" to "Egg", "quantity" to "2", "calories" to 140.0,
                      "proteinG" to 12.0, "carbsG" to 1.0, "fatG" to 10.0)
            ),
            "totals" to mapOf("calories" to 140.0, "proteinG" to 12.0, "carbsG" to 1.0, "fatG" to 10.0),
            "lowConfidence" to true
        )
        val result = mapAnalyzeResponse(raw)
        assertEquals(1, result.items.size)
        assertEquals("Egg", result.items[0].name)
        assertEquals(140, result.items[0].calories)
        assertEquals(140, result.totals.calories)
        assertTrue(result.lowConfidence)
    }

    @Test
    fun `empty items maps to empty result with zero totals`() {
        val raw = mapOf("items" to emptyList<Any>(), "lowConfidence" to false)
        val result = mapAnalyzeResponse(raw)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totals.calories)
        assertEquals(false, result.lowConfidence)
    }

    @Test(expected = MealAnalysisException::class)
    fun `missing items throws analysis exception`() {
        mapAnalyzeResponse(mapOf("lowConfidence" to false))
    }
}
