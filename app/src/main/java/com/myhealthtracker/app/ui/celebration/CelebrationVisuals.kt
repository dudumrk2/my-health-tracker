package com.myhealthtracker.app.ui.celebration

import androidx.annotation.StringRes
import com.myhealthtracker.app.R
import com.myhealthtracker.app.data.celebration.CelebrationType

/**
 * Maps each celebration type to its candidate animation file names (resolved from
 * res/raw by name at runtime) and its encouragement message resource ID. Multiple
 * animations per type give variety — one is picked at random per firing.
 */
object CelebrationVisuals {

    data class Visuals(val animations: List<String>, @StringRes val messageRes: Int)

    fun forType(type: CelebrationType): Visuals = when (type) {
        CelebrationType.STEP_GOAL -> Visuals(
            listOf("celeb_confetti", "celeb_fireworks"),
            R.string.celebration_step_goal
        )
        CelebrationType.SECOND_WORKOUT -> Visuals(
            listOf("celeb_clap", "celeb_muscle"),
            R.string.celebration_second_workout
        )
        CelebrationType.FOURTH_WORKOUT -> Visuals(
            listOf("celeb_clap", "celeb_muscle"),
            R.string.celebration_fourth_workout
        )
        CelebrationType.GREAT_MEAL -> Visuals(
            listOf("celeb_fireworks", "celeb_medal"),
            R.string.celebration_great_meal
        )
        CelebrationType.GOOD_MEAL -> Visuals(
            listOf("celeb_clap", "celeb_confetti"),
            R.string.celebration_good_meal
        )
        CelebrationType.CALORIE_GOAL -> Visuals(
            listOf("celeb_confetti", "celeb_trophy"),
            R.string.celebration_calorie_goal
        )
    }
}
