package com.myhealthtracker.app.data.insights

import com.myhealthtracker.app.data.insights.model.InsightToday
import com.myhealthtracker.app.data.insights.model.InsightTomorrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightSelectionTest {

    private val today = InsightToday(
        general = "סיכום כללי טוב.",
        nutrition = "החלבון יפה.",
        activity = "צעדים מצוינים.",
        sleep = "שינה סבירה."
    )
    private val tomorrow = InsightTomorrow(
        nutrition = "כדאי ארוחת בוקר חלבונית.",
        activity = "כדאי הליכה בבוקר.",
        sleep = "כדאי לישון מוקדם."
    )

    @Test
    fun `prefers today when the category field is present`() {
        val r = pickInsight(today, tomorrow, InsightCategory.NUTRITION)
        assertEquals("החלבון יפה.", r.text)
        assertEquals(InsightSource.TODAY, r.source)
        assertNull(r.label)
    }

    @Test
    fun `falls back to tomorrow with label when today is absent`() {
        val r = pickInsight(today = null, tomorrow = tomorrow, category = InsightCategory.ACTIVITY)
        assertEquals("כדאי הליכה בבוקר.", r.text)
        assertEquals(InsightSource.TOMORROW, r.source)
        assertEquals(INSIGHT_TOMORROW_LABEL, r.label)
    }

    @Test
    fun `falls back to tomorrow when today field is blank`() {
        val blankToday = today.copy(sleep = "   ")
        val r = pickInsight(blankToday, tomorrow, InsightCategory.SLEEP)
        assertEquals("כדאי לישון מוקדם.", r.text)
        assertEquals(InsightSource.TOMORROW, r.source)
    }

    @Test
    fun `reports not-ready when neither block has the category`() {
        val r = pickInsight(today = null, tomorrow = null, category = InsightCategory.NUTRITION)
        assertEquals(INSIGHT_NOT_READY, r.text)
        assertEquals(InsightSource.NOT_READY, r.source)
    }

    @Test
    fun `general has no tomorrow fallback so morning is not-ready`() {
        // today absent, tomorrow present -> GENERAL still not ready (tomorrow has no general)
        val r = pickInsight(today = null, tomorrow = tomorrow, category = InsightCategory.GENERAL)
        assertEquals(INSIGHT_NOT_READY, r.text)
        assertEquals(InsightSource.NOT_READY, r.source)
    }
}
