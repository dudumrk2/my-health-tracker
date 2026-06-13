package com.myhealthtracker.app.data.insights.model

import java.time.Instant

/** Today's focused insights — one short sentence per category. */
data class InsightToday(
    val general: String,
    val nutrition: String,
    val activity: String,
    val sleep: String
)

/** Tomorrow's gentle emphases authored the previous evening (no `general`). */
data class InsightTomorrow(
    val nutrition: String,
    val activity: String,
    val sleep: String
)

/**
 * Mirror of a `users/{uid}/insights/{date}` document. Either block may be absent:
 * `tomorrow` is written the previous evening; `today` from 15:00 / manual refresh.
 */
data class DailyInsights(
    val date: String,
    val today: InsightToday?,
    val tomorrow: InsightTomorrow?,
    val disclaimer: String,
    val trigger: String,
    val generatedAt: Instant?
)
