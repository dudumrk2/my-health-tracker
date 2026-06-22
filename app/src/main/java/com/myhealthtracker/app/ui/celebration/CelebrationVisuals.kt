package com.myhealthtracker.app.ui.celebration

import com.myhealthtracker.app.data.celebration.CelebrationType

/**
 * Maps each celebration type to its candidate animation file names (resolved from
 * res/raw by name at runtime) and its Hebrew encouragement message. Multiple
 * animations per type give variety — one is picked at random per firing.
 */
object CelebrationVisuals {

    data class Visuals(val animations: List<String>, val message: String)

    fun forType(type: CelebrationType): Visuals = when (type) {
        CelebrationType.STEP_GOAL -> Visuals(
            listOf("celeb_confetti", "celeb_fireworks"),
            "כל הכבוד! עמדת ביעד הצעדים היום 🎉"
        )
        CelebrationType.SECOND_WORKOUT -> Visuals(
            listOf("celeb_clap", "celeb_muscle"),
            "אימון שני השבוע — ממשיכים בכיף! 💪"
        )
        CelebrationType.FOURTH_WORKOUT -> Visuals(
            listOf("celeb_clap", "celeb_muscle"),
            "ארבעה אימונים השבוע, אלוף! 🔥"
        )
        CelebrationType.GREAT_MEAL -> Visuals(
            listOf("celeb_fireworks", "celeb_medal"),
            "ארוחה מצוינת! בחירה נקייה ובריאה 🥬"
        )
        CelebrationType.GOOD_MEAL -> Visuals(
            listOf("celeb_clap", "celeb_confetti"),
            "ארוחה טובה, כל הכבוד! 👏"
        )
        CelebrationType.CALORIE_GOAL -> Visuals(
            listOf("celeb_confetti", "celeb_trophy"),
            "עמדת ביעד הקלורי אתמול — מעולה! 🏆"
        )
    }
}
