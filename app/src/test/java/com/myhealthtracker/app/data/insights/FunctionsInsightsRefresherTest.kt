package com.myhealthtracker.app.data.insights

import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionsInsightsRefresherTest {

    @Test
    fun `maps a status response to a typed result`() {
        val result = mapRefreshResponse(mapOf("status" to "written"))
        assertEquals("written", result.status)
    }

    @Test
    fun `maps a skipped status`() {
        assertEquals("skipped", mapRefreshResponse(mapOf("status" to "skipped")).status)
    }

    @Test(expected = InsightsRefreshException::class)
    fun `missing status throws refresh exception`() {
        mapRefreshResponse(mapOf("foo" to "bar"))
    }

    @Test(expected = InsightsRefreshException::class)
    fun `null response throws refresh exception`() {
        mapRefreshResponse(null)
    }
}
