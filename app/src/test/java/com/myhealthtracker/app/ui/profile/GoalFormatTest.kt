package com.myhealthtracker.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalFormatTest {

    @Test
    fun rangeWhenMinDiffersFromMax() {
        assertEquals("7-9 שעות", formatSleepGoal(7, 9))
    }

    @Test
    fun singleValueWhenMinEqualsMax() {
        // After a sleep override, GoalCalculator sets min == max.
        assertEquals("8 שעות", formatSleepGoal(8, 8))
    }
}
