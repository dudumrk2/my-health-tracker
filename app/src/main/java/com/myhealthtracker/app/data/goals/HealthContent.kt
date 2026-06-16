package com.myhealthtracker.app.data.goals

/**
 * Fixed, non-medical disclaimer shown in the UI next to any health-related content
 * (computed goals, self-declared focus areas). Mirrors the server-side insights disclaimer.
 * Iron rule #5: general info only, never medical advice.
 */
const val HEALTH_DISCLAIMER_HE =
    "מידע כללי ואינו תחליף לייעוץ רפואי. מומלץ להתייעץ עם רופא/ה."

/** Primary usage goals (self-declared at registration). value → Hebrew label. */
val PRIMARY_GOAL_OPTIONS: List<Pair<String, String>> = listOf(
    "lose" to "ירידה במשקל",
    "maintain" to "שמירה על המשקל",
    "gain" to "עלייה במשקל"
)

/** TDEE activity levels (self-declared). value → Hebrew label. */
val ACTIVITY_LEVEL_OPTIONS: List<Pair<String, String>> = listOf(
    "sedentary" to "יושבני",
    "light" to "פעיל קל",
    "moderate" to "בינוני",
    "very" to "פעיל מאוד",
    "extra" to "אקסטרה"
)

/**
 * Optional self-declared focus areas. value → Hebrew label. These are NEVER inferred
 * from age/gender — only set when the user explicitly checks them.
 */
val FOCUS_AREA_OPTIONS: List<Pair<String, String>> = listOf(
    "menopause" to "גיל המעבר",
    "muscle_gain" to "בניית שריר",
    "heart_health" to "בריאות הלב"
)
