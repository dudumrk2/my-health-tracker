package com.myhealthtracker.app.ui.profile

/**
 * Formats the sleep goal for display. After a manual sleep override GoalCalculator sets
 * min == max, so we show a single value instead of a "7-7" range.
 */
fun formatSleepGoal(min: Int, max: Int, unitString: String = "שעות"): String =
    if (min == max) "$min $unitString" else "$min-$max $unitString"
