package com.myhealthtracker.app.data.insights

import com.myhealthtracker.app.data.insights.model.InsightToday
import com.myhealthtracker.app.data.insights.model.InsightTomorrow

enum class InsightCategory { GENERAL, NUTRITION, ACTIVITY, SLEEP }

enum class InsightSource { TODAY, TOMORROW, NOT_READY }

/** Result of resolving which sentence to show for a category, plus a UI label when relevant. */
data class InsightDisplay(
    val text: String,
    val source: InsightSource,
    val label: String? = null
)

const val INSIGHT_TOMORROW_LABEL = "הדגשים שלך להיום"
const val INSIGHT_NOT_READY = "התובנות עדיין לא מוכנות"

private fun InsightToday.text(category: InsightCategory): String = when (category) {
    InsightCategory.GENERAL -> general
    InsightCategory.NUTRITION -> nutrition
    InsightCategory.ACTIVITY -> activity
    InsightCategory.SLEEP -> sleep
}

/** `tomorrow` has no `general` field, so GENERAL has no tomorrow fallback. */
private fun InsightTomorrow.text(category: InsightCategory): String? = when (category) {
    InsightCategory.GENERAL -> null
    InsightCategory.NUTRITION -> nutrition
    InsightCategory.ACTIVITY -> activity
    InsightCategory.SLEEP -> sleep
}

/**
 * Pure, presence-based selection (no wall-clock): prefer today's sentence; otherwise
 * fall back to last night's tomorrow emphasis (labelled); otherwise "not ready yet".
 * The "before/after 15:00" behavior emerges naturally because `today` is only filled
 * from the 15:00 / manual refresh runs.
 */
fun pickInsight(
    today: InsightToday?,
    tomorrow: InsightTomorrow?,
    category: InsightCategory
): InsightDisplay {
    val todayText = today?.text(category)
    if (!todayText.isNullOrBlank()) {
        return InsightDisplay(todayText, InsightSource.TODAY)
    }
    val tomorrowText = tomorrow?.text(category)
    if (!tomorrowText.isNullOrBlank()) {
        return InsightDisplay(tomorrowText, InsightSource.TOMORROW, INSIGHT_TOMORROW_LABEL)
    }
    return InsightDisplay(INSIGHT_NOT_READY, InsightSource.NOT_READY)
}
