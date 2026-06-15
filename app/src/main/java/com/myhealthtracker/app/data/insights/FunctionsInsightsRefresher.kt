package com.myhealthtracker.app.data.insights

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/** Pure mapping of the callable's raw response map to a typed result. */
fun mapRefreshResponse(raw: Map<*, *>?): InsightRefreshResult {
    val status = raw?.get("status") as? String
        ?: throw InsightsRefreshException("Unexpected response.")
    return InsightRefreshResult(status)
}

class FunctionsInsightsRefresher(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : InsightsRefresher {

    override suspend fun refresh(): InsightRefreshResult {
        try {
            val response = functions.getHttpsCallable("generateInsightsManual").call().await()
            return mapRefreshResponse(response.getData() as? Map<*, *>)
        } catch (e: FirebaseFunctionsException) {
            throw InsightsRefreshException(
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                        "שירות ה-AI עמוס כרגע. נסה שוב בעוד רגע."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        "נדרשת התחברות מחדש."
                    else -> "לא ניתן לרענן את התובנות כרגע. נסה שוב מאוחר יותר."
                }
            )
        }
    }
}
