package com.myhealthtracker.app.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForegroundTrackerTest {
    @Test
    fun `tracks enter and exit`() {
        AppForegroundTracker.onEnterBackground()
        assertFalse(AppForegroundTracker.isForeground())
        AppForegroundTracker.onEnterForeground()
        assertTrue(AppForegroundTracker.isForeground())
        AppForegroundTracker.onEnterBackground()
        assertFalse(AppForegroundTracker.isForeground())
    }
}
