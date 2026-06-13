package com.myhealthtracker.app.data.insights

import com.myhealthtracker.app.data.insights.model.DailyInsights
import kotlinx.coroutines.flow.StateFlow

/** Exposes the current day's insights document as an observable stream. */
interface InsightsRepository {
    val insights: StateFlow<DailyInsights?>
}

/** Result of a manual refresh callable. */
data class InsightRefreshResult(val status: String)

class InsightsRefreshException(message: String) : Exception(message)

/** Triggers an on-demand refresh of today's insights via the backend callable. */
interface InsightsRefresher {
    /** Calls the backend; throws [InsightsRefreshException] on failure. */
    suspend fun refresh(): InsightRefreshResult
}
