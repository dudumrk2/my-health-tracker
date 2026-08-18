package com.myhealthtracker.app.data.celebration

/** The kind of milestone being celebrated. Drives both the animation and the copy. */
enum class CelebrationType {
    STEP_GOAL,
    SECOND_WORKOUT,
    FOURTH_WORKOUT,
    GREAT_MEAL,
    GOOD_MEAL,
    CALORIE_GOAL
}

/**
 * A single celebration to show. [dedupKey] makes state-derived celebrations fire
 * once (see CelebrationController.tryCelebrate); it is ignored for one-shot,
 * action-triggered celebrations (CelebrationController.celebrateNow).
 */
data class CelebrationEvent(
    val type: CelebrationType,
    val dedupKey: String
)
